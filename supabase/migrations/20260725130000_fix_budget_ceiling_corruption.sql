-- zad_users.budget is the client-set monthly ceiling (SupabaseRepo.updateUserBudget,
-- read by zad-brain/index.ts:62 as "the user's budget"). The trigger below was instead
-- treating it as a running "remaining" total, decrementing/incrementing it on every
-- transaction insert — silently corrupting the ceiling the user actually set, since
-- ZadViewModel.loadBudget() pulls this value down over the local cache almost every
-- app open. Remaining-balance tracking is already correct client-side (BudgetTracker +
-- ZadViewModel's reactive Room Flow recalculation) — this column should only ever be
-- written by the client via updateUserBudget(), never by a transaction-insert trigger.
DROP TRIGGER IF EXISTS trigger_update_budget ON zad_transactions;
DROP FUNCTION IF EXISTS public.update_budget_on_transaction();
