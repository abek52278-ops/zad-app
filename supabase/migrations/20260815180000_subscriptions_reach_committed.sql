-- Subscriptions never reached "المحجوز" at all.
--
-- `zad_budget_state_legacy` counted a subscription only when its `renewal_date` matched
-- `^\d{4}-\d{2}-\d{2}$`. But `renewal_date` is a free-text column and nothing on either
-- write path validates it: the agent tool passes `input.renewal_date ?? null` straight
-- through, and the subscriptions screen saves a plain text field verbatim. The actual
-- rows in this project on 2026-08-15 were '30 مارس', '20' and '30' — not one of them a
-- date. So every subscription was silently dropped from the reservation, and the
-- committed figure the customer sees has never included a single one. The number was not
-- wrong; the feature was dead, which is harder to notice.
--
-- Two further problems sat behind that regex, both of which would have surfaced the
-- moment anyone typed a real date:
--
--   * No lower bound. Obligations are rolled forward by zad_obligation_next_due(), so a
--     past due day always resolves to the next occurrence. Subscriptions were compared
--     raw, so a renewal_date left in the past stayed reserved forever.
--   * The only thing that ever advanced renewal_date was SubscriptionAutoDeductWorker, a
--     24-hour WorkManager job on the phone. There is no server-side equivalent, so the
--     Telegram bot's view of the budget depended on an Android device having run recently.
--     And in the window between a charge landing (recorded as an expense, inside `spent`)
--     and that worker firing, the same money was counted twice — once spent, once
--     committed — making "متاح" too small by exactly the subscription amount.
--
-- zad_subscription_next_renewal() answers all three the way obligations already do:
-- resolve an anchor day, then step forward by the billing cycle until it is not in the
-- past. The anchor is read from the clearest available source and never guessed — a
-- subscription whose day cannot be determined returns null and stays out of the
-- reservation, because inventing a day reserves money in the wrong cycle.

create or replace function public.zad_subscription_next_renewal(
  p_renewal_date text, p_due_day int, p_billing_cycle text, p_asof date
) returns date language plpgsql immutable set search_path = public as $$
declare
  v_anchor date;
  v_day int;
  v_next date;
  v_guard int := 0;
begin
  if p_asof is null then return null; end if;

  -- 1. A real ISO date is the customer's most explicit statement of intent.
  if p_renewal_date is not null
     and substring(p_renewal_date from 1 for 10) ~ '^\d{4}-\d{2}-\d{2}$' then
    begin
      v_anchor := substring(p_renewal_date from 1 for 10)::date;
    exception when others then
      v_anchor := null;   -- '2026-02-31' parses by regex but not by date
    end;
  end if;

  -- 2. due_day is a real column that nothing was reading. '30 مارس' and '20' both mean a
  --    day of the month, and that is recoverable where a full date is not.
  if v_anchor is null then
    v_day := coalesce(p_due_day, (substring(coalesce(p_renewal_date, '') from '\d{1,2}'))::int);
    if v_day is null or v_day < 1 or v_day > 31 then return null; end if;
    v_anchor := make_date(
      extract(year from p_asof)::int, extract(month from p_asof)::int,
      least(v_day, extract(day from (date_trunc('month', p_asof) + interval '1 month - 1 day'))::int));
  else
    v_day := extract(day from v_anchor)::int;
  end if;

  -- 3. Step forward to the first occurrence that has not already happened. The guard is
  --    not defensive dressing: a weekly cycle anchored years back would otherwise loop
  --    thousands of times inside a function called on every budget read.
  v_next := v_anchor;
  while v_next < p_asof and v_guard < 600 loop
    v_guard := v_guard + 1;
    v_next := case upper(coalesce(p_billing_cycle, 'MONTHLY'))
      when 'YEARLY' then (v_next + interval '1 year')::date
      when 'ANNUAL' then (v_next + interval '1 year')::date
      when 'WEEKLY' then (v_next + interval '1 week')::date
      else make_date(
        extract(year from (v_next + interval '1 month'))::int,
        extract(month from (v_next + interval '1 month'))::int,
        least(v_day, extract(day from (date_trunc('month', v_next + interval '1 month')
                                       + interval '1 month - 1 day'))::int))
    end;
  end loop;
  if v_next < p_asof then return null; end if;
  return v_next;
