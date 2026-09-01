-- بند 35.1 (تكملة) — عقدة "المخزون وتأمين الغذاء" في الودجت العصبي كانت هتاخد متوسط
-- كفاية كل المخزون بالأيام، وده رقم مضلل بطبيعته: ملح يكفي 300 يوم + بصل يكفي يومين
-- متوسطهم 151 يوم — رقم "آمن" وهمي بينما البصل هيخلص بعد يومين. راجعت القرار مع
-- llm-council قبل التنفيذ؛ Gemini جادل بقوة إن "أضعف حلقة" (أقرب صنف للنفاد) أصدق
-- وأكثر فايدة من أي متوسط، مهما كان مفلتر — ده اللي اتنفذ هنا.
--
-- مقصور على zad_consumption.rate_known = true بس (نفس مبدأ باقي الشغل النهاردة: رقم
-- حقيقي من عينات فعلية، مش تخمين لصنف لسه مالوش تاريخ استهلاك كافي). لو مفيش ولا صنف
-- rate_known، next_shortage_item بترجع null والكلاينت يعرض "لسه بيدرس نمط استهلاكك"
-- بدل ما يخترع رقم.
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
  v_shortage_item text;
  v_shortage_days integer;
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

  select count(*) filter (where status = 'posted'), count(*) filter (where status = 'rejected')
    into v_posted, v_rejected
    from public.zad_transaction_proposals
    where user_id = p_user and decided_at >= now() - interval '90 days'
      and status in ('posted', 'rejected');
  v_prediction_accuracy_pct := case when (v_posted + v_rejected) > 0
    then round((v_posted::numeric / (v_posted + v_rejected)) * 100, 1)
    else null
  end;

  select i.item_name, floor(i.quantity / c.avg_daily_qty)::integer
    into v_shortage_item, v_shortage_days
    from public.zad_inventory i
    join public.zad_consumption c on c.user_id = i.user_id and c.item_name = i.item_name
    where i.user_id = p_user and c.rate_known = true and c.avg_daily_qty > 0 and i.quantity > 0
    order by (i.quantity / c.avg_daily_qty) asc
    limit 1;

  return jsonb_build_object(
    'active_nodes', v_active_nodes,
    'memory_count', v_memory_count,
    'skills_count', v_skills_count,
    'neural_links', v_links_count,
    'growth_pct', v_growth_pct,
    'prediction_accuracy_pct', v_prediction_accuracy_pct,
    'next_shortage_item', v_shortage_item,
    'next_shortage_days', v_shortage_days
  );
end;
$function$;
