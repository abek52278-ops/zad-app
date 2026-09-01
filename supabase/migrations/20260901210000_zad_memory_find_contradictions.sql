-- بند 31.3 (تقليم المتناقض) — zad_memory_upsert() بيرفض كتابة ملاحظة جديدة لو
-- بتناقض أقرب ملاحظة موجودة (similarity>=0.6 + negation مختلفة)، لكن ده بيصد بس
-- وقت الكتابة عبر مسار واحد. ملاحظتين اتكتبوا بمسارات مختلفة (remember() يدوي،
-- استخلاص ما بعد اللفة 31.2، تأمل الليل نفسه) ممكن يتلاقوا في الجدول من غير ما
-- تتقارن ببعض خالص. الفانكشن دي بتلاقي الأزواج المتبقية دي عشان حلقة التأمل الليلي
-- (31.3) تربطهم relation='contradicts' في zad_memory_links بدل ما يفضلوا حقيقتين
-- متناقضتين ساكتين في نفس الscope — مفيش حذف هنا عن قصد، نفس فلسفة zad_memory_upsert
-- نفسها: تعارض بيتسجّل كإشارة، مش بيتحل بمسح بيانات العميل.
create or replace function public.zad_memory_find_contradictions(p_user uuid)
returns table(from_id uuid, to_id uuid, from_note text, to_note text)
language sql
stable
security definer
set search_path to 'public', 'extensions', 'pg_temp'
as $function$
  select a.id, b.id, a.note, b.note
    from public.zad_memory a
    join public.zad_memory b
      on a.user_id = b.user_id
     and a.scope = b.scope
     and a.id < b.id
   where a.user_id = p_user
     and similarity(a.note, b.note) >= 0.5
     and public.zad_text_has_negation(a.note) <> public.zad_text_has_negation(b.note)
     and not exists (
       select 1 from public.zad_memory_links l
        where l.user_id = p_user and l.relation = 'contradicts'
          and ((l.from_id = a.id and l.to_id = b.id) or (l.from_id = b.id and l.to_id = a.id))
     );
$function$;

revoke all on function public.zad_memory_find_contradictions(uuid) from public, anon, authenticated;
grant execute on function public.zad_memory_find_contradictions(uuid) to service_role;
