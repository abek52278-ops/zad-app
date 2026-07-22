-- =====================================================
-- CATCH-UP: core zad_* tables, user_behavior_profile, the budget trigger,
-- and required extensions currently exist ONLY on the live Supabase
-- project — no file anywhere in this repo (not supabase/migrations/, not
-- the root-level database_updates.sql / FIX_DATABASE.sql) creates them.
-- Written from the actual live schema via Supabase MCP introspection
-- (list_tables, pg_policies, pg_proc, information_schema.triggers), not
-- reconstructed from memory, so a fresh project bootstrapped from
-- supabase/migrations/*.sql alone now reaches parity with production.
--
-- NOT duplicated here (already exist at the repo root, just outside
-- supabase/migrations/): family_groups, family_members, family_chores,
-- family_tasbiha (base columns), chat_messages, family_goals,
-- shared_grocery_list, app_notifications (base) — see database_updates.sql
-- and FIX_DATABASE.sql.
--
-- Also bundles the two hardening fixes approved alongside this catch-up:
-- (1) app_notifications had an INSERT policy allowing ANY authenticated
--     user to write a notification for ANY other user_id, plus a second
--     policy with WITH CHECK(true) open to public — see "SECURITY FIX"
--     section below.
-- (2) 7 foreign keys across existing tables have no covering index
--     (Supabase performance advisor) — see "PERFORMANCE FIX" section.
-- =====================================================

-- ── Extensions ─────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_cron;
-- pg_net is installed in the public schema on the live project (flagged by
-- the security advisor as extension_in_public). Matched here rather than
-- moved to `extensions` — an ALTER EXTENSION ... SET SCHEMA touches the
-- live cron job in 20260721150210_fix_update_behavior_profile_cron_timeout.sql
-- that calls net.http_post(), and isn't worth the risk to a working
-- production cron pipeline for a WARN-level lint. Left as a known,
-- deliberately-deferred item.
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA public;

-- ── zad_users ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS zad_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT,
    budget DOUBLE PRECISION DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    avatar_uri TEXT,
    emergency_fund_balance NUMERIC NOT NULL DEFAULT 0
);
ALTER TABLE zad_users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_own_profile" ON zad_users;
CREATE POLICY "user_own_profile" ON zad_users FOR ALL USING (auth.uid() = id);

-- ── zad_transactions (+ budget trigger) ────────────────
CREATE TABLE IF NOT EXISTS zad_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    amount DOUBLE PRECISION NOT NULL,
    title TEXT NOT NULL,
    category TEXT,
    is_expense BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE zad_transactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_own_transactions" ON zad_transactions;
CREATE POLICY "user_own_transactions" ON zad_transactions FOR ALL USING (auth.uid() = user_id);

CREATE OR REPLACE FUNCTION public.update_budget_on_transaction()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $function$
BEGIN
    IF NEW.is_expense = true THEN
        UPDATE zad_users SET budget = budget - NEW.amount WHERE id = NEW.user_id;
    ELSE
        UPDATE zad_users SET budget = budget + NEW.amount WHERE id = NEW.user_id;
    END IF;
    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trigger_update_budget ON zad_transactions;
CREATE TRIGGER trigger_update_budget
    AFTER INSERT ON zad_transactions
    FOR EACH ROW EXECUTE FUNCTION update_budget_on_transaction();

-- ── zad_inventory ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS zad_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    item_name TEXT NOT NULL,
    category TEXT,
    quantity INTEGER DEFAULT 1,
    expiry_date TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE zad_inventory ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_own_inventory" ON zad_inventory;
CREATE POLICY "user_own_inventory" ON zad_inventory FOR ALL USING (auth.uid() = user_id);

-- ── zad_subscriptions ──────────────────────────────────
CREATE TABLE IF NOT EXISTS zad_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    renewal_date TEXT,
    category TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    billing_cycle TEXT DEFAULT 'MONTHLY'
);
ALTER TABLE zad_subscriptions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "user_own_subscriptions" ON zad_subscriptions;
CREATE POLICY "user_own_subscriptions" ON zad_subscriptions FOR ALL USING (auth.uid() = user_id);

-- ── zad_shopping_list ──────────────────────────────────
CREATE TABLE IF NOT EXISTS zad_shopping_list (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    item_name TEXT NOT NULL,
    quantity INTEGER DEFAULT 1,
    estimated_price DOUBLE PRECISION DEFAULT 0.0,
    is_purchased BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE zad_shopping_list ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can view their own shopping list" ON zad_shopping_list;
CREATE POLICY "Users can view their own shopping list" ON zad_shopping_list FOR SELECT USING (auth.uid() = user_id);
DROP POLICY IF EXISTS "Users can insert their own shopping list" ON zad_shopping_list;
CREATE POLICY "Users can insert their own shopping list" ON zad_shopping_list FOR INSERT WITH CHECK (auth.uid() = user_id);
DROP POLICY IF EXISTS "Users can update their own shopping list" ON zad_shopping_list;
CREATE POLICY "Users can update their own shopping list" ON zad_shopping_list FOR UPDATE USING (auth.uid() = user_id);
DROP POLICY IF EXISTS "Users can delete their own shopping list" ON zad_shopping_list;
CREATE POLICY "Users can delete their own shopping list" ON zad_shopping_list FOR DELETE USING (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS idx_zad_shopping_list_user ON zad_shopping_list(user_id);

-- ── user_behavior_profile ──────────────────────────────
CREATE TABLE IF NOT EXISTS user_behavior_profile (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    avg_weekly_spending NUMERIC DEFAULT 0,
    top_spending_categories JSONB DEFAULT '[]'::jsonb,
    spending_pattern_by_weekday JSONB DEFAULT '{}'::jsonb,
    inventory_consumption_rate JSONB DEFAULT '{}'::jsonb,
    subscription_load_monthly NUMERIC DEFAULT 0,
    last_updated_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE user_behavior_profile ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users can read own profile" ON user_behavior_profile;
CREATE POLICY "Users can read own profile" ON user_behavior_profile FOR SELECT USING (auth.uid() = user_id);
DROP POLICY IF EXISTS "Users can update own profile" ON user_behavior_profile;
CREATE POLICY "Users can update own profile" ON user_behavior_profile FOR UPDATE USING (auth.uid() = user_id);


-- =====================================================
-- SECURITY FIX 1: app_notifications INSERT policies allowed ANY
-- authenticated user (and, via the second policy, effectively anyone) to
-- write a notification row for ANY user_id in the system — no ownership
-- or family-membership check at all. The client DOES legitimately insert
-- cross-user rows (SupabaseRepo.sendAppNotification, called from
-- FamilyViewModel to notify other family members of chores/goals/chat),
-- so the fix scopes this to "yourself or a member of one of your
-- families" instead of dropping cross-user insert entirely.
-- =====================================================
DROP POLICY IF EXISTS "service_insert_notifications" ON app_notifications;
DROP POLICY IF EXISTS "authenticated_insert_notifications" ON app_notifications;

CREATE POLICY "notify_self_or_family_member"
    ON app_notifications FOR INSERT
    TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        OR user_id IN (
            SELECT fm.user_id FROM family_members fm
            WHERE fm.family_id IN (SELECT public.get_my_family_ids())
        )
    );
-- service_role bypasses RLS entirely (BYPASSRLS), so it needs no explicit
-- policy for server-side inserts from the edge functions.

-- ── SECURITY FIX 2: mutable search_path on SECURITY DEFINER / trigger
-- functions (privilege-escalation vector via search_path hijacking) ──
ALTER FUNCTION public.get_my_family_ids() SET search_path = public;
ALTER FUNCTION public.increment_tasbiha_clicks(
    uuid, integer, integer, text, boolean, timestamptz, integer, text, text
) SET search_path = public;
-- update_budget_on_transaction already gets SET search_path = public from
-- its CREATE OR REPLACE above.

-- ── SECURITY FIX 3: anon has no legitimate reason to call this
-- SECURITY DEFINER function (auth.uid() is null for anon, so it already
-- returns zero rows today — this is defense in depth, not a live leak).
-- REVOKE ... FROM anon alone does NOT work here: the function still
-- carries its default PUBLIC grant, which anon inherits regardless of a
-- role-specific revoke. Must revoke from PUBLIC itself, then re-grant
-- only to the role that legitimately needs it — authenticated, used
-- throughout RLS policies as (SELECT public.get_my_family_ids()).
REVOKE EXECUTE ON FUNCTION public.get_my_family_ids() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_my_family_ids() TO authenticated;


-- =====================================================
-- PERFORMANCE FIX: missing indexes on foreign keys flagged by the
-- Supabase performance advisor. zad_shopping_list.user_id is indexed
-- above alongside its table; zad_debts.family_id needs its own index
-- separate from add_zad_intelligence_features.sql's idx_zad_debts_user
-- (that one only covers user_id, not family_id).
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_family_tasbiha_user ON family_tasbiha(user_id);
CREATE INDEX IF NOT EXISTS idx_family_tasbiha_challenges_family ON family_tasbiha_challenges(family_id);
CREATE INDEX IF NOT EXISTS idx_tasbiha_challenge_progress_user ON tasbiha_challenge_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_family_typing_status_user ON family_typing_status(user_id);
CREATE INDEX IF NOT EXISTS idx_family_financial_challenges_family ON family_financial_challenges(family_id);
CREATE INDEX IF NOT EXISTS idx_financial_challenge_progress_challenge ON financial_challenge_progress(challenge_id);
CREATE INDEX IF NOT EXISTS idx_financial_challenge_progress_user ON financial_challenge_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_zad_debts_family ON zad_debts(family_id);

-- Not fixed here (deliberately deferred, see review notes):
--  - auth_rls_initplan (40 policies use auth.uid() instead of
--    (select auth.uid()), re-evaluated per row) and multiple_permissive_policies
--    (50 cases) span nearly every table's policies across 8 migration
--    files — rewriting all of them safely needs its own reviewed pass,
--    not a blind bundle here.
--  - extension_in_public (pg_net) — see comment above.
--  - auth_leaked_password_protection — Auth setting in the Supabase
--    dashboard (Authentication > Policies), not a SQL migration concern.
--  - family_groups_insert_authenticated WITH CHECK(true) — reviewed:
--    intentional by design (see 20260720010000_fix_family_groups_rls.sql),
--    not a bug.
