-- Seasonal & Event Budget Forecasting
-- seasonal_events: family_id NULL = global template (Ramadan, Eid al-Fitr,
-- Eid al-Adha, back_to_school seeded below); NOT NULL = family-created
-- custom one-off event.
CREATE TABLE IF NOT EXISTS seasonal_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID REFERENCES family_groups(id) ON DELETE CASCADE,
    slug TEXT,
    name TEXT NOT NULL,
    category_tags TEXT[] NOT NULL DEFAULT '{}',
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    notes TEXT,
    created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE seasonal_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "seasonal_events_select" ON seasonal_events;
CREATE POLICY "seasonal_events_select" ON seasonal_events FOR SELECT
    USING (family_id IS NULL OR family_id IN (SELECT public.get_my_family_ids()));

DROP POLICY IF EXISTS "seasonal_events_insert" ON seasonal_events;
CREATE POLICY "seasonal_events_insert" ON seasonal_events FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

DROP POLICY IF EXISTS "seasonal_events_update" ON seasonal_events;
CREATE POLICY "seasonal_events_update" ON seasonal_events FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

DROP POLICY IF EXISTS "seasonal_events_delete" ON seasonal_events;
CREATE POLICY "seasonal_events_delete" ON seasonal_events FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE INDEX IF NOT EXISTS idx_seasonal_events_family ON seasonal_events(family_id);

-- seasonal_event_windows: Gregorian date range for a given event in a given
-- year. Global reference data (published Hijri-calendar dates), read-only
-- to clients — static seed, not a live Hijri conversion library, to avoid
-- adding a new runtime dependency to the Deno edge function. Needs a
-- periodic top-up migration every couple of years.
CREATE TABLE IF NOT EXISTS seasonal_event_windows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES seasonal_events(id) ON DELETE CASCADE,
    year INTEGER NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    UNIQUE(event_id, year)
);
ALTER TABLE seasonal_event_windows ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "seasonal_event_windows_select" ON seasonal_event_windows;
CREATE POLICY "seasonal_event_windows_select" ON seasonal_event_windows FOR SELECT
    USING (true);
-- No INSERT/UPDATE/DELETE policy: writes locked to service-role/migrations only.

CREATE INDEX IF NOT EXISTS idx_seasonal_event_windows_event ON seasonal_event_windows(event_id);

-- sinking_funds: family-shared savings bucket, optionally tied to an event.
CREATE TABLE IF NOT EXISTS sinking_funds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    event_id UUID REFERENCES seasonal_events(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    target_amount NUMERIC NOT NULL DEFAULT 0,
    current_amount NUMERIC NOT NULL DEFAULT 0,
    target_date TIMESTAMPTZ,
    created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE sinking_funds ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "sinking_funds_select" ON sinking_funds;
CREATE POLICY "sinking_funds_select" ON sinking_funds FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
DROP POLICY IF EXISTS "sinking_funds_insert" ON sinking_funds;
CREATE POLICY "sinking_funds_insert" ON sinking_funds FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));
DROP POLICY IF EXISTS "sinking_funds_update" ON sinking_funds;
CREATE POLICY "sinking_funds_update" ON sinking_funds FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));
DROP POLICY IF EXISTS "sinking_funds_delete" ON sinking_funds;
CREATE POLICY "sinking_funds_delete" ON sinking_funds FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE INDEX IF NOT EXISTS idx_sinking_funds_family ON sinking_funds(family_id);
CREATE INDEX IF NOT EXISTS idx_sinking_funds_event ON sinking_funds(event_id);

