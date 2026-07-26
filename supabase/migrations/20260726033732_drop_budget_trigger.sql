-- Task 19.0 step 3 — stop the trigger that decremented zad_users.budget on every
-- expense insert. It's what made `budget` unusable as a stable ceiling: split into
-- two jobs (the trigger's running balance vs the user's chosen limit), and the
-- 20260726030000_monthly_limit.sql migration already moved the real ceiling to its
-- own column (monthly_limit).
--
-- ORDER IS MANDATORY: trigger first, then the function. Dropping the function while
-- the trigger still references it would make every INSERT on zad_transactions fail.
-- Correct trigger name confirmed live via pg_get_triggerdef(): trigger_update_budget
-- (NOT trg_update_budget_on_transaction, which appeared in an earlier doc draft and
-- does not exist — a DROP by that name would have silently no-opped and left this
-- trigger running under everything built afterward).
drop trigger if exists trigger_update_budget on public.zad_transactions;
drop function if exists public.update_budget_on_transaction();
