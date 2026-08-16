-- Income no longer raises the spending ceiling on its own.
--
-- The rule until now was `remaining = monthly_limit - spent + income`, so any deposit that
-- landed silently enlarged what the customer was told they could spend. That is wrong for
-- a budgeting product and the customer said so plainly: a 20,000 transfer arriving is not
-- 20,000 more of household grocery money. `monthly_limit` is a **ceiling the customer
-- chose**, not a balance to be topped up.
--
-- From here:
--
--     remaining = monthly_limit - spent + income the customer explicitly allocated
--
-- `counts_toward_budget` carries that decision, and its three states are all meaningful:
--
--     null   never asked            → excluded, and listed in income_awaiting_decision
--     true   "أيوة، ده لمصروف البيت" → added to the ceiling
--     false  "لأ، ده مش للبيت"       → excluded, and never asked about again
--
-- null is deliberately not the same as false. The agent needs to tell "the customer said
-- no" apart from "nobody has asked yet", otherwise it either nags about settled deposits
-- or silently swallows ones it should have raised. `income_awaiting_decision` in the
-- snapshot is what it reads to ask, and it is capped so a customer with a hundred
-- deposits cannot blow up the prompt.

alter table public.zad_transactions
  add column if not exists counts_toward_budget boolean;

comment on column public.zad_transactions.counts_toward_budget is
  'Income only: true = customer confirmed it funds this cycle''s budget, false = it does not, null = not asked yet. Ignored for expense/transfer rows.';

-- Existing income predates the question, so it is left null: it stops inflating the
-- ceiling immediately, and the agent will ask about each one rather than us guessing an
-- answer on the customer''s behalf.

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
    and renewal_date is not null and renewal_date <> ''
    and substring(renewal_date from 1 for 10) ~ '^\d{4}-\d{2}-\d{2}$'
    and substring(renewal_date from 1 for 10)::date <= v_end;

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
      'next_due', substring(renewal_date from 1 for 10)::date) as x
    from public.zad_subscriptions
    where user_id = p_user and is_active
      and renewal_date is not null and renewal_date <> ''
      and substring(renewal_date from 1 for 10) ~ '^\d{4}-\d{2}-\d{2}$'
      and substring(renewal_date from 1 for 10)::date <= v_end
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