end;
$$;

grant execute on function public.zad_subscription_next_renewal(text, int, text, date)
  to authenticated, anon, service_role;

-- The rest of zad_budget_state_legacy is unchanged from
-- 20260815140000_income_requires_allocation.sql; only the two subscription clauses move
-- onto the resolver. zad_budget_state (the wrapper from 20260810000000) still applies the
-- `next_due < cycle_end` half-open boundary on top, so a renewal landing exactly on the
-- next cycle's first day is still reserved there and not here.
create or replace function public.zad_budget_state_legacy(p_user uuid, p_tz text default null)
returns jsonb language plpgsql stable set search_path = public as $function$
declare
  v_user record;
  v_tz text;
  v_asof date;
  v_start date;
  v_end date;
  v_limit numeric;
  v_spent numeric;
  v_income numeric;
  v_income_allocated numeric;
  v_income_pending numeric;
  v_income_items jsonb;
  v_remaining numeric;
  v_committed numeric;
  v_obligations numeric;
  v_subscriptions numeric;
  v_next_due jsonb;
  v_items jsonb;
  v_by_category jsonb;
  v_available numeric;
  v_cash numeric;
  v_len int;
  v_elapsed int;
  v_left int;
  v_velocity numeric;
  v_daily numeric;
  v_threat text;
  v_unverified int;
