-- =====================================================
-- FIX: family_groups RLS — invite-code join flow was failing with
-- "Invalid Invite Code or Connection Error" for any user joining a
-- family they didn't create.
--
-- Root cause (from code review, not a live DB session — see caveat below):
-- `family_groups` is referenced as a foreign key target in
-- 20250101000000_fix_rls_and_tables.sql but is never CREATEd or given an
-- RLS policy by any tracked migration in this repo. That means its schema
-- and RLS state exist only in the live Supabase project, set up outside
-- version control — the same untracked-RLS pattern already documented for
-- affiliate_products/affiliate_clicks/affiliate_catalog_requests.
--
-- The Kotlin client (SupabaseRepo.joinFamilyGroup, SupabaseRepo.kt) does a
-- raw `SELECT * FROM family_groups WHERE invite_code = ?` BEFORE the
-- caller has any family_members row. If family_groups' SELECT policy is
-- scoped to "your own family only" (e.g. `created_by = auth.uid()` or
-- `id IN (get_my_family_ids())`), that lookup returns zero rows for
-- anyone joining a family they didn't create — which is exactly the
-- reported symptom. Self-create-then-self-join (FamilyViewModel.createFamily)
-- still "works" in that scenario because the creator can always see their
-- own row, which is why this bug only shows up for a second/joining account.
--
-- CAVEAT: this migration was written from static code review only — this
-- sandbox has no live Supabase credentials to inspect or test the actual
-- current policy. Apply it via `supabase db push` or the SQL editor, then
-- verify by joining an existing family from a second account.
-- =====================================================

ALTER TABLE family_groups ENABLE ROW LEVEL SECURITY;

-- Drop whatever SELECT/INSERT/UPDATE/DELETE policies exist today under any
-- name, so this migration is safe to re-run and doesn't collide with a
-- manually-created policy of unknown name.
DO $$
DECLARE
    pol RECORD;
BEGIN
    FOR pol IN SELECT policyname FROM pg_policies WHERE tablename = 'family_groups' AND schemaname = 'public'
    LOOP
        EXECUTE 'DROP POLICY IF EXISTS "' || pol.policyname || '" ON family_groups';
    END LOOP;
END $$;

-- SELECT: invite_code is the shared secret that gates access here (same
-- trust model as a Slack/Discord invite link) — anyone authenticated needs
-- to be able to look a group up by its code before they're a member of it,
-- otherwise the join flow can never work. This does not violate the
-- "never trust client-supplied family_id" rule: the client isn't asserting
-- membership, it's presenting a secret code to resolve.
CREATE POLICY "family_groups_select_authenticated"
    ON family_groups FOR SELECT
    TO authenticated
    USING (true);

-- INSERT: any authenticated user can create a new family group
-- (SupabaseRepo.createFamilyGroup — first step of onboarding).
CREATE POLICY "family_groups_insert_authenticated"
    ON family_groups FOR INSERT
    TO authenticated
    WITH CHECK (true);

-- UPDATE/DELETE: restricted to members of that family.
CREATE POLICY "family_groups_update_members"
    ON family_groups FOR UPDATE
    USING (id IN (SELECT public.get_my_family_ids()));

CREATE POLICY "family_groups_delete_members"
    ON family_groups FOR DELETE
    USING (id IN (SELECT public.get_my_family_ids()));
