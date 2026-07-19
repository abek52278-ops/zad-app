-- =====================================================
-- FIX: Supabase Database - Fix RLS Policies & Missing Columns
-- Run this ENTIRE script in: Supabase Dashboard > SQL Editor > Run
-- =====================================================

-- 0. Create SECURITY DEFINER function to break RLS recursion
CREATE OR REPLACE FUNCTION public.get_my_family_ids()
RETURNS SETOF UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT family_id FROM family_members WHERE user_id = auth.uid();
$$;

-- 1. DROP ALL existing policies on family_members (fixes infinite recursion)
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

-- 3. RECREATE family_members RLS policies using SECURITY DEFINER function (no recursion)
CREATE POLICY "family_members_select"
    ON family_members FOR SELECT
    USING (
        family_id IN (SELECT public.get_my_family_ids())
        OR user_id = auth.uid()
    );

CREATE POLICY "family_members_insert"
    ON family_members FOR INSERT
    WITH CHECK (auth.role() = 'authenticated');

CREATE POLICY "family_members_update"
    ON family_members FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_members_delete"
    ON family_members FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- 4. Recreate all other family table policies using the same function
-- chat_messages
DO $$ DECLARE pol RECORD; BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'chat_messages' AND schemaname = 'public'
    LOOP EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON chat_messages'; END LOOP;
END $$;

CREATE POLICY "chat_messages_select" ON chat_messages FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "chat_messages_insert" ON chat_messages FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "chat_messages_update" ON chat_messages FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- family_chores
DO $$ DECLARE pol RECORD; BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'family_chores' AND schemaname = 'public'
    LOOP EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON family_chores'; END LOOP;
END $$;

CREATE POLICY "family_chores_select" ON family_chores FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "family_chores_insert" ON family_chores FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "family_chores_update" ON family_chores FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "family_chores_delete" ON family_chores FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- family_goals
DO $$ DECLARE pol RECORD; BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'family_goals' AND schemaname = 'public'
    LOOP EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON family_goals'; END LOOP;
END $$;

CREATE POLICY "family_goals_select" ON family_goals FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "family_goals_modify" ON family_goals FOR ALL
    USING (family_id IN (SELECT public.get_my_family_ids()));

-- family_tasbiha
DO $$ DECLARE pol RECORD; BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'family_tasbiha' AND schemaname = 'public'
    LOOP EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON family_tasbiha'; END LOOP;
END $$;

CREATE POLICY "family_tasbiha_select" ON family_tasbiha FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "family_tasbiha_update" ON family_tasbiha FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "family_tasbiha_insert" ON family_tasbiha FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));

-- shared_grocery_list
DO $$ DECLARE pol RECORD; BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'shared_grocery_list' AND schemaname = 'public'
    LOOP EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON shared_grocery_list'; END LOOP;
END $$;

CREATE POLICY "grocery_select" ON shared_grocery_list FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "grocery_insert" ON shared_grocery_list FOR INSERT
    WITH CHECK (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "grocery_update" ON shared_grocery_list FOR UPDATE
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "grocery_delete" ON shared_grocery_list FOR DELETE
    USING (family_id IN (SELECT public.get_my_family_ids()));

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

CREATE POLICY "tasbiha_progress_select" ON tasbiha_challenge_progress FOR SELECT
    USING (auth.uid() = user_id);
CREATE POLICY "tasbiha_progress_insert" ON tasbiha_challenge_progress FOR INSERT
    WITH CHECK (auth.uid() = user_id);
CREATE POLICY "tasbiha_progress_update" ON tasbiha_challenge_progress FOR UPDATE
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

CREATE POLICY "typing_status_select" ON family_typing_status FOR SELECT
    USING (family_id IN (SELECT public.get_my_family_ids()));
CREATE POLICY "typing_status_insert" ON family_typing_status FOR INSERT
    WITH CHECK (auth.uid() = user_id);
CREATE POLICY "typing_status_update" ON family_typing_status FOR UPDATE
    USING (auth.uid() = user_id);

-- 9. Enable Realtime for key tables (ignore errors if already added)
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE chat_messages;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE family_typing_status;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE family_members;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- =====================================================
-- DONE! Run this and all family features should work.
-- =====================================================
