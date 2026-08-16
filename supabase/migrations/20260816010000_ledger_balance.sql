-- The ceiling becomes an opening balance: zad turns into a ledger.
--
-- Until now `monthly_limit` was a cap the customer chose and the headline figure was
-- `limit - spent + income_the_customer_explicitly_allocated`. Two things about that were
-- wrong in practice, and the customer named both:
--
--   1. It produces numbers nobody can predict from their own arithmetic. A cap of 3,000
--      with 3,500 spent reads "-500" even though real money did arrive that month, and no
--      amount of explaining makes "-500" mean anything to someone whose salary landed.
--   2. `counts_toward_budget` defaults to null, and null is excluded. So a 10,000 salary
--      sat in the account, visible in `income`, and moved the headline figure by zero
--      until somebody answered a question about it. The feature was designed that way
--      (20260815133417) to stop deposits inflating a household grocery cap. Once the cap
--      stops being a cap, the reason evaporates.
--
-- The new rule is the whole of it:
--
--     balance = opening_balance + income - spent
--
-- `monthly_limit` is reused as `opening_balance` rather than replaced. The column is read
-- by ~40 call sites across Kotlin, zad-brain and the bot, and renaming it would be a
-- mechanical change to all of them for no behavioural gain — the meaning moves, the
-- plumbing does not. `opening_balance` is emitted alongside it so every reader can use the
-- honest name, and `monthly_limit` stays in the payload so nothing breaks mid-deploy.
--
-- Why a wrapper and not a rewrite of zad_budget_state_legacy. That function is 180 lines
-- and decides what a household is told it can spend; 20260815200000 already declined to
-- re-emit it by hand for exactly this reason. The outer wrapper from 20260810000000
-- already recomputes committed/available/daily_allowance on top of it, so the ledger
-- belongs in the same place. The legacy body is not touched.

-- ── Auto-approve the income that was waiting on a question ────────────────────
-- Every existing income row predates a question nobody will now be asked. Under the
-- ledger they all count, so the backfill is not a guess about intent — it is the new
-- rule applied to old rows. `false` is left alone: a customer who explicitly said "this
-- is not household money" said something, and that still means something to the agent
-- even though it no longer moves the balance.
update public.zad_transactions
set counts_toward_budget = true
where txn_kind = 'income' and counts_toward_budget is null;

-- New income counts on arrival instead of landing in a queue. The column keeps its
-- meaning for expense/transfer rows (ignored) and for a deliberate `false`.
alter table public.zad_transactions
  alter column counts_toward_budget set default true;

comment on column public.zad_transactions.counts_toward_budget is
  'Income only: true = funds the ledger balance (the default since the ledger migration), false = the customer excluded it, null = legacy rows only. Ignored for expense/transfer.';

comment on column public.zad_users.monthly_limit is
  'The cycle opening balance. Named monthly_limit for history: it was a spending ceiling until the ledger migration (20260816010000), and is now the amount the balance starts from. Exposed as opening_balance by zad_budget_state().';

-- ── The authority ─────────────────────────────────────────────────────────────
create or replace function public.zad_budget_state(p_user uuid, p_tz text default null)
returns jsonb language plpgsql stable set search_path = public as $$
declare
  v_state jsonb;
  v_end date;
  v_items jsonb;
  v_committed numeric;
  v_obligations numeric;
  v_subscriptions numeric;
  v_available numeric;
  v_daily numeric;
  v_next jsonb;
  v_days_left int;
  v_opening numeric;
  v_has_opening boolean;
  v_income numeric;
  v_spent numeric;
  v_balance numeric;
  v_remaining numeric;
  v_velocity numeric;
  v_threat text;
