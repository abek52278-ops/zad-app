-- =====================================================
-- zad_pharmacy_items: add dose_times (time-of-day schedule, e.g. "08:00,20:00")
-- Completes the DosageSchedule piece of the pharmacy spec that was
-- originally simplified to daily_dose_count only.
-- =====================================================

ALTER TABLE zad_pharmacy_items
  ADD COLUMN IF NOT EXISTS dose_times TEXT;
