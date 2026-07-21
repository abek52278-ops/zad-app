-- =====================================================
-- Zad Intelligence: 6 new analytical features
-- Feature 1: Financial Stress Test — emergency fund balance on zad_users
-- Feature 2: Debt Snowball/Avalanche — new zad_debts table
-- Feature 5: Gamified Financial Challenges — new table pair mirroring
--            family_tasbiha_challenges / tasbiha_challenge_progress
-- Features 3, 4, 6 need no schema changes (reuse zad_transactions,
-- user_behavior_profile.spending_pattern_by_weekday,
-- user_behavior_profile.inventory_consumption_rate, zad_inventory)
-- =====================================================

-- ── Feature 1: Financial Stress Test ──────────────────────────────
-- Manual input field: user enters their liquid/emergency savings once,
-- editable from the intelligence screen. No RLS change needed — zad_users
-- RLS already scopes to the owning row.
ALTER TABLE zad_users
  ADD COLUMN IF NOT EXISTS emergency_fund_balance NUMERIC NOT NULL DEFAULT 0;

-- ── Feature 2: Debt Snowball / Avalanche ──────────────────────────
CREATE TABLE IF NOT EXISTS zad_debts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    family_id UUID REFERENCES family_groups(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    principal_amount NUMERIC NOT NULL DEFAULT 0,
    remaining_balance NUMERIC NOT NULL DEFAULT 0,
    interest_rate NUMERIC NOT NULL DEFAULT 0,       -- annual %, e.g. 18.5
    minimum_payment NUMERIC NOT NULL DEFAULT 0,
    due_day INTEGER,                                -- 1-31, nullable
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE zad_debts ENABLE ROW LEVEL SECURITY;

-- Debts are personal, not family-shared (unlike family_* tables) — scoped
-- to auth.uid() = user_id, matching the affiliate_clicks idiom in
-- 20260720000000_create_affiliate_tables.sql rather than get_my_family_ids().
DROP POLICY IF EXISTS "zad_debts_select_own" ON zad_debts;
CREATE POLICY "zad_debts_select_own"
    ON zad_debts FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "zad_debts_insert_own" ON zad_debts;
CREATE POLICY "zad_debts_insert_own"
    ON zad_debts FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "zad_debts_update_own" ON zad_debts;
CREATE POLICY "zad_debts_update_own"
    ON zad_debts FOR UPDATE
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "zad_debts_delete_own" ON zad_debts;
CREATE POLICY "zad_debts_delete_own"
    ON zad_debts FOR DELETE
    USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_zad_debts_user ON zad_debts(user_id);

-- ── Feature 5: Gamified Financial Challenges ──────────────────────
-- Structurally mirrors family_tasbiha_challenges / tasbiha_challenge_progress
-- (see 20250101000000_fix_rls_and_tables.sql), target_clicks -> target_amount,
-- current_clicks -> current_amount. NOT a reuse of the tasbiha tables —
-- separate domain, separate table pair.
CREATE TABLE IF NOT EXISTS family_financial_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    challenge_type TEXT NOT NULL DEFAULT 'monthly',
    title TEXT NOT NULL,
    description TEXT,
    target_amount NUMERIC NOT NULL DEFAULT 0,
    reward_amount NUMERIC NOT NULL DEFAULT 0,       -- paid into family_members.balance on completion
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE family_financial_challenges ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "family_financial_challenges_select" ON family_financial_challenges;
CREATE POLICY "family_financial_challenges_select"
    ON family_financial_challenges FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

DROP POLICY IF EXISTS "family_financial_challenges_insert" ON family_financial_challenges;
CREATE POLICY "family_financial_challenges_insert"
    ON family_financial_challenges FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

DROP POLICY IF EXISTS "family_financial_challenges_update" ON family_financial_challenges;
CREATE POLICY "family_financial_challenges_update"
    ON family_financial_challenges FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE TABLE IF NOT EXISTS financial_challenge_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id UUID NOT NULL REFERENCES family_financial_challenges(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    current_amount NUMERIC NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE financial_challenge_progress ENABLE ROW LEVEL SECURITY;

-- DELIBERATE DEVIATION from tasbiha_challenge_progress_select (which is
-- auth.uid()=user_id only, so family members can't see each other's
-- progress — a latent leaderboard bug there). A shared challenge needs a
-- shared leaderboard, so SELECT is family-wide via the parent challenge's
-- family_id. INSERT/UPDATE stay owner-scoped so one member can't edit
-- another's progress.
DROP POLICY IF EXISTS "financial_challenge_progress_select" ON financial_challenge_progress;
CREATE POLICY "financial_challenge_progress_select"
    ON financial_challenge_progress FOR SELECT
    USING (
        challenge_id IN (
            SELECT id FROM family_financial_challenges
            WHERE family_id IN (SELECT public.get_my_family_ids())
        )
    );

DROP POLICY IF EXISTS "financial_challenge_progress_insert" ON financial_challenge_progress;
CREATE POLICY "financial_challenge_progress_insert"
    ON financial_challenge_progress FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "financial_challenge_progress_update" ON financial_challenge_progress;
CREATE POLICY "financial_challenge_progress_update"
    ON financial_challenge_progress FOR UPDATE
    USING (auth.uid() = user_id);

-- DONE. Features 3 (Inflation Radar), 4 (Behavioral Nudge Engine), and 6
-- (Smart Buying Timing) need no schema changes — they read existing
-- zad_transactions and user_behavior_profile columns.