begin
  v_state := public.zad_budget_state_legacy(p_user, p_tz);
  v_end := (v_state ->> 'cycle_end')::date;
  v_days_left := coalesce((v_state ->> 'days_left')::int, 0);

  -- A cycle is [start, end). A charge on payday belongs to the next cycle; keeping it
  -- reserved in the closing cycle made "المتاح" too small precisely at the boundary.
  select coalesce(jsonb_agg(item order by item ->> 'next_due'), '[]'::jsonb)
    into v_items
  from jsonb_array_elements(coalesce(v_state -> 'committed_items', '[]'::jsonb)) item
  where (item ->> 'next_due')::date < v_end;

  select coalesce(sum((item ->> 'amount')::numeric), 0),
         coalesce(sum((item ->> 'amount')::numeric) filter (where item ->> 'kind' <> 'subscription'), 0),
         coalesce(sum((item ->> 'amount')::numeric) filter (where item ->> 'kind' = 'subscription'), 0)
    into v_committed, v_obligations, v_subscriptions
  from jsonb_array_elements(v_items) item;

  -- ── Ledger ──────────────────────────────────────────────────────────────────
  -- The legacy body computes `remaining` from income_allocated. Recomputing from `income`
  -- here rather than relying on the backfill above is deliberate: the ledger has to be a
  -- rule this function states, not a property that happens to hold because a one-off
  -- UPDATE ran once and nothing has written a null since.
  v_has_opening := (v_state ->> 'monthly_limit') is not null;
  v_opening := coalesce((v_state ->> 'monthly_limit')::numeric, 0);
  v_income  := coalesce((v_state ->> 'income')::numeric, 0);
  v_spent   := coalesce((v_state ->> 'spent')::numeric, 0);
  v_balance := v_opening + v_income - v_spent;

  -- `remaining` keeps its null-means-unset contract. It is the field every existing
  -- reader already renders, and null is the only signal the app has that the opening
  -- balance never reached the server — ZadViewModel.resyncMonthlyLimitToServer() is
  -- driven by it. `balance` is always a number, for callers that want the ledger
  -- unconditionally.
  v_remaining := case when v_has_opening then v_balance else null end;
  v_available := case when v_remaining is null then null else v_remaining - v_committed end;
  v_daily := case
    when v_available is null then null
    when v_days_left > 0 then v_available / v_days_left
    else v_available
  end;
  v_next := v_items -> 0;

  -- Pace is still worth stating, but it can no longer be measured against a ceiling that
  -- no longer exists. Spending is compared to the straight-line burn of what the cycle
  -- actually opened with — opening plus income — which is the same question ("am I going
  -- faster than this money lasts?") asked of a number that is now real.
  v_velocity := case
    when (v_opening + v_income) <= 0 then null
    else v_spent / ((v_opening + v_income)
                    * greatest(coalesce((v_state ->> 'days_elapsed')::int, 1), 1)
                    / greatest(coalesce((v_state ->> 'cycle_length_days')::int, 1), 1))
  end;

  v_threat := case
    when not v_has_opening then 'UNKNOWN'
    when v_balance < 0 then 'OVER'
    when v_velocity is null then 'UNKNOWN'
    when v_velocity > 1.3 then 'DANGER'
    when v_velocity > 1.05 then 'WATCH'
    else 'SAFE'
  end;

  return v_state || jsonb_build_object(
    'committed', round(v_committed, 2),
    'committed_obligations', round(v_obligations, 2),
    'committed_subscriptions', round(v_subscriptions, 2),
    'committed_items', v_items,
    'next_obligation_due', v_next,
    'opening_balance', round(v_opening, 2),
    'balance', round(v_balance, 2),
    'remaining', round(v_remaining, 2),
    'available', round(v_available, 2),
    'daily_allowance_left', round(v_daily, 2),
    'velocity', round(v_velocity, 4),
    'threat', v_threat
  );
end;
$$;

comment on function public.zad_budget_state(uuid, text) is
  'Single authority for every money figure zad shows. Ledger: balance = opening_balance + income - spent. Cycle is [start,end); daily allowance uses available after committed charges.';

grant execute on function public.zad_budget_state(uuid, text) to authenticated, service_role;
