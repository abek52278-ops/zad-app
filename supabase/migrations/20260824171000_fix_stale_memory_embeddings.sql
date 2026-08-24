-- ═══════════════════════════════════════════════════════════
-- 20260824171000 — إصلاح stale embeddings بعد merge.
--
-- المشكلة: zad_memory_upsert في حالة 'strengthened' بيستبدل نص الملاحظة
-- (note = p_note) لكن الـ embedding القديم بيفضل محفوظ — فبقى يمثل نص قديم
-- مش النص الحالي، والاسترجاع الدلالي بيرتّب على أساسه غلط. وzad_memory_set_embedding
-- كان بيشترط embedding is null فمش بيصحّح الحالة دي أبداً.
--
-- الإصلاح:
-- 1) strengthen يصفّر الـ embedding → يتولّد تاني من النص الجديد في الكتابة الجاية
--    (fail-open زي الأول: لو التوليد فشل، الصف يرجع للسلوك keyword-only).
-- 2) set_embedding يبقى overwrite صريح بدل شرط is-null.
-- ═══════════════════════════════════════════════════════════

-- 1) صفّر الـ embedding عند تقوية ملاحظة بنص جديد
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
           note = p_note,
           -- النص اتغير → الـ embedding القديم بقى كاذب. نصفّره عشان يتولّد
           -- من جديد من النص الحالي (backfill في remember tool).
           embedding = null
     where id = v_id;
    return 'strengthened';
  end if;

  insert into public.zad_memory(user_id, scope, note, confidence)
  values (p_user, p_scope, p_note, coalesce(p_conf, 0.5));
  return 'inserted';
end
$function$;

-- 2) الربط بيبقى overwrite — آخر vector للنص الحالي هو الصح دايماً
create or replace function public.zad_memory_set_embedding(
  p_user uuid, p_note text, p_vec vector(768)
)
returns void language sql security definer set search_path = public as $$
  update public.zad_memory
    set embedding = p_vec
    where user_id = p_user and note = p_note;
$$;

revoke execute on function public.zad_memory_upsert(uuid, text, text, real)
  from public, anon, authenticated;
revoke execute on function public.zad_memory_set_embedding(uuid, text, vector(768))
  from public, anon, authenticated;
