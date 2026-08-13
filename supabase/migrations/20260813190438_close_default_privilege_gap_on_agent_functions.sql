-- Same root cause as the previous migration's functions: Supabase's default privileges
-- grant EXECUTE to anon/authenticated on new functions automatically, and this project's
-- existing "revoke all ... from public" pattern (zad_agent_undo, zad_agent_usage_record)
-- does not actually strip that -- PUBLIC and anon/authenticated are separate grantees here,
-- confirmed by the advisor still flagging both after the from-public-only revoke.
--
-- zad_agent_undo IS called by the authenticated Android client with the user's own session
-- (SupabaseRepo.kt) and already checks auth.uid() ownership internally -- authenticated
-- keeps EXECUTE, only anon (no session at all) is revoked.
--
-- zad_agent_usage_record is internal-only (LLM token usage bookkeeping, never called by
-- the client) -- revoke both anon and authenticated, service_role grant already correct.

revoke execute on function public.zad_agent_undo(uuid) from anon;
revoke execute on function public.zad_agent_usage_record(uuid, integer, integer) from anon, authenticated;
