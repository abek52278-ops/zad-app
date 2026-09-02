-- Follow-up to 20260902005638_secure_zad_memory_upsert.sql, applied the same day.
-- That migration's `REVOKE ... FROM anon` was not enough: `pg_proc.proacl` showed the
-- function's ACL granted EXECUTE to PUBLIC (the bare `=X/postgres` entry), and every
-- role — including anon — inherits from PUBLIC regardless of a role-specific revoke.
-- Verified live: after the anon-only revoke, `has_function_privilege('anon', ...,
-- 'EXECUTE')` still returned true. Revoking from PUBLIC and re-granting explicitly to
-- the two roles that legitimately call this function (authenticated — the Android
-- client's own dismissInsightWithReason(), always for itself; service_role —
-- zad-brain, zad-telegram-bot) closes it for real. Idempotent: safe to run again even
-- though it was already applied by hand against production the same day this file was
-- written, which is why this migration exists — the earlier fix was applied via a raw
-- SQL execution with no tracked migration version, leaving the database and this repo's
-- migration history out of sync until now.
revoke execute on function public.zad_memory_upsert(uuid, text, text, real, uuid) from public;
grant execute on function public.zad_memory_upsert(uuid, text, text, real, uuid) to authenticated, service_role;
