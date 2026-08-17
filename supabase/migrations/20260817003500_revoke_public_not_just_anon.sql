-- The previous attempt at this (20260816230000) did not work, and the session notes
-- recorded it as fixed. It ran `revoke execute ... from anon`, but the grant was never to
-- anon — it was to PUBLIC:
--
--   proacl = {=X/postgres, postgres=X/postgres, authenticated=X/postgres, service_role=X/postgres}
--             ^^^^^^^^^^^ empty grantee = PUBLIC
--
-- Every role is implicitly a member of PUBLIC, so revoking from anon removed a grant that
-- did not exist and left the PUBLIC one untouched. Verified after that migration shipped:
-- has_function_privilege('anon', 'public.zad_behavior_patterns(uuid)', 'EXECUTE') is still
-- true. The hole described in that file is still open exactly as described — the anon key
-- ships inside the APK, so anyone who unpacks it can POST any uuid to
-- /rest/v1/rpc/zad_behavior_patterns and read that person's spending profile.
--
-- Postgres grants EXECUTE on every new function to PUBLIC by default, which is why this
-- keeps happening. The revoke has to name PUBLIC.

-- Reads another user's behaviour profile when given their uuid. authenticated and
-- service_role keep the explicit grants 20260816000000 gave them; only the blanket
-- PUBLIC grant goes.
revoke execute on function public.zad_behavior_patterns(uuid) from public;
grant execute on function public.zad_behavior_patterns(uuid) to authenticated, service_role;

-- These three are trigger functions — `for each row execute function ...` on auth.users,
-- zad_transactions and zad_users respectively. A trigger runs as the table owner and does
-- not consult EXECUTE grants at all, so nothing needs to call them over PostgREST and
-- nothing will break by making that impossible. They were only reachable at
-- /rest/v1/rpc/<name> because of the default PUBLIC grant.
revoke execute on function public.zad_provision_user_row() from public;
revoke execute on function public.zad_normalize_transaction_currency() from public;
revoke execute on function public.zad_retire_market_insight() from public;
