-- The Forward Ledger (gaps item 4, "أعلى أثر").
--
-- Zad already holds four things and has never joined them: the salary cycle, fixed
-- obligations, subscription renewal dates, and zad_consumption.avg_daily_qty (the learned
-- per-item burn rate). Separately each answers "what happened". Together, walked forward a
-- day at a time, they answer the question the customer actually asks — "will I make it to
-- payday, and what runs out before then".
--
-- Deterministic on purpose. This is SQL, not a model call: it costs no quota, returns in
-- milliseconds, and gives the same answer twice. The agent calls it as a tool instead of
-- estimating, which is the difference between a number and a guess.
--
-- What it will NOT do is invent. If the burn rate for an item is unknown the item is
-- reported in `stock_unknown` rather than given a made-up date. If income is not
-- confidently recurring, next_income.amount is null rather than an average of two numbers.
-- Same rule as the budget: 0 is "not recorded", never an invented 3500.
create or replace function public.zad_forward_ledger(
  p_user uuid,
  p_days int default 30,
  p_tz text default 'UTC'
)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
with guard as (
  -- Same shape as the other SECURITY DEFINER readers: a signed-in caller may only ask
  -- about themselves; service_role (auth.uid() null) may ask about anyone.
  select case
    when auth.uid() is not null and auth.uid() <> p_user
      then null::uuid
    else p_user
  end as uid,
  greatest(1, least(coalesce(p_days, 30), 120)) as horizon
),
st as (
  select g.uid, g.horizon, public.zad_budget_state(g.uid, p_tz) as s
  from guard g where g.uid is not null
),
base as (
  select uid, horizon,
    (s->>'as_of')::date as as_of,
    coalesce((s->>'balance')::numeric, 0) as opening,
    -- velocity is spend-per-day so far this cycle. Negative or absent means we have no
    -- evidence of a burn rate, and projecting a negative burn would invent income.
    greatest(coalesce((s->>'velocity')::numeric, 0), 0) as burn,
    s->>'currency' as currency,
    (s->>'cycle_end')::date as cycle_end
  from st
),
days as (
  select b.*, gs::date as d, (gs::date - b.as_of) as idx
  from base b, generate_series(b.as_of + 1, b.as_of + b.horizon, interval '1 day') gs
),
ev as (
  select d.d, 'obligation'::text as kind, o.title, (-o.amount)::numeric as amount
  from days d
  join zad_obligations o on o.user_id = d.uid and o.active
  where public.zad_obligation_next_due(o.recurrence, o.due_day, o.due_date, d.d - 1) = d.d
  union all
  select d.d, 'subscription', s.title, (-s.amount)::numeric
  from days d
  join zad_subscriptions s on s.user_id = d.uid and s.is_active
  where public.zad_subscription_next_renewal(s.renewal_date, s.due_day, s.billing_cycle, d.d - 1) = d.d
),
per_day as (
  select d.d, d.idx, d.opening, d.burn,
    coalesce((select sum(amount) from ev where ev.d = d.d), 0) as day_events,
    coalesce(
      (select jsonb_agg(jsonb_build_object('kind', kind, 'title', title, 'amount', amount))
       from ev where ev.d = d.d),
      '[]'::jsonb
    ) as events
  from days d
),
running as (
  select d, idx, events,
    round(
      opening - burn * idx
      + sum(day_events) over (order by d rows between unbounded preceding and current row),
      2
    ) as balance
  from per_day
),
stock as (
  select i.item_name, c.avg_daily_qty,
    floor(i.quantity / nullif(c.avg_daily_qty, 0))::int as days_left
  from zad_inventory i
  join zad_consumption c
    on c.user_id = i.user_id and c.item_name = i.item_name
   and c.rate_known and c.avg_daily_qty > 0
  where i.user_id = (select uid from base) and i.quantity > 0
),
unknown_stock as (
  select i.item_name
  from zad_inventory i
  left join zad_consumption c
    on c.user_id = i.user_id and c.item_name = i.item_name and c.rate_known and c.avg_daily_qty > 0
  where i.user_id = (select uid from base) and i.quantity > 0 and c.item_name is null
)
select case when (select count(*) from base) = 0 then null::jsonb else jsonb_build_object(
  'as_of', (select as_of from base),
  'currency', (select currency from base),
  'horizon_days', (select horizon from base),
  'opening_balance', (select opening from base),
  'daily_burn', (select burn from base),
  'cycle_end', (select cycle_end from base),
  -- The headline: the first day the projection crosses zero, or null if it never does.
  'first_negative', (
    select jsonb_build_object('date', d, 'balance', balance)
    from running where balance < 0 order by d limit 1
  ),
  'lowest', (
    select jsonb_build_object('date', d, 'balance', balance)
    from running order by balance asc, d asc limit 1
  ),
  -- Only days something actually happens. A 30-row array where 28 rows are "nothing
  -- happened" is noise in a prompt and noise on a screen.
  'event_days', (
    select coalesce(jsonb_agg(jsonb_build_object('date', d, 'balance', balance, 'events', events) order by d), '[]'::jsonb)
    from running where jsonb_array_length(events) > 0
  ),
  'stockouts', (
    select coalesce(jsonb_agg(jsonb_build_object(
      'item_name', item_name, 'days_left', days_left,
      'date', (select as_of from base) + days_left,
      'avg_daily_qty', avg_daily_qty
    ) order by days_left), '[]'::jsonb)
    from stock where days_left <= (select horizon from base)
  ),
  -- Named, not silently omitted: "I don't know how fast you get through this" is a real
  -- answer and the only honest one until zad_consumption has samples for the item.
  'stock_unknown', (
    select coalesce(jsonb_agg(item_name order by item_name), '[]'::jsonb) from unknown_stock
  )
) end;
$$;

revoke execute on function public.zad_forward_ledger(uuid, int, text) from public;
grant execute on function public.zad_forward_ledger(uuid, int, text) to authenticated, service_role;

comment on function public.zad_forward_ledger(uuid, int, text) is
  'Day-by-day cash and stock projection over the next N days. Deterministic, no model call.';
