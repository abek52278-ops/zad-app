-- proacl showed EXECUTE granted to the PUBLIC pseudo-role ("=X/postgres"), which anon
-- inherits implicitly regardless of a direct per-role revoke -- the previous migration's
-- "revoke ... from anon" only strips a direct anon grant, which never existed here; the
-- PUBLIC entry itself must be revoked. `authenticated=X` is a separate, explicit grant
-- and is untouched by this -- the Android client keeps working.

revoke execute on function public.zad_recompute_consumption(uuid, text) from public;
revoke execute on function public.zad_record_observation(uuid, text, numeric, text) from public;
