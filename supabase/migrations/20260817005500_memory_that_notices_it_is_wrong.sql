-- Self-correcting memory (gaps item 4).
--
-- zad_memory_links.relation has accepted 'contradicts' since it was written and nothing
-- has ever produced one. The reason turns out to be worse than "nobody got round to it":
-- zad_memory_upsert cannot produce a contradiction, because it reads one as agreement.
--
-- Its merge rule is `similarity(note, p_note) >= 0.6 -> strengthen`, and strengthening
-- does `note = p_note` plus `confidence + 0.1`. Now take:
--
--   stored: "بيصرف كتير على المطاعم"
--   new:    "مابيصرفش كتير على المطاعم"
--
-- Trigram similarity is high — they are nearly the same string. So the new note silently
-- OVERWRITES the old one and Zad becomes *more* confident, having just been told the
-- opposite of what it believed. The one case where the system should stop and ask is the
-- one case it treats as confirmation.
--
-- The fix is a polarity check in front of the merge. Same text, different negation, is a
-- conflict: nothing is written, and the caller is handed the existing note so the agent
-- can ask which is true instead of quietly picking one.

/**
 * Does this note negate? Deliberately shallow — this decides whether to ask a question,
 * not whether to write data, so a false positive costs one clarifying question and a
 * false negative just restores today's behaviour.
 */
create or replace function public.zad_text_has_negation(t text)
returns boolean
language sql
immutable
parallel safe
as $$
  select coalesce(t, '') ~ (
    -- Arabic: مش/مِش، مو، مب، ما...ش، ليس، أبداً، بطّل، وقف
    '(^|\s)(مش|مِش|مو|مب|ليس|لا|أبدا|أبداً|ابدا|بطل|بطّل|وقف|وقّف|خلاص)($|\s)'
    -- the ما...ش circumfix, e.g. مابيصرفش
    || '|(^|\s)ما\S*ش($|\s)'
    -- English
    || '|(^|\s)(not|no|never|stopped|doesn''t|don''t|isn''t|won''t)($|\s)'
  );
$$;

comment on function public.zad_text_has_negation(text) is
  'Shallow negation detector used to tell a contradiction apart from a restatement.';

-- Returns 'inserted' | 'strengthened' | 'conflict' (unchanged for the first two, so
-- existing callers keep working; 'conflict' is the new third outcome).
create or replace function public.zad_memory_upsert(
  p_user uuid,
  p_scope text,
  p_note text,
  p_conf real default 0.5
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
  select id, note, similarity(note, p_note)
    into v_id, v_note, v_sim
    from public.zad_memory
   where user_id = p_user and scope = p_scope
   order by similarity(note, p_note) desc
   limit 1;

  if v_id is not null and v_sim >= 0.6 then
    -- Nearly the same sentence with the negation flipped is not the same observation
    -- twice. Refuse both the overwrite and the confidence bump, and say so.
    if public.zad_text_has_negation(v_note) <> public.zad_text_has_negation(p_note) then
      return 'conflict';
    end if;

    update public.zad_memory
       set evidence_count = evidence_count + 1,
           confidence = least(1.0, confidence + 0.1),
           last_seen = now(),
           note = p_note
     where id = v_id;
    return 'strengthened';
  end if;

  insert into public.zad_memory(user_id, scope, note, confidence)
  values (p_user, p_scope, p_note, coalesce(p_conf, 0.5));
  return 'inserted';
end
$function$;

/**
 * The note a new observation would collide with, or null. Read-only — the agent calls this
 * (through the remember tool) to quote the existing belief back to the customer.
 */
create or replace function public.zad_memory_conflict(p_user uuid, p_scope text, p_note text)
returns jsonb
language sql
stable
security definer
set search_path to 'public', 'extensions', 'pg_temp'
as $$
  select case
    when auth.uid() is not null and auth.uid() <> p_user then null::jsonb
    else (
      select jsonb_build_object(
        'id', m.id, 'note', m.note, 'confidence', m.confidence,
        'evidence_count', m.evidence_count, 'similarity', round(similarity(m.note, p_note)::numeric, 3)
      )
      from public.zad_memory m
      where m.user_id = p_user
        and m.scope = p_scope
        and similarity(m.note, p_note) >= 0.6
        and public.zad_text_has_negation(m.note) <> public.zad_text_has_negation(p_note)
      order by similarity(m.note, p_note) desc
      limit 1
    )
  end;
$$;

/**
 * The customer settled it. Replaces the old belief with the new one, records the
 * 'contradicts' link so the history is visible, and drops the old note's confidence
 * instead of deleting it — being wrong once is evidence too.
 */
create or replace function public.zad_memory_resolve_conflict(
  p_user uuid,
  p_old_id uuid,
  p_scope text,
  p_note text,
  p_conf real default 0.6
)
returns jsonb
language plpgsql
security definer
set search_path to 'public', 'extensions', 'pg_temp'
as $function$
declare
  v_new_id uuid;
begin
  if auth.uid() is not null and auth.uid() <> p_user then
    raise exception 'not_authorized';
  end if;

  -- Ownership check before anything: the brain runs as service_role and bypasses RLS, so
  -- a hallucinated id must not be able to touch another account's memory.
  if not exists (select 1 from public.zad_memory where id = p_old_id and user_id = p_user) then
    return jsonb_build_object('ok', false, 'reason', 'unknown_note');
  end if;

  insert into public.zad_memory(user_id, scope, note, confidence)
  values (p_user, p_scope, p_note, coalesce(p_conf, 0.6))
  returning id into v_new_id;

  update public.zad_memory
     set confidence = greatest(0.1, confidence - 0.3),
         last_seen = now()
   where id = p_old_id;

  insert into public.zad_memory_links(user_id, from_id, to_id, relation, strength, evidence_count)
  values (p_user, v_new_id, p_old_id, 'contradicts', 0.8, 1)
  on conflict do nothing;

  return jsonb_build_object('ok', true, 'new_id', v_new_id, 'old_id', p_old_id);
end
$function$;

revoke execute on function public.zad_memory_conflict(uuid, text, text) from public;
revoke execute on function public.zad_memory_resolve_conflict(uuid, uuid, text, text, real) from public;
grant execute on function public.zad_memory_conflict(uuid, text, text) to authenticated, service_role;
grant execute on function public.zad_memory_resolve_conflict(uuid, uuid, text, text, real) to authenticated, service_role;