-- Seed global event templates + windows.
-- category_tags use the app's real free-text transaction categories
-- (see app/src/main/java/com/example/ui/screens/BudgetScreen.kt:721-728),
-- not invented English slugs — no "education"/"clothing" category exists
-- in this app today, so back-to-school and Eid map onto "تسوق" (shopping).
--
-- Dates below are best-known published Hijri-calendar approximations for
-- AH 1447-1449 — VERIFY against an official Umm al-Qura calendar before
-- relying on them in production and correct if off by a day.
INSERT INTO seasonal_events (id, family_id, slug, name, category_tags, is_recurring)
VALUES
  ('00000000-0000-0000-0000-000000000001', NULL, 'ramadan', 'رمضان', ARRAY['طعام','مطاعم'], true),
  ('00000000-0000-0000-0000-000000000002', NULL, 'eid_al_fitr', 'عيد الفطر', ARRAY['تسوق','مطاعم'], true),
  ('00000000-0000-0000-0000-000000000003', NULL, 'eid_al_adha', 'عيد الأضحى', ARRAY['طعام','تسوق'], true),
  ('00000000-0000-0000-0000-000000000004', NULL, 'back_to_school', 'العودة للمدارس', ARRAY['تسوق'], true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO seasonal_event_windows (event_id, year, start_date, end_date) VALUES
  ('00000000-0000-0000-0000-000000000001', 2026, '2026-02-18', '2026-03-19'),
  ('00000000-0000-0000-0000-000000000002', 2026, '2026-03-20', '2026-03-22'),
  ('00000000-0000-0000-0000-000000000003', 2026, '2026-05-27', '2026-05-30'),
  ('00000000-0000-0000-0000-000000000004', 2026, '2026-08-15', '2026-09-05'),
  ('00000000-0000-0000-0000-000000000001', 2027, '2027-02-08', '2027-03-09'),
  ('00000000-0000-0000-0000-000000000002', 2027, '2027-03-10', '2027-03-12'),
  ('00000000-0000-0000-0000-000000000003', 2027, '2027-05-17', '2027-05-20'),
  ('00000000-0000-0000-0000-000000000004', 2027, '2027-08-15', '2027-09-05')
ON CONFLICT (event_id, year) DO NOTHING;

-- Aggregation RPCs — SECURITY DEFINER so a single call can read across all
-- of a family's members despite zad_transactions' per-user-only RLS
-- (auth.uid() = user_id). Locked to service_role (used only by the
-- zad-core-intelligence edge function), not exposed to authenticated
-- clients directly.
CREATE OR REPLACE FUNCTION public.get_family_category_monthly_stats(p_family_id UUID)
RETURNS TABLE(category TEXT, avg_monthly NUMERIC, month_count INTEGER, total_amount NUMERIC)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    COALESCE(t.category, 'أخرى') AS category,
    ROUND((SUM(t.amount) / GREATEST(COUNT(DISTINCT date_trunc('month', t.created_at)), 1))::numeric, 2) AS avg_monthly,
    COUNT(DISTINCT date_trunc('month', t.created_at))::int AS month_count,
    SUM(t.amount) AS total_amount
  FROM zad_transactions t
  JOIN family_members fm ON fm.user_id = t.user_id
  WHERE fm.family_id = p_family_id
    AND t.is_expense = true
    AND t.created_at >= now() - interval '12 months'
  GROUP BY COALESCE(t.category, 'أخرى');
$$;
-- REVOKE ALL FROM PUBLIC only clears the implicit PUBLIC-pseudo-role grant;
-- this project also grants EXECUTE to anon/authenticated directly via
-- ALTER DEFAULT PRIVILEGES on new public-schema functions, so both must be
-- revoked explicitly or any signed-in user could pass an arbitrary
-- p_family_id and read another family's spending (this function has no
-- caller-owns-that-family check — that's enforced by only ever being
-- called from the service-role edge function).
REVOKE ALL ON FUNCTION public.get_family_category_monthly_stats(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.get_family_category_monthly_stats(UUID) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_family_category_monthly_stats(UUID) TO service_role;

CREATE OR REPLACE FUNCTION public.get_family_event_window_spend(p_family_id UUID, p_start TIMESTAMPTZ, p_end TIMESTAMPTZ)
RETURNS TABLE(category TEXT, total_amount NUMERIC)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(t.category, 'أخرى') AS category, SUM(t.amount) AS total_amount
  FROM zad_transactions t
  JOIN family_members fm ON fm.user_id = t.user_id
  WHERE fm.family_id = p_family_id
    AND t.is_expense = true
    AND t.created_at >= p_start AND t.created_at <= p_end
  GROUP BY COALESCE(t.category, 'أخرى');
$$;
REVOKE ALL ON FUNCTION public.get_family_event_window_spend(UUID, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.get_family_event_window_spend(UUID, TIMESTAMPTZ, TIMESTAMPTZ) FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_family_event_window_spend(UUID, TIMESTAMPTZ, TIMESTAMPTZ) TO service_role;
