-- =====================================================
-- zad_subscriptions.due_day / auto_deduct exist in the Kotlin ZadSubscription
-- model (ZadSubscription.kt-era addition) but were never actually migrated —
-- only zad_debts got a due_day column (20260721170000). Any addSubscription()
-- call setting these would fail remotely since the columns don't exist.
-- Adding them here, plus enabling the scheduled auto-deduct worker.
-- =====================================================

ALTER TABLE zad_subscriptions
  ADD COLUMN IF NOT EXISTS due_day INTEGER,
  ADD COLUMN IF NOT EXISTS auto_deduct BOOLEAN NOT NULL DEFAULT FALSE;
