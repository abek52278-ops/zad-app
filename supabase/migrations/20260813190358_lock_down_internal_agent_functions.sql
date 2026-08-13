-- Security advisor flagged: several SECURITY DEFINER functions are executable by
-- anon/authenticated via PostgREST RPC with an arbitrary p_user, because they either
-- have no grant restriction at all, or rely on `auth.uid() is not null and auth.uid()
-- <> p_user` to reject impersonation -- which does NOT cover anon-key-only requests
-- (no user JWT => auth.uid() is null, same as a service-role call), so an unauthenticated
-- caller could trigger writes/proactive scans for any other user by guessing a UUID.
--
-- _agent_spending_ahead_for_user / _agent_med_followup_for_user / agent_proactive_scan
-- are internal to zad-brain's cron-only proactive-scan path (guarded server-side by
-- ZAD-PROACTIVE-CRON-SECRET) -- never meant to be called directly via REST at all.
-- notify_telegram_on_transaction is a trigger function (AFTER INSERT ON zad_transactions)
-- -- Postgres fires it via the trigger regardless of role grants, so revoking direct
-- EXECUTE doesn't affect the trigger, only closes the direct-RPC-call path.
--
-- zad_recompute_consumption / zad_record_observation ARE called by the authenticated
-- Android client with the user's own session (SupabaseRepo.kt), so `authenticated` keeps
-- EXECUTE -- only `anon` is revoked, closing the unauthenticated-impersonation gap while
-- leaving the real caller unaffected (its auth.uid()=p_user check already holds there).

revoke execute on function public._agent_spending_ahead_for_user(uuid) from public, anon, authenticated;
revoke execute on function public._agent_med_followup_for_user(uuid) from public, anon, authenticated;
revoke execute on function public.agent_proactive_scan() from public, anon, authenticated;
revoke execute on function public.notify_telegram_on_transaction() from public, anon, authenticated;
grant execute on function public._agent_spending_ahead_for_user(uuid) to service_role;
grant execute on function public._agent_med_followup_for_user(uuid) to service_role;
grant execute on function public.agent_proactive_scan() to service_role;
grant execute on function public.notify_telegram_on_transaction() to service_role;

revoke execute on function public.zad_recompute_consumption(uuid, text) from anon;
revoke execute on function public.zad_record_observation(uuid, text, numeric, text) from anon;
