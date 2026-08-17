-- Performance advisors, run for the first time on 2026-08-16: 150 findings. This handles
-- the two that cost real query time. Nothing here changes who can see what.
--
-- 1. auth_rls_initplan (68 policies, 45 tables)
--
-- A policy written `auth.uid() = user_id` re-evaluates auth.uid() once PER ROW, because
-- the planner cannot hoist a volatile-looking call out of the filter. Wrapped as a scalar
-- subquery — `(select auth.uid()) = user_id` — it becomes an InitPlan evaluated once per
-- statement. Identical semantics, and it is the fix Supabase's own advisor names.
--
-- Done as a DO block reading pg_policies rather than 68 hand-written CREATE POLICY
-- statements, for one reason: transcribing a security predicate by hand is how a policy
-- silently gets loosened. This re-emits each policy from what the catalogue actually
-- holds, changing only the auth.* call sites. The unwrap-then-wrap pair makes it
-- idempotent, so a re-run is a no-op rather than `(select (select auth.uid()))`.
do $$
declare
  r record;
  new_qual text;
  new_check text;
  stmt text;
  n int := 0;
begin
  for r in
    select tablename, policyname, permissive, roles, cmd, qual, with_check
    from pg_policies
    where schemaname = 'public'
      and ( (qual is not null and qual ~ 'auth\.(uid|jwt|role)\(\)')
         or (with_check is not null and with_check ~ 'auth\.(uid|jwt|role)\(\)') )
  loop
    -- unwrap first so the wrap below cannot nest
    new_qual := replace(replace(replace(r.qual,
      '(select auth.uid())','auth.uid()'),'(select auth.role())','auth.role()'),'(select auth.jwt())','auth.jwt()');
    new_qual := replace(replace(replace(new_qual,
      'auth.uid()','(select auth.uid())'),'auth.role()','(select auth.role())'),'auth.jwt()','(select auth.jwt())');

    new_check := replace(replace(replace(r.with_check,
      '(select auth.uid())','auth.uid()'),'(select auth.role())','auth.role()'),'(select auth.jwt())','auth.jwt()');
    new_check := replace(replace(replace(new_check,
      'auth.uid()','(select auth.uid())'),'auth.role()','(select auth.role())'),'auth.jwt()','(select auth.jwt())');

    execute format('drop policy %I on public.%I', r.policyname, r.tablename);

    stmt := format('create policy %I on public.%I as %s for %s to %s',
      r.policyname,
      r.tablename,
      case when r.permissive = 'PERMISSIVE' then 'permissive' else 'restrictive' end,
      r.cmd,
      array_to_string(r.roles, ', '));
    if new_qual is not null then
      stmt := stmt || format(' using (%s)', new_qual);
    end if;
    if new_check is not null then
      stmt := stmt || format(' with check (%s)', new_check);
    end if;
    execute stmt;
    n := n + 1;
  end loop;
  raise notice 'rewrote % policies to use an InitPlan for auth.*()', n;
end $$;

-- 2. unindexed_foreign_keys (10)
--
-- A foreign key with no covering index makes every cascade and every join on it a
-- sequential scan. Two of these are on the pharmacy path that this branch already
-- touches: zad_dose_log.pharmacy_item_id and zad_pharmacy_doses.item_id are read per
-- medicine on every adherence calculation.
create index if not exists idx_agent_actions_run_id on public.agent_actions (run_id);
create index if not exists idx_agent_drift_events_run_id on public.agent_drift_events (run_id);
create index if not exists idx_seasonal_events_created_by on public.seasonal_events (created_by);
create index if not exists idx_sinking_funds_created_by on public.sinking_funds (created_by);
create index if not exists idx_zad_brain_queue_user_id on public.zad_brain_queue (user_id);
create index if not exists idx_zad_dose_log_pharmacy_item_id on public.zad_dose_log (pharmacy_item_id);
create index if not exists idx_zad_notification_ingest_events_transaction_id on public.zad_notification_ingest_events (transaction_id);
create index if not exists idx_zad_obligations_user_id on public.zad_obligations (user_id);
create index if not exists idx_zad_pharmacy_doses_item_id on public.zad_pharmacy_doses (item_id);
create index if not exists idx_zad_pharmacy_items_family_member_id on public.zad_pharmacy_items (family_member_id);

-- The advisor's 22 unused_index findings are deliberately NOT acted on. On a project this
-- young "unused" mostly means "not yet used" — dropping an index because it has no scans
-- after a few weeks of single-family traffic would be reading noise as signal.
