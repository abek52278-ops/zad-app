-- =====================================================
-- Zad Home Maintenance Tracker: appliance/warranty/service scheduling
-- Same scoping pattern as zad_inventory/zad_pharmacy_items (user-owned row).
-- =====================================================

CREATE TABLE IF NOT EXISTS zad_maintenance_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'عام',
    purchase_date TEXT,
    warranty_expiry_date TEXT,
    last_service_date TEXT,
    service_interval_days INTEGER,
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE zad_maintenance_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_own_maintenance_items" ON zad_maintenance_items;
CREATE POLICY "user_own_maintenance_items" ON zad_maintenance_items FOR ALL USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_zad_maintenance_items_user_id ON zad_maintenance_items(user_id);
