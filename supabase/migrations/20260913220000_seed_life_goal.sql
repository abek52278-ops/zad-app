-- 20260913220000 — زرع أول هدف حياة من الواجهة، من غير ما يعتمد على الموديل.
--
-- الحالة قبل (قياس 2026-09-13):
--   * `agent_goals` صفر صفوف، و`set_life_goal` **عمره مااتنادى** (صفر صف في agent_actions).
--     زرار «هدف جديد» في البروفايل بيبعت طلب للموديل يسجّل الهدف ويفكّكه — ده مسار مابيتضمنش.
--   * التقدم (`trg_agent_goal_progress`) بيزوّد `current_value` بواحد مع كل مهمة مربوطة
--     بالهدف بتخلص. هدف من غير مهام مربوطة = هدف ميت، عمره ما يتحرك.
--   * الواجهة تقدر تكتب في agent_goals (RLS لصاحبه)، بس agent_tasks **قراءة بس** للعميل —
--     فالتطبيق لوحده مايقدرش يربط الهدف بمهمة متابعة.
--   * شاشة الـOnboarding قبل تسجيل الدخول (مفيش user_id)، فالزرع بيحصل في خطوة تفعيل
--     الرئيسية بعد التسجيل.
--
-- الدالة دي بتعمل الاتنين في معاملة واحدة: الهدف + مهمة متابعة أسبوعية مربوطة بيه (نوع
-- `goal_review`). المتابعة مبادرة، فبتوصل تليجرام بأزرار الرفض (20260913190000) وعنوانها
-- «🎯 زاد بيتابع هدفك». الموديل لسه شايف الهدف في سياقه وبيقدر يضيف مهام تانية من الشات.

create or replace function public.zad_seed_life_goal(
  p_title text,
  p_metric text default null,
  p_target_value numeric default null,
  p_deadline date default null
)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_user uuid := auth.uid();
  v_title text := btrim(coalesce(p_title, ''));
  v_metric text := nullif(btrim(coalesce(p_metric, '')), '');
  v_active int;
  v_goal_id uuid;
  v_existing_status text;
  v_task_id uuid;
begin
  if v_user is null then
    return jsonb_build_object('ok', false, 'error', 'not_authenticated');
  end if;
  -- نفس قواعد validateSetLifeGoal في zad-brain/validators.ts، عشان المسارين يرفضوا نفس الحاجات.
  if char_length(v_title) < 4 then
    return jsonb_build_object('ok', false, 'error', 'title_too_short');
  end if;
  if char_length(v_title) > 200 then
    return jsonb_build_object('ok', false, 'error', 'title_too_long');
  end if;
  if v_metric is not null and char_length(v_metric) > 200 then
    return jsonb_build_object('ok', false, 'error', 'metric_too_long');
  end if;
  if p_target_value is not null and (p_target_value <= 0 or p_target_value > 100000000) then
    return jsonb_build_object('ok', false, 'error', 'bad_target');
  end if;
  if p_deadline is not null and p_deadline < current_date then
    return jsonb_build_object('ok', false, 'error', 'deadline_in_past');
  end if;

  select status into v_existing_status
  from public.agent_goals where user_id = v_user and title = v_title;

  -- سقف ٥ أهداف نشطة — بس لو ده هدف جديد (إعادة تفعيل/تحديث هدف موجود مابيتحسبش).
  if v_existing_status is distinct from 'active' then
    select count(*) into v_active from public.agent_goals where user_id = v_user and status = 'active';
    if v_active >= 5 then
      return jsonb_build_object('ok', false, 'error', 'too_many_active_goals');
    end if;
  end if;

  insert into public.agent_goals (user_id, title, metric, target_value, deadline_date, status, updated_at)
  values (v_user, v_title, v_metric, p_target_value, p_deadline, 'active', now())
  on conflict (user_id, title) do update
    set metric = excluded.metric,
        target_value = excluded.target_value,
        deadline_date = excluded.deadline_date,
        status = 'active',
        updated_at = now()
  returning id into v_goal_id;

  -- مهمة متابعة واحدة بس لكل هدف: لو فيه متابعة مستنية/شغالة، مابنكررهاش (ضغطتين = مهمة واحدة).
  select id into v_task_id
  from public.agent_tasks
  where user_id = v_user and goal_id = v_goal_id and kind = 'goal_review'
    and status in ('pending', 'running')
  limit 1;

  if v_task_id is null then
    insert into public.agent_tasks (user_id, kind, goal_id, recurrence, scheduled_for, task_description)
    values (
      v_user, 'goal_review', v_goal_id, 'weekly', now() + interval '7 days',
      format(
        'متابعة أسبوعية لهدف العميل «%s»%s. بص على بياناته الفعلية آخر أسبوع (المصاريف، الميزانية، '
        'الديون، المخزون — اللي يخص الهدف) واكتب له في جملتين: هو ماشي إزاي ناحية الهدف، وأهم خطوة '
        'واحدة يعملها الأسبوع الجاي. متسجّلش ولا تعدّل أي بيانات في المتابعة دي.',
        v_title,
        coalesce(' (المقياس: ' || v_metric || ')', '')
      )
    )
    returning id into v_task_id;
  end if;

  return jsonb_build_object('ok', true, 'goal_id', v_goal_id, 'review_task_id', v_task_id,
                            'reactivated', v_existing_status is not null and v_existing_status <> 'active');
end;
$$;

revoke execute on function public.zad_seed_life_goal(text, text, numeric, date) from public, anon;
grant execute on function public.zad_seed_life_goal(text, text, numeric, date) to authenticated, service_role;

comment on function public.zad_seed_life_goal(text, text, numeric, date) is
  'زرع هدف حياة للمستخدم الحالي (auth.uid) + مهمة متابعة أسبوعية مربوطة (goal_review). حتمية، من غير موديل.';

-- اسم مقروء لنوع المتابعة في رد البوت بعد الرفض (بدل «goal_review» خام).
create or replace function public._agent_proactive_kind_label(p_kind text)
returns text
language sql
immutable
set search_path to 'public'
as $$
  select case p_kind
    when 'home_weekly_digest' then 'ملخص البيت'
    when 'spend_forecast'     then 'توقّع مصروف الأسبوع'
    when 'spending_ahead'     then 'الصرف أسرع من الميزانية'
    when 'med_followup'       then 'متابعة الدوا'
    when 'bill_reminder'      then 'تذكير الفواتير'
    when 'warranty_reminder'  then 'تذكير الضمان'
    when 'listener_gap_alert' then 'وقوف إشعارات البنك'
    when 'goal_review'        then 'متابعة الهدف'
    else p_kind
  end;
$$;
