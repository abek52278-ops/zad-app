-- بند 34.2 (تكملة) — zad_memory_upsert بتاخد p_family_id اختيارية دلوقتي. الاتنين
-- caller القديمين (index.ts × 2، zad-telegram-bot × 1) هيفضلوا شغالين زي ما هم —
-- الباراميتر الجديد default null، يعني ملاحظة عادية خاصة زي ما كانت دايمًا.
--
-- على strengthen: family_id = coalesce(p_family_id, family_id) — لو الملاحظة كانت
-- متشاركة قبل كده وجت لفة تانية من غير share_with_family=true، تفضل متشاركة (مش
-- بتتسحب المشاركة بصمت لمجرد إن الموديل نسي يبعتها تاني).
create or replace function public.zad_memory_upsert(
  p_user uuid, p_scope text, p_note text, p_conf real default 0.5, p_family_id uuid default null
) returns text
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
