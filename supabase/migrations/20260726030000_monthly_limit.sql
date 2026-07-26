-- Task 19.0 step 2 — split the two meanings currently sharing zad_users.budget.
--
-- `budget` has been doing two incompatible jobs: the user's chosen monthly ceiling,
-- and a live running balance decremented by update_budget_on_transaction(). This adds
-- the ceiling as its own column and leaves `budget` untouched for now (dead field,
-- kept one release for rollback safety per 19_0_single_authority.md §2).
--
-- NULL means "not known yet". Nothing may display or reason on this column until
-- limit_confirmed_at is set — an unconfirmed guess must never drive a number.
alter table public.zad_users
  add column if not exists monthly_limit numeric,
  add column if not exists limit_confirmed_at timestamptz;

-- ── Backfill ────────────────────────────────────────────────────────────────
-- The formula in 19_0_single_authority.md §2 was `budget + sum(expenses)`. That is
-- wrong: it inverts only one branch of update_budget_on_transaction(), which does
--   is_expense=true  -> budget = budget - amount
--   is_expense=false -> budget = budget + amount
-- so income must be subtracted back out. Corrected inverse:
--
--   monthly_limit = budget + sum(expenses) - sum(income)
--
-- Verified against live data before applying: user e4e5929e had budget=400,
-- expenses=500, income=400. Doc formula gave 900; corrected gives 500.
--
-- MUST run before any junk-row cleanup. trigger_update_budget is AFTER INSERT only
-- (confirmed via pg_get_triggerdef) — it does not fire on DELETE or UPDATE, so
-- deleting a row changes the ledger sums without changing `budget`, and any
-- backfill computed afterwards would be wrong by the deleted amounts.
--
-- limit_confirmed_at stays NULL on purpose. This value is reconstructed, not stated
-- by the user: `budget` is also overwritten by ZadViewModel.loadBudget() on every app
-- open, so two different histories can produce the same number and nothing
-- distinguishes them. The app must confirm it once before any screen reads it.
update public.zad_users u
set monthly_limit = sub.reconstructed
from (
  select u2.id,
         u2.budget
           + coalesce(sum(t.amount) filter (where t.is_expense), 0)
           - coalesce(sum(t.amount) filter (where not t.is_expense), 0) as reconstructed
  from public.zad_users u2
  left join public.zad_transactions t on t.user_id = u2.id
  group by u2.id, u2.budget
) sub
where u.id = sub.id
  and u.monthly_limit is null
  and sub.reconstructed > 0;   -- implausible (<=0) stays NULL so the UI asks instead

comment on column public.zad_users.monthly_limit is
  'User-chosen monthly ceiling. Set by the user, never by a trigger. NULL = unknown, ask.';
comment on column public.zad_users.limit_confirmed_at is
  'When the user confirmed monthly_limit. NULL = captured but unconfirmed; do not display or reason on it.';