begin
  select monthly_limit, limit_confirmed_at, cycle_start_day, cycle_anchor, currency, country
    into v_user from public.zad_users where id = p_user;

  v_tz := coalesce(nullif(p_tz, ''), public.zad_market_timezone(v_user.country));
  begin
    v_asof := (now() at time zone v_tz)::date;
  exception when others then
    v_tz := 'UTC';
    v_asof := (now() at time zone 'UTC')::date;
  end;

  select cycle_start, cycle_end into v_start, v_end
  from public.zad_cycle_bounds(v_asof, v_user.cycle_start_day,
                               coalesce(v_user.cycle_anchor, 'day_of_month'), v_user.country);

  v_limit := case when coalesce(v_user.monthly_limit, 0) > 0 then v_user.monthly_limit else null end;

  -- `income` stays the honest total of money that came in — the customer is still shown
  -- what they earned. Only `income_allocated` is allowed to move the ceiling.
  select
    coalesce(sum(amount) filter (where txn_kind = 'expense'), 0),
    coalesce(sum(amount) filter (where txn_kind = 'income'), 0),
    coalesce(sum(amount) filter (where txn_kind = 'income' and counts_toward_budget is true), 0),
    coalesce(sum(amount) filter (where txn_kind = 'income' and counts_toward_budget is null), 0),
    count(*) filter (where txn_kind in ('expense','income') and coalesce(is_verified, false) = false)
  into v_spent, v_income, v_income_allocated, v_income_pending, v_unverified
  from public.zad_transactions
  where user_id = p_user
    and (created_at at time zone v_tz)::date >= v_start
    and (created_at at time zone v_tz)::date <  v_end;

  select coalesce(jsonb_agg(x order by x ->> 'created_at' desc), '[]'::jsonb) into v_income_items
  from (
    select jsonb_build_object(
      'id', id, 'title', title, 'amount', round(amount::numeric, 2),
      'created_at', created_at) as x
    from public.zad_transactions
    where user_id = p_user and txn_kind = 'income' and counts_toward_budget is null
      and (created_at at time zone v_tz)::date >= v_start
      and (created_at at time zone v_tz)::date <  v_end
    order by created_at desc
    limit 10
  ) i;

  v_remaining := case when v_limit is null then null else v_limit - v_spent + v_income_allocated end;

  select coalesce(sum(amount), 0) into v_obligations
  from public.zad_obligations
  where user_id = p_user and active and confirmed
    and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) is not null
    and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) <= v_end;

  select coalesce(sum(amount), 0) into v_subscriptions
  from public.zad_subscriptions
  where user_id = p_user and is_active
    and public.zad_subscription_next_renewal(renewal_date, due_day, billing_cycle, v_asof) is not null
    and public.zad_subscription_next_renewal(renewal_date, due_day, billing_cycle, v_asof) <= v_end;

  v_committed := v_obligations + v_subscriptions;
  v_available := case when v_remaining is null then null else v_remaining - v_committed end;

  select coalesce(jsonb_agg(x order by x->>'next_due'), '[]'::jsonb) into v_items from (
    select jsonb_build_object(
      'title', title, 'amount', round(amount::numeric, 2), 'kind', kind,
      'next_due', public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof)) as x
    from public.zad_obligations
    where user_id = p_user and active and confirmed
      and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) is not null
      and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) <= v_end
    union all
    select jsonb_build_object(
      'title', title, 'amount', round(amount::numeric, 2), 'kind', 'subscription',
      'next_due', public.zad_subscription_next_renewal(renewal_date, due_day, billing_cycle, v_asof)) as x
    from public.zad_subscriptions
    where user_id = p_user and is_active
      and public.zad_subscription_next_renewal(renewal_date, due_day, billing_cycle, v_asof) is not null
      and public.zad_subscription_next_renewal(renewal_date, due_day, billing_cycle, v_asof) <= v_end
  ) s;

  v_next_due := v_items -> 0;

  select coalesce(jsonb_object_agg(cat, amt), '{}'::jsonb) into v_by_category from (
    select coalesce(nullif(category, ''), 'أخرى') as cat, round(sum(amount)::numeric, 2) as amt
    from public.zad_transactions
    where user_id = p_user and txn_kind = 'expense'
      and (created_at at time zone v_tz)::date >= v_start
      and (created_at at time zone v_tz)::date <  v_end
    group by 1
  ) c;

  v_cash := public.zad_cash_balance(p_user);

  v_len := greatest((v_end - v_start), 1);
  v_elapsed := greatest((v_asof - v_start) + 1, 1);
  v_left := greatest((v_end - v_asof), 0);

  v_velocity := case
    when v_limit is null then null
    when v_limit * v_elapsed / v_len <= 0 then 0
    else v_spent / (v_limit * v_elapsed / v_len)
  end;

  v_daily := case
    when v_remaining is null then null
    when v_left > 0 then v_remaining / v_left
    else v_remaining
  end;

  v_threat := case
    when v_remaining is null then 'UNKNOWN'
    when v_remaining < 0 then 'OVER'
    when v_velocity > 1.3 then 'DANGER'
    when v_velocity > 1.05 then 'WATCH'
    else 'SAFE'
  end;

  return jsonb_build_object(
    'user_id', p_user,
    'timezone', v_tz,
    'as_of', v_asof,
    'currency', v_user.currency,
    'country', v_user.country,
    'cycle_start', v_start,
    'cycle_end', v_end,
    'cycle_length_days', v_len,
    'days_elapsed', v_elapsed,
    'days_left', v_left,
    'monthly_limit', round(v_limit, 2),
    'limit_confirmed', v_user.limit_confirmed_at is not null,
    'spent', round(v_spent, 2),
    'income', round(v_income, 2),
    'income_allocated', round(v_income_allocated, 2),
    'income_pending', round(v_income_pending, 2),
    'income_awaiting_decision', v_income_items,
    'remaining', round(v_remaining, 2),
    'committed', round(v_committed, 2),
    'committed_obligations', round(v_obligations, 2),
    'committed_subscriptions', round(v_subscriptions, 2),
    'committed_items', v_items,
    'next_obligation_due', v_next_due,
    'by_category', v_by_category,
    'available', round(v_available, 2),
    'cash_on_hand', round(v_cash, 2),
    'daily_allowance_left', round(v_daily, 2),
    'velocity', round(v_velocity, 4),
    'threat', v_threat,
    'unverified_count', v_unverified,
    'computed_at', now()
  );
end;
$function$;

-- Recover the day that is already sitting inside the free-text column, so existing rows
-- start counting without the customer re-entering anything. Only rows where due_day is
-- still unset and the text carries a plausible day are touched; nothing is overwritten
-- and no renewal_date is edited, so a later real date still wins over this.
update public.zad_subscriptions
set due_day = (substring(renewal_date from '\d{1,2}'))::int
where due_day is null
  and renewal_date is not null
  and substring(renewal_date from 1 for 10) !~ '^\d{4}-\d{2}-\d{2}$'
  and (substring(renewal_date from '\d{1,2}'))::int between 1 and 31;
