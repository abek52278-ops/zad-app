-- =====================================================
-- zad_dose_log: سجل جرعات الدواء (اتاخدت/فاتت) لحساب نسبة الالتزام الأسبوعية
-- =====================================================

CREATE TABLE IF NOT EXISTS zad_dose_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    pharmacy_item_id UUID REFERENCES zad_pharmacy_items(id) ON DELETE CASCADE,
    item_name TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    taken_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE zad_dose_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_own_dose_log" ON zad_dose_log;
CREATE POLICY "user_own_dose_log" ON zad_dose_log FOR ALL USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_zad_dose_log_user_id ON zad_dose_log(user_id);
CREATE INDEX IF NOT EXISTS idx_zad_dose_log_scheduled_at ON zad_dose_log(scheduled_at);
