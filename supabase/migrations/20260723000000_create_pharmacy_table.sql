-- =====================================================
-- Zad Smart Pharmacy: home medicine inventory
-- Same scoping pattern as zad_inventory (user_id owns the row;
-- family_member_id is a display-only reference to who the medicine
-- is for, not an RLS boundary — mirrors how zad_inventory/zad_subscriptions
-- are user-scoped rather than family-scoped in this app).
-- =====================================================

CREATE TABLE IF NOT EXISTS zad_pharmacy_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    active_ingredient TEXT,
    category TEXT NOT NULL DEFAULT 'عام',
    dosage TEXT,
    remaining_quantity INTEGER NOT NULL DEFAULT 1,
    unit TEXT NOT NULL DEFAULT 'قرص',
    daily_dose_count INTEGER NOT NULL DEFAULT 1,
    expiry_date TEXT,
    price DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    family_member_id UUID REFERENCES family_members(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE zad_pharmacy_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "user_own_pharmacy_items" ON zad_pharmacy_items;
CREATE POLICY "user_own_pharmacy_items" ON zad_pharmacy_items FOR ALL USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_zad_pharmacy_items_user_id ON zad_pharmacy_items(user_id);
