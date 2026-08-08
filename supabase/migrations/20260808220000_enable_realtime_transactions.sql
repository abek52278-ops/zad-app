-- zad_transactions was never added to the supabase_realtime publication (only
-- chat_messages/family_typing_status/family_members were, in the original
-- 20250101000000 migration). Two consumers were already written against a live
-- feed that could never fire because of this:
--   - RealtimeFamilySpendingRepo (FamilyViewModel) — admin's "child just spent" live update
--   - the new RealtimePersonalTransactionsRepo (ZadViewModel) — CashCard staying in sync
--     with a transaction written by @ZadhApp_bot on Telegram instead of the app itself
ALTER PUBLICATION supabase_realtime ADD TABLE zad_transactions;
