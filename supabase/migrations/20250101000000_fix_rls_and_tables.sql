-- =====================================================
-- FIX: Supabase Database - Fix RLS Policies & Missing Columns
-- Run this in Supabase SQL Editor (Dashboard > SQL Editor)
-- =====================================================

-- 1. DROP ALL EXISTING POLICES ON family_members (fixes infinite recursion)
DROP POLICY IF EXISTS "Allow members to read their family" ON family_members;
DROP POLICY IF EXISTS "Allow authenticated users to join families" ON family_members;
DROP POLICY IF EXISTS "Allow admins to update members" ON family_members;
DROP POLICY IF EXISTS "Allow admins to delete members" ON family_members;

-- Also drop any other policies that might exist
DO $$
DECLARE
    pol RECORD;
BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'family_members' AND schemaname = 'public'
    LOOP
        EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON family_members';
    END LOOP;
END $$;

-- 2. ADD missing column to family_members
ALTER TABLE family_members ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- 3. RECREATE family_members RLS policies WITHOUT recursion
-- Use a simpler approach: check if the user is authenticated and is a member
CREATE POLICY "members_select_own_family"
    ON family_members FOR SELECT
    USING (
        auth.uid() = user_id
        OR family_id IN (
            SELECT fm.family_id FROM family_members fm
            WHERE fm.user_id = auth.uid()
            AND fm.id != family_members.id
        )
    );

-- Simpler: any authenticated user can see members (we'll use app-level filtering)
-- Actually, use a security definer function to avoid recursion
CREATE OR REPLACE FUNCTION public.get_my_family_ids()
RETURNS SETOF UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT family_id FROM family_members WHERE user_id = auth.uid();
$$;

-- Now recreate policies using the function (avoids recursion)
DROP POLICY IF EXISTS "members_select_own_family" ON family_members;

CREATE POLICY "family_members_select"
    ON family_members FOR SELECT
    USING (
        family_id IN (SELECT public.get_my_family_ids())
        OR user_id = auth.uid()
    );

CREATE POLICY "family_members_insert"
    ON family_members FOR INSERT
    WITH CHECK (
        auth.role() = 'authenticated'
    );

CREATE POLICY "family_members_update"
    ON family_members FOR UPDATE
    USING (
        family_id IN (SELECT public.get_my_family_ids())
    );

CREATE POLICY "family_members_delete"
    ON family_members FOR DELETE
    USING (
        family_id IN (SELECT public.get_my_family_ids())
    );

-- 4. DROP AND RECREATE all family table policies that reference family_members
-- chat_messages
DROP POLICY IF EXISTS "Allow members to view chat" ON chat_messages;
DROP POLICY IF EXISTS "Allow members to insert chat" ON chat_messages;
DROP POLICY IF EXISTS "Allow members to update chat" ON chat_messages;

CREATE POLICY "chat_messages_select"
    ON chat_messages FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "chat_messages_insert"
    ON chat_messages FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "chat_messages_update"
    ON chat_messages FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- family_chores
DROP POLICY IF EXISTS "Allow members to read chores for their family" ON family_chores;
DROP POLICY IF EXISTS "Allow admins to insert chores" ON family_chores;
DROP POLICY IF EXISTS "Allow members to update chore status" ON family_chores;
DROP POLICY IF EXISTS "Allow admins to delete chores" ON family_chores;

CREATE POLICY "family_chores_select"
    ON family_chores FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_chores_insert"
    ON family_chores FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_chores_update"
    ON family_chores FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_chores_delete"
    ON family_chores FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- family_goals
DROP POLICY IF EXISTS "Allow members to view goals" ON family_goals;
DROP POLICY IF EXISTS "Allow admins to modify goals" ON family_goals;

CREATE POLICY "family_goals_select"
    ON family_goals FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_goals_modify"
    ON family_goals FOR ALL
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- family_tasbiha
DROP POLICY IF EXISTS "Allow members to view family tasbiha" ON family_tasbiha;
DROP POLICY IF EXISTS "Allow members to update family tasbiha" ON family_tasbiha;
DROP POLICY IF EXISTS "Allow members to insert tasbiha" ON family_tasbiha;

CREATE POLICY "family_tasbiha_select"
    ON family_tasbiha FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_tasbiha_update"
    ON family_tasbiha FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_tasbiha_insert"
    ON family_tasbiha FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

-- 5. ADD missing columns to family_tasbiha
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS tree_type TEXT NOT NULL DEFAULT 'normal';
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS garden_name TEXT NOT NULL DEFAULT 'بستاني';
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS is_mature BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS matured_at TIMESTAMPTZ;
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS tree_emoji TEXT NOT NULL DEFAULT '🌰';
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS streak_days INTEGER NOT NULL DEFAULT 0;
ALTER TABLE family_tasbiha ADD COLUMN IF NOT EXISTS last_streak_date DATE;

-- 6. CREATE family_tasbiha_challenges if not exists
CREATE TABLE IF NOT EXISTS family_tasbiha_challenges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    challenge_type TEXT NOT NULL DEFAULT 'weekly',
    title TEXT NOT NULL,
    description TEXT,
    target_clicks INTEGER NOT NULL DEFAULT 100,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE family_tasbiha_challenges ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    DROP POLICY IF EXISTS "family_tasbiha_challenges_select" ON family_tasbiha_challenges;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE POLICY "family_tasbiha_challenges_select"
    ON family_tasbiha_challenges FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_tasbiha_challenges_insert"
    ON family_tasbiha_challenges FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

-- 7. CREATE tasbiha_challenge_progress if not exists
CREATE TABLE IF NOT EXISTS tasbiha_challenge_progress (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    challenge_id UUID NOT NULL REFERENCES family_tasbiha_challenges(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    current_clicks INTEGER NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE tasbiha_challenge_progress ENABLE ROW LEVEL SECURITY;

CREATE POLICY "tasbiha_progress_select"
    ON tasbiha_challenge_progress FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "tasbiha_progress_insert"
    ON tasbiha_challenge_progress FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "tasbiha_progress_update"
    ON tasbiha_challenge_progress FOR UPDATE
    USING (auth.uid() = user_id);

-- 8. CREATE family_typing_status if not exists
CREATE TABLE IF NOT EXISTS family_typing_status (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    family_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    is_typing BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE family_typing_status ENABLE ROW LEVEL SECURITY;

CREATE POLICY "typing_status_select"
    ON family_typing_status FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "typing_status_insert"
    ON family_typing_status FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "typing_status_update"
    ON family_typing_status FOR UPDATE
    USING (auth.uid() = user_id);

-- 9. Enable Realtime for key tables
ALTER PUBLICATION supabase_realtime ADD TABLE chat_messages;
ALTER PUBLICATION supabase_realtime ADD TABLE family_typing_status;
ALTER PUBLICATION supabase_realtime ADD TABLE family_members;

-- 10. Make sure shared_grocery_list policies are not recursive
-- These should already work since they use family_id directly, but let's be safe
DROP POLICY IF EXISTS "Allow members to view grocery list" ON shared_grocery_list;
DROP POLICY IF EXISTS "Allow members to insert grocery items" ON shared_grocery_list;
DROP POLICY IF EXISTS "Allow members to update grocery items" ON shared_grocery_list;
DROP POLICY IF EXISTS "Allow members to delete grocery items" ON shared_grocery_list;

CREATE POLICY "grocery_select"
    ON shared_grocery_list FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "grocery_insert"
    ON shared_grocery_list FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "grocery_update"
    ON shared_grocery_list FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "grocery_delete"
    ON shared_grocery_list FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- DONE! All RLS policies fixed, missing columns added, missing tables created.
