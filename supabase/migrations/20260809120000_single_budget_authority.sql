-- Phase 0 — "one number, everywhere": zad_budget_state() becomes the single
-- authority for every money figure zad shows the customer.
--
-- Why this exists. Before this migration the same concept ("المتبقي") had FOUR
-- independent implementations that disagreed with each other, and the customer
-- could read four different numbers for the same month depending on where they
-- looked:
--
--   1. BudgetMath.remainingInCycle (Kotlin)   limit - spent + income, null if no limit,
--                                             salary cycle, device timezone,
--                                             last_working_day anchor honoured.
--   2. zad-brain buildSnapshot (Deno)         limit - spent  (income DROPPED),
--                                             salary cycle but in UTC, and
--                                             last_working_day treated as day_of_month.
--                                             With no limit set it computed 0 - spent,
--                                             i.e. a negative "remaining" and a
--                                             permanent threat=OVER for every user who
--                                             never picked a ceiling.
--   3. zad-telegram-bot context.ts (Deno)     limit - spent + income, CALENDAR month,
--                                             no obligations, no cycle at all.
--   4. zad-telegram-bot /balance button       limit - spent + income, calendar month,
--                                             AND the limit forced to 0 unless
--                                             limit_confirmed_at was set — which
--                                             AGENT_GAP_ANALYSIS.md §6 documents as
--                                             null even on accounts that do have a
--                                             real monthly_limit. Those users were
--                                             told their budget was 0.
--
-- The rule from here on: this function is the definition. Kotlin's BudgetMath stays,
-- but only as the offline mirror (the app is offline-first and must still show a
-- number with no network); ZadTransactionRepo prefers this RPC whenever it answers.
-- Any change to the meaning of spent/remaining/committed/available happens HERE first
-- and is mirrored into BudgetMath.kt second, never the other way round.
--
-- Security: deliberately NOT `security definer`. These read zad_users /
-- zad_transactions / zad_obligations / zad_subscriptions through the caller's own
-- RLS, exactly like zad_cash_balance() (migration 20260726060000). The edge functions
-- reach it with the service-role key; the app reaches it with the user's JWT and is
-- scoped by RLS. Passing someone else's p_user simply returns their-visible rows,
-- which for a family admin is the same access the existing child-budget policy grants.

-- ── Timezone per market ────────────────────────────────────────────────────────
-- zad_users.country holds the ISO code written by syncMarketProfile (MarketProfile.kt's
-- Market.countryCode — "EG", "SA", …). The server has no other way to know which day
-- "today" is for this customer, and getting that wrong shifts every cycle boundary by
-- up to a day. 'UTC' is the fallback for the accounts that still have country = null.
create or replace function public.zad_market_timezone(p_country text)
returns text language sql immutable set search_path = public as $$
  select case upper(coalesce(p_country, ''))
    when 'SA' then 'Asia/Riyadh'      when 'EG' then 'Africa/Cairo'
    when 'AE' then 'Asia/Dubai'       when 'KW' then 'Asia/Kuwait'
    when 'QA' then 'Asia/Qatar'       when 'BH' then 'Asia/Bahrain'
    when 'OM' then 'Asia/Muscat'      when 'JO' then 'Asia/Amman'
    when 'LB' then 'Asia/Beirut'      when 'IQ' then 'Asia/Baghdad'
    when 'SY' then 'Asia/Damascus'    when 'YE' then 'Asia/Aden'
    when 'PS' then 'Asia/Gaza'        when 'LY' then 'Africa/Tripoli'
    when 'SD' then 'Africa/Khartoum'  when 'MA' then 'Africa/Casablanca'
    when 'TN' then 'Africa/Tunis'     when 'DZ' then 'Africa/Algiers'
    when 'TR' then 'Europe/Istanbul'
    else 'UTC'
  end;
$$;

-- Mirror of CycleMath.weekendDays: most of the region is Fri/Sat, but Turkey, Morocco,
-- Tunisia, Algeria and Lebanon are Sat/Sun. Only last_working_day salaries care.
-- Postgres dow: 0=Sunday … 6=Saturday.
create or replace function public.zad_weekend_dows(p_country text)
returns int[] language sql immutable set search_path = public as $$
  select case when upper(coalesce(p_country, '')) in ('TR','MA','TN','DZ','LB')
              then array[6,0] else array[5,6] end;
$$;

-- Mirror of CycleMath.anchoredDay: clamp the salary day to the month's real length,
-- then walk backwards off the weekend when the salary lands on the last working day.
create or replace function public.zad_anchored_day(
  p_year int, p_month int, p_day int, p_anchor text, p_country text
) returns date language plpgsql immutable set search_path = public as $$
declare
  v_last int;
  v_date date;
  v_weekend int[];
