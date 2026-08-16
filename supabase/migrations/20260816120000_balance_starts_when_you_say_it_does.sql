-- The balance starts counting the moment the customer states it — not at cycle start.
--
-- The ledger migration (20260816010000) made the headline figure honest arithmetic
-- (`opening + income - spent`) but left the window untouched: `zad_budget_state_legacy`
-- sums every transaction from `cycle_start`. So a customer who installs zad on the 16th,
-- opens their wallet, counts 1,000 and types it in gets 1,000 minus fifteen days of
-- spending that happened *before* they counted — money already missing from the 1,000
-- they were holding. Every one of those rows is subtracted twice: once in real life,
-- once again by the app.
--
-- The customer's own words for this: "مليش دعوة بمصاريفه القديمة ... أنا معايا X من
-- الفلوس دخلت حطيتها في الكارت الأخضر، ده الرقم اللي معايا".
--
-- So the opening balance gets a timestamp, and the ledger runs from it:
--
--     balance = opening_balance + income(after anchor) - spent(after anchor)
--
-- `balance_anchored_at` is a new column rather than a reuse of `limit_confirmed_at`.
-- That column is documented in 20260809120000 as deliberately NOT part of the arithmetic
-- ("confirmation state is a prompting concern, not an arithmetic one") and one of the two
-- write paths (captureMonthlyLimit) leaves it null on purpose. Overloading it would make
-- an agent-captured limit silently un-anchored — the exact class of bug that comment
-- exists to prevent.
--
-- Null anchor keeps the old cycle-window behaviour verbatim. Accounts that predate this
-- migration and never re-state their balance are not retroactively re-based; the backfill
-- below only adopts a timestamp that already meant "the customer confirmed this figure".

alter table public.zad_users
  add column if not exists balance_anchored_at timestamptz;

update public.zad_users
set balance_anchored_at = limit_confirmed_at
where balance_anchored_at is null
  and limit_confirmed_at is not null
  and coalesce(monthly_limit, 0) > 0;

comment on column public.zad_users.balance_anchored_at is
  'The instant the customer stated their opening balance. The ledger sums income/spent from here, not from cycle_start — money spent before the customer counted what they were holding is already reflected in the figure they typed. Null = never stated; the ledger falls back to the cycle window.';

-- The window is open-ended on the right on purpose. A ledger balance is "what you have
-- now", and it does not reset when a salary cycle rolls over — the salary landing is an
-- income row that raises it, which is the same event the cycle boundary marks anyway.
-- days_left / daily_allowance_left stay cycle-scoped: those answer "how long must this
-- last", which is still a question about the cycle.
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
  v_anchor timestamptz;
  v_tz text;
  v_unverified int;
  v_days_since numeric;
begin
  v_state := public.zad_budget_state_legacy(p_user, p_tz);
  v_end := (v_state ->> 'cycle_end')::date;
  v_days_left := coalesce((v_state ->> 'days_left')::int, 0);
  v_tz := coalesce(v_state ->> 'timezone', 'UTC');

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
  -- here rather than relying on the backfill in 20260816010000 is deliberate: the ledger
  -- has to be a rule this function states, not a property that happens to hold because a
  -- one-off UPDATE ran once and nothing has written a null since.
  v_has_opening := (v_state ->> 'monthly_limit') is not null;
  v_opening := coalesce((v_state ->> 'monthly_limit')::numeric, 0);

  select balance_anchored_at into v_anchor from public.zad_users where id = p_user;

  if v_anchor is null then
    -- Unanchored account: the cycle window, exactly as before this migration.
    v_income     := coalesce((v_state ->> 'income')::numeric, 0);
    v_spent      := coalesce((v_state ->> 'spent')::numeric, 0);
    v_unverified := coalesce((v_state ->> 'unverified_count')::int, 0);
  else
    select
      coalesce(sum(amount) filter (where txn_kind = 'income'), 0),
      coalesce(sum(amount) filter (where txn_kind = 'expense'), 0),
      count(*) filter (where txn_kind in ('expense','income') and coalesce(is_verified, false) = false)
    into v_income, v_spent, v_unverified
    from public.zad_transactions
    where user_id = p_user
      and created_at >= v_anchor;
  end if;

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
  -- no longer exists. Spending is compared to the straight-line burn of what the balance
  -- opened with — opening plus income — over the days it has actually been running. An
  -- anchored account measures elapsed days from the anchor, not from cycle start, or a
  -- balance stated an hour ago would be judged against two weeks of expected burn and
  -- report a velocity near zero on its first day.
  v_days_since := case
    when v_anchor is null then greatest(coalesce((v_state ->> 'days_elapsed')::int, 1), 1)
    else greatest(extract(epoch from (now() - v_anchor)) / 86400.0, 1)
  end;
  v_velocity := case
    when (v_opening + v_income) <= 0 then null
    else v_spent / ((v_opening + v_income)
                    * v_days_since
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

  -- `by_category` and `cash_on_hand` are left on the legacy cycle window untouched, and
  -- that is not an oversight. They answer a different question ("where did this cycle's
  -- money go", "how much cash is in my pocket") from the one the anchor re-frames ("what
  -- am I holding right now"). Re-basing the category donut on an anchor set this morning
  -- would empty a screen nobody asked to change. `spent`/`income`/`unverified_count` ARE
  -- overridden, because those three feed the balance card and its ≈ confidence marker —
  -- a pre-anchor unverified row would otherwise leave the figure marked uncertain forever.
  return v_state || jsonb_build_object(
    'committed', round(v_committed, 2),
    'committed_obligations', round(v_obligations, 2),
    'committed_subscriptions', round(v_subscriptions, 2),
    'committed_items', v_items,
    'next_obligation_due', v_next,
    'opening_balance', round(v_opening, 2),
    'balance_anchored_at', v_anchor,
    'spent', round(v_spent, 2),
    'income', round(v_income, 2),
    'unverified_count', v_unverified,
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
  'Single authority for every money figure zad shows. Ledger: balance = opening_balance + income - spent, summed from balance_anchored_at (the instant the customer stated the balance) when set, else over the cycle. Cycle is [start,end); daily allowance uses available after committed charges.';

grant execute on function public.zad_budget_state(uuid, text) to authenticated, service_role;
