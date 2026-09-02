-- zad_memory_upsert(p_user, ...) is SECURITY DEFINER and never checked that the caller
-- actually IS p_user. Net effect verified live 2026-09-02: an unauthenticated (anon key)
-- caller, or any logged-in user, could pass an arbitrary UUID as p_user and
-- insert/strengthen a fabricated note in a stranger's zad_memory — which zad-brain later
-- reads back into that stranger's own chat context as ground truth about their own life.
--
-- This migration is the identity check half of the fix (safe for every caller: a
-- service_role caller presents no JWT subject, so auth.uid() is null and passes through
-- untouched; a normal authenticated user is only allowed to write p_user = their own
-- auth.uid()). The REVOKE half shipped here (`... FROM anon`) turned out to be
-- insufficient on its own — see 20260902005700_secure_zad_memory_upsert_from_public.sql
-- for why and the actual fix. Left as originally applied rather than rewritten, so this
-- file matches what the remote database has recorded at this version.
revoke execute on function public.zad_memory_upsert(uuid, text, text, real, uuid) from anon;

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