begin
  v_last := extract(day from (make_date(p_year, p_month, 1) + interval '1 month - 1 day'))::int;
  v_date := make_date(p_year, p_month, least(p_day, v_last));
  if p_anchor = 'last_working_day' then
    v_weekend := public.zad_weekend_dows(p_country);
    while extract(dow from v_date)::int = any(v_weekend) loop
      v_date := v_date - 1;
    end loop;
  end if;
  return v_date;
end;
$$;

-- Mirror of CycleMath.cycleStart/cycleEnd. cycle_start_day = null means the salary day
-- was never detected, and the answer is a plain calendar month — the same fallback the
-- client makes, so an undetected account behaves identically on both sides.
create or replace function public.zad_cycle_bounds(
  p_asof date, p_cycle_start_day int, p_cycle_anchor text, p_country text
) returns table(cycle_start date, cycle_end date)
language plpgsql immutable set search_path = public as $$
declare
  v_this date;
  v_start date;
  v_next date;
begin
  if p_cycle_start_day is null then
    cycle_start := date_trunc('month', p_asof)::date;
    cycle_end := (date_trunc('month', p_asof) + interval '1 month')::date;
    return next;
    return;
  end if;

  v_this := public.zad_anchored_day(
    extract(year from p_asof)::int, extract(month from p_asof)::int,
    p_cycle_start_day, p_cycle_anchor, p_country);

  if p_asof < v_this then
    v_start := public.zad_anchored_day(
      extract(year from p_asof - interval '1 month')::int,
      extract(month from p_asof - interval '1 month')::int,
      p_cycle_start_day, p_cycle_anchor, p_country);
  else
    v_start := v_this;
  end if;

  v_next := (v_start + interval '1 month')::date;
  cycle_start := v_start;
  cycle_end := public.zad_anchored_day(
    extract(year from v_next)::int, extract(month from v_next)::int,
    p_cycle_start_day, p_cycle_anchor, p_country);
  return next;
end;
$$;

-- Mirror of BudgetMath.nextDueDate. 'once' that already passed returns null (assumed
-- paid); a recurring obligation with no due_day returns null (we refuse to guess a day
-- rather than invent a charge). quarterly/yearly step 3/12 months off due_day itself —
-- the same deliberate simplification the Kotlin side documents, since the table has no
-- due_month column.
create or replace function public.zad_obligation_next_due(
  p_recurrence text, p_due_day int, p_due_date date, p_asof date
) returns date language plpgsql immutable set search_path = public as $$
declare
  v_step int;
  v_next date;
  v_day int;
begin
  if p_recurrence = 'once' then
    if p_due_date is null or p_due_date < p_asof then return null; end if;
    return p_due_date;
  end if;
  if p_due_day is null then return null; end if;

  v_step := case p_recurrence when 'quarterly' then 3 when 'yearly' then 12 else 1 end;
  v_day := least(p_due_day, extract(day from (date_trunc('month', p_asof) + interval '1 month - 1 day'))::int);
  v_next := make_date(extract(year from p_asof)::int, extract(month from p_asof)::int, v_day);
  while v_next < p_asof loop
    v_next := (v_next + (v_step || ' month')::interval)::date;
    v_next := make_date(
      extract(year from v_next)::int, extract(month from v_next)::int,
      least(p_due_day, extract(day from (date_trunc('month', v_next) + interval '1 month - 1 day'))::int));
  end loop;
  return v_next;
end;
$$;

