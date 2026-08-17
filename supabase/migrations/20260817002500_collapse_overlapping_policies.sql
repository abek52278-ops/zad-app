-- multiple_permissive_policies: 50 advisor findings across 5 tables. Permissive policies
-- OR together, so N policies on the same (role, command) means Postgres evaluates all N
-- on every row — and, more importantly, the BROADEST one decides. Two of these were not
-- redundant at all; they were holes.
--
-- Each table below states what changed and whether access changed with it.

-- ── family_typing_status ────────────────────────────────────────────────────────
-- INSERT was governed by `auth.role() = 'authenticated'` OR `auth.uid() = user_id`. The
-- first wins, so ANY logged-in user could insert a typing row naming any other user in
-- any family. ACCESS NARROWS, deliberately: you may only write your own row.
drop policy if exists "Allow members to update typing status" on public.family_typing_status;
drop policy if exists "typing_status_insert" on public.family_typing_status;
create policy "typing_status_insert" on public.family_typing_status
  as permissive for insert to public
  with check ((select auth.uid()) = user_id);

-- UPDATE had two policies with literally the same predicate written both ways round.
-- No access change.
drop policy if exists "Allow members to update their typing" on public.family_typing_status;
drop policy if exists "typing_status_update" on public.family_typing_status;
create policy "typing_status_update" on public.family_typing_status
  as permissive for update to public
  using ((select auth.uid()) = user_id);

-- SELECT had the same family scope expressed twice: an inline family_members subquery and
-- get_my_family_ids(). Keeping the helper, which is the project's documented way to scope
-- a family table. No access change.
drop policy if exists "Allow members to view typing status" on public.family_typing_status;
drop policy if exists "typing_status_select" on public.family_typing_status;
create policy "typing_status_select" on public.family_typing_status
  as permissive for select to public
  using (family_id in (select get_my_family_ids()));

-- ── tasbiha_challenge_progress ──────────────────────────────────────────────────
-- Same INSERT hole as above, and it matters more here: this table holds challenge scores,
-- so "any authenticated user" meant any user could write a score row under someone else's
-- name. ACCESS NARROWS, deliberately.
drop policy if exists "Allow members to insert their progress" on public.tasbiha_challenge_progress;
drop policy if exists "tasbiha_progress_insert" on public.tasbiha_challenge_progress;
create policy "tasbiha_progress_insert" on public.tasbiha_challenge_progress
  as permissive for insert to public
  with check ((select auth.uid()) = user_id);

-- Duplicate predicate written both ways round. No access change.
drop policy if exists "Allow members to update their progress" on public.tasbiha_challenge_progress;
drop policy if exists "tasbiha_progress_update" on public.tasbiha_challenge_progress;
create policy "tasbiha_progress_update" on public.tasbiha_challenge_progress
  as permissive for update to public
  using ((select auth.uid()) = user_id);

-- SELECT: own-rows-only was a strict subset of "any progress in a challenge belonging to
-- my family", and the wider one is what the leaderboard needs. Keeping the wider one
-- alone. No access change.
drop policy if exists "Allow members to view challenge progress" on public.tasbiha_challenge_progress;
drop policy if exists "tasbiha_progress_select" on public.tasbiha_challenge_progress;
create policy "tasbiha_progress_select" on public.tasbiha_challenge_progress
  as permissive for select to public
  using (challenge_id in (
    select id from public.family_tasbiha_challenges
    where family_id in (select get_my_family_ids())
  ));

-- ── family_tasbiha_challenges ───────────────────────────────────────────────────
-- INSERT had an admin-only policy AND an any-member policy, so the effective rule today
-- is any-member — the admin one has never restricted anything. That is a product
-- question, not a lint, so this keeps TODAY'S effective behaviour (any member of the
-- family) in a single policy rather than silently deciding it should be admin-only.
-- If admin-only was the intent, narrow the predicate here in a follow-up.
drop policy if exists "Allow admins to create challenges" on public.family_tasbiha_challenges;
drop policy if exists "family_tasbiha_challenges_insert" on public.family_tasbiha_challenges;
create policy "family_tasbiha_challenges_insert" on public.family_tasbiha_challenges
  as permissive for insert to public
  with check (family_id in (select get_my_family_ids()));

-- Same family scope written twice. No access change.
drop policy if exists "Allow members to view family challenges" on public.family_tasbiha_challenges;
drop policy if exists "family_tasbiha_challenges_select" on public.family_tasbiha_challenges;
create policy "family_tasbiha_challenges_select" on public.family_tasbiha_challenges
  as permissive for select to public
  using (family_id in (select get_my_family_ids()));

-- ── family_goals ────────────────────────────────────────────────────────────────
-- FOR ALL already covers SELECT with the identical predicate, so the separate SELECT
-- policy only made Postgres evaluate the same expression twice. No access change.
drop policy if exists "family_goals_select" on public.family_goals;

-- ── zad_users ───────────────────────────────────────────────────────────────────
-- Genuinely two different rules: you read your own row, and a family admin reads a child's
-- row. They cannot be dropped, but the overlap only exists on SELECT, so splitting the
-- FOR ALL policy per-command lets the two SELECT rules become one predicate. No access
-- change — the OR below is exactly what the two policies evaluated to before.
drop policy if exists "user_own_profile" on public.zad_users;
drop policy if exists "family_admin_read_child_budget" on public.zad_users;

create policy "zad_users_select" on public.zad_users
  as permissive for select to public
  using (
    ((select auth.uid()) = id)
    or exists (
      select 1
      from public.family_members fm_admin
      join public.family_members fm_child on fm_child.family_id = fm_admin.family_id
      where fm_admin.user_id = (select auth.uid())
        and fm_admin.role = 'admin'
        and fm_child.user_id = zad_users.id
        and fm_child.role = 'child'
    )
  );

create policy "zad_users_insert" on public.zad_users
  as permissive for insert to public
  with check ((select auth.uid()) = id);

create policy "zad_users_update" on public.zad_users
  as permissive for update to public
  using ((select auth.uid()) = id);

create policy "zad_users_delete" on public.zad_users
  as permissive for delete to public
  using ((select auth.uid()) = id);
