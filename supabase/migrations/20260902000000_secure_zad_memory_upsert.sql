-- zad_memory_upsert(p_user, ...) is SECURITY DEFINER and never checked that the caller
-- actually IS p_user. It was also GRANTed to anon. Net effect verified live 2026-09-02:
-- an unauthenticated (anon key) caller, or any logged-in user, could pass an arbitrary
-- UUID as p_user and insert/strengthen a fabricated note in a stranger's zad_memory —
-- which zad-brain later reads back into that stranger's own chat context as ground truth
-- about their own life. Two separate holes, two separate fixes:
--
--  1. REVOKE from anon — this function has no business being reachable without a session
--     at all; only zad-brain/zad-telegram-bot (service_role) and the Android client's own
--     dismissInsightWithReason() (authenticated, calling for itself) ever call it.
--  2. auth.uid() identity check inside the function — service_role callers present no
--     JWT subject (auth.uid() is null), so they pass through untouched; a normal
--     authenticated user is only allowed to write p_user = their own auth.uid().
--     REVOKE alone doesn't cover this half: anon and service_role both read as
--     auth.uid() IS NULL, so the grant-level fix can't distinguish "no session" from
--     "trusted server", and the in-function check can't distinguish "no session" from
--     "trusted server" either — only doing both together closes it.

-- REVOKE ... FROM anon alone is not enough: the function's ACL granted EXECUTE to
-- PUBLIC (verified live via pg_proc.proacl — `{=X/postgres,...}`, the bare `=` entry
-- being PUBLIC), and every role including anon inherits from PUBLIC regardless of a
-- role-specific revoke. Revoke from PUBLIC and re-grant explicitly to the two roles
-- that legitimately call this: authenticated (the Android client's own
-- dismissInsightWithReason(), always for itself) and service_role (zad-brain,
-- zad-telegram-bot).
revoke execute on function public.zad_memory_upsert(uuid, text, text, real, uuid) from public;
grant execute on function public.zad_memory_upsert(uuid, text, text, real, uuid) to authenticated, service_role;

create or replace function public.zad_memory_upsert(
  p_user uuid, p_scope text, p_note text, p_conf real default 0.5, p_family_id uuid default null::uuid
)
returns text
language plpgsql
security definer
set search_path to 'public', 'extensions', 'pg_temp'
as $function$
declare
  v_id uuid;
  v_sim real;
  v_note text;
begin
  if auth.uid() is not null and auth.uid() <> p_user then
    raise exception 'zad_memory_upsert: p_user must match the authenticated caller';
  end if;

  select id, note, similarity(note, p_note)
    into v_id, v_note, v_sim
    from public.zad_memory
   where user_id = p_user and scope = p_scope
   order by similarity(note, p_note) desc
   limit 1;

  if v_id is not null and v_sim >= 0.6 then
    if public.zad_text_has_negation(v_note) <> public.zad_text_has_negation(p_note) then
      return 'conflict';
    end if;

    update public.zad_memory
       set evidence_count = evidence_count + 1,
           confidence = least(1.0, confidence + 0.1),
           last_seen = now(),
           note = p_note,
           embedding = null,
           family_id = coalesce(p_family_id, family_id)
     where id = v_id;
    return 'strengthened';
  end if;

  insert into public.zad_memory(user_id, scope, note, confidence, family_id)
  values (p_user, p_scope, p_note, coalesce(p_conf, 0.5), p_family_id);
  return 'inserted';
end
$function$;