-- ── The authority ──────────────────────────────────────────────────────────────
-- p_tz: the client passes its own ZoneId so the app and the server agree on which
-- calendar day it is even for a traveller. Null (the edge functions' normal call)
-- resolves the timezone from the account's country.
create or replace function public.zad_budget_state(p_user uuid, p_tz text default null)
returns jsonb language plpgsql stable set search_path = public as $$
declare
  v_user record;
  v_tz text;
  v_asof date;
  v_start date;
  v_end date;
  v_limit numeric;
  v_spent numeric;
  v_income numeric;
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
    -- An unknown IANA name from a client must not take the whole figure down.
    v_tz := 'UTC';
    v_asof := (now() at time zone 'UTC')::date;
  end;

  select cycle_start, cycle_end into v_start, v_end
  from public.zad_cycle_bounds(v_asof, v_user.cycle_start_day,
                               coalesce(v_user.cycle_anchor, 'day_of_month'), v_user.country);

  -- The limit rule, stated once. A ceiling <= 0 (or absent) means UNKNOWN, and every
  -- derived figure that depends on it is null rather than zero: "0 ج.م متبقي" is a real
  -- number with a real meaning (you are broke) and must never be shown to someone who
  -- simply never set a budget. This is BudgetMath.remaining's contract, adopted here.
  --
  -- limit_confirmed_at is deliberately NOT part of this rule. The /balance button used
  -- to gate on it and therefore reported budget 0 to accounts holding a real
  -- monthly_limit, because captureMonthlyLimit never writes the confirmation timestamp
  -- (AGENT_GAP_ANALYSIS.md §6). Confirmation state is a prompting concern — whether to
  -- ask "ميزانيتك ٥٠٠٠ صح؟" — not an arithmetic one. It is still returned below so a
  -- caller that wants to prompt can, without changing the number.
  v_limit := case when coalesce(v_user.monthly_limit, 0) > 0 then v_user.monthly_limit else null end;

  select
    coalesce(sum(amount) filter (where txn_kind = 'expense'), 0),
    coalesce(sum(amount) filter (where txn_kind = 'income'), 0),
    count(*) filter (where txn_kind in ('expense','income') and coalesce(is_verified, false) = false)
  into v_spent, v_income, v_unverified
  from public.zad_transactions
  where user_id = p_user
    and (created_at at time zone v_tz)::date >= v_start
    and (created_at at time zone v_tz)::date <  v_end;

  v_remaining := case when v_limit is null then null else v_limit - v_spent + v_income end;

  -- committed = confirmed+active obligations and active subscriptions that fall due
  -- before the next salary lands. Both bounds inclusive of cycle_end, matching
  -- BudgetMath.committedInCycle.
  select coalesce(sum(amount), 0) into v_obligations
  from public.zad_obligations
  where user_id = p_user and active and confirmed
    and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) is not null
    and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) <= v_end;

  -- zad_subscriptions.renewal_date is text, not date — an unparseable value is skipped
  -- rather than allowed to abort the whole figure.
  select coalesce(sum(amount), 0) into v_subscriptions
  from public.zad_subscriptions
  where user_id = p_user and is_active
    and renewal_date is not null and renewal_date <> ''
    and substring(renewal_date from 1 for 10) ~ '^\d{4}-\d{2}-\d{2}$'
    and substring(renewal_date from 1 for 10)::date <= v_end;

  v_committed := v_obligations + v_subscriptions;
  v_available := case when v_remaining is null then null else v_remaining - v_committed end;

  -- The itemised list behind `committed`, so a caller can name the charges without
  -- re-deriving which ones fall inside the cycle — the brain used to keep its own copy of
  -- that filter and could therefore print a list whose sum was not the committed total.
  --
  -- BOTH halves of `committed` are listed here, obligations AND subscriptions, filtered by
  -- exactly the predicates that produced v_obligations and v_subscriptions above. Listing
  -- only the obligations reproduces the very defect this list exists to prevent: the first
  -- live account checked after this migration had committed = 500 coming entirely from one
  -- active subscription (ايجار, due 2026-08-24), and returned an empty items array next to
  -- it — so the brain was handed "500 محجوز" with nothing to name, and next_obligation_due
  -- was null on a cycle that very much had a next charge coming.
  select coalesce(jsonb_agg(x order by x->>'next_due'), '[]'::jsonb) into v_items from (
    select jsonb_build_object(
      'title', title, 'amount', round(amount::numeric, 2), 'kind', kind,
      'next_due', public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof)) as x
    from public.zad_obligations
    where user_id = p_user and active and confirmed
      and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) is not null
      and public.zad_obligation_next_due(recurrence, due_day, due_date, v_asof) <= v_end
    union all
    -- kind='subscription' rather than the row's own `category`: the consumer is deciding
    -- how to phrase a committed charge, and "اشتراك" is the fact that matters there, not
    -- whether the user filed it under ترفيه.
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

  -- get_spend_by_category, same window and the same txn_kind filter as `spent`. The brain's
  -- own byCategory used is_expense instead, so an ATM withdrawal (is_expense = true,
  -- txn_kind = 'transfer') landed in the category donut while being excluded from `spent`
  -- — the donut could not add up to the total sitting next to it.
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
  v_elapsed := greatest((v_asof - v_start) + 1, 1);   -- inclusive: day one is already spent
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

  -- UNKNOWN, not SAFE and not OVER. The brain used to compute 0 - spent for a user with
  -- no ceiling and then warn them they were over budget on a budget they never set.
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
    -- Phase 0 item 4: every surface stamps the figure it rendered with this. Two
    -- surfaces showing different numbers is then diagnosable (different computed_at =
    -- stale cache) instead of ambiguous (different formula, the bug this migration ends).
    'computed_at', now()
  );
end;
$$;

comment on function public.zad_budget_state(uuid, text) is
  'Single authority for every money figure zad shows. Kotlin BudgetMath is its offline mirror; zad-brain and zad-telegram-bot must not recompute these. See migration 20260809120000.';

grant execute on function public.zad_market_timezone(text) to authenticated, anon, service_role;
grant execute on function public.zad_weekend_dows(text) to authenticated, anon, service_role;
grant execute on function public.zad_anchored_day(int, int, int, text, text) to authenticated, anon, service_role;
grant execute on function public.zad_cycle_bounds(date, int, text, text) to authenticated, anon, service_role;
grant execute on function public.zad_obligation_next_due(text, int, date, date) to authenticated, anon, service_role;
grant execute on function public.zad_budget_state(uuid, text) to authenticated, service_role;
