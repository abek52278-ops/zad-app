-- ═══════════════════════════════════════════════════════════
-- بند 35.1 — أرقام حقيقية للكروت الأربعة اللي فوق ودجت الكرة العصبية 3D
-- (ZadHomeGlanceCards.kt's Zad3DNeuralSphereWidget): "412" عقدة، "2,103" رابط،
-- "+196%" نمو، "96%" دقة تنبؤ — كل الأربعة كانوا حروف مكتوبة (§4 من
-- ZAD_V2_MASTER_PLAN.md)، الودجت مكانتش بتاخد أي داتا أصلاً. الخريطة من نفس
-- الخطة (§35): العقد = zad_memory + zad_skills، الروابط = zad_memory_links
-- (كانت صفر قبل بند 31.1 النهاردة، دلوقتي حقيقية)، النمو = فرق العدّ عن 30 يوم،
-- دقة التنبؤ = نسبة قبول zad_transaction_proposals.
--
-- growth_pct و prediction_accuracy_pct بيرجعوا null لو مفيش بيانات كفاية للمقارنة
-- (مش صفر، ومش رقم مخترع) — نفس مبدأ "رقم حقيقي أو حالة فاضية شريفة" المستخدم في
-- كل شغل النهاردة. الكلاينت بيعرض "لسه مفيش بيانات كفاية" لو null، مش يعرض 0%.
-- ═══════════════════════════════════════════════════════════
create or replace function public.zad_brain_stats(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_memory_count integer;
  v_skills_count integer;
  v_links_count integer;
  v_active_nodes integer;
  v_recent_count integer;
  v_previous_count integer;
  v_growth_pct numeric;
  v_posted integer;
  v_rejected integer;
  v_prediction_accuracy_pct numeric;
begin
  if auth.uid() is null and coalesce(auth.role(), '') <> 'service_role' then
    return jsonb_build_object('error', 'forbidden');
  end if;
  if auth.uid() is not null and auth.uid() <> p_user then
    return jsonb_build_object('error', 'forbidden');
  end if;

  select count(*) into v_memory_count from public.zad_memory where user_id = p_user;
  select count(*) into v_skills_count from public.zad_skills where user_id = p_user and retired_at is null;
  select count(*) into v_links_count from public.zad_memory_links where user_id = p_user;
  v_active_nodes := v_memory_count + v_skills_count;

  -- النمو: عدد ملاحظات+مهارات اتكتبت آخر 30 يوم مقابل الـ30 يوم اللي قبلهم.
  -- v_previous_count = 0 معناه مفيش أساس نقارن عليه (حساب جديد) — مش نمو "لا نهائي"
  -- ولا صفر، مجرد "متعرفش لسه".
  select count(*) into v_recent_count from (
    select created_at from public.zad_memory where user_id = p_user and created_at >= now() - interval '30 days'
    union all
    select created_at from public.zad_skills where user_id = p_user and created_at >= now() - interval '30 days'
  ) recent;
  select count(*) into v_previous_count from (
    select created_at from public.zad_memory where user_id = p_user and created_at >= now() - interval '60 days' and created_at < now() - interval '30 days'
    union all
    select created_at from public.zad_skills where user_id = p_user and created_at >= now() - interval '60 days' and created_at < now() - interval '30 days'
  ) previous;
  v_growth_pct := case when v_previous_count > 0
    then round(((v_recent_count - v_previous_count)::numeric / v_previous_count) * 100, 1)
    else null
  end;

  -- دقة التنبؤ: من كل الاقتراحات المتحسومة (اتقبلت أو اترفضت، مش لسه مستنية) آخر 90
  -- يوم، نسبة اللي اتقبلت. الاقتراح المرفوض مش معناه "غلط" بالضرورة (ممكن العميل
  -- يرفض معاملة حقيقية لأسباب تانية) لكنه أقرب مقياس عملي متاح دلوقتي.
  select count(*) filter (where status = 'posted'), count(*) filter (where status = 'rejected')
    into v_posted, v_rejected
    from public.zad_transaction_proposals
    where user_id = p_user and decided_at >= now() - interval '90 days'
      and status in ('posted', 'rejected');
  v_prediction_accuracy_pct := case when (v_posted + v_rejected) > 0
    then round((v_posted::numeric / (v_posted + v_rejected)) * 100, 1)
    else null
  end;

  return jsonb_build_object(
    'active_nodes', v_active_nodes,
    'memory_count', v_memory_count,
    'skills_count', v_skills_count,
    'neural_links', v_links_count,
    'growth_pct', v_growth_pct,
    'prediction_accuracy_pct', v_prediction_accuracy_pct
  );
end;
$function$;

revoke execute on function public.zad_brain_stats(uuid) from public, anon;
grant execute on function public.zad_brain_stats(uuid) to authenticated, service_role;
