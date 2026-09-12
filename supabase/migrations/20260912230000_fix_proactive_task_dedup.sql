-- تنبيهان استباقيان بيتحسبوا صح وبيترموا في الهوا من ٢٠٢٦-٠٨-١٠.
--
-- `_agent_spending_ahead_for_user` و`_agent_med_followup_for_user` بيقفلوا الإدخال بـ
--   on conflict on constraint idx_agent_tasks_proactive_dedup_uq
-- والاسم ده **مش موجود في `pg_constraint` خالص**. اللي موجود فهرس جزئي فريد اسمه
-- `idx_agent_tasks_proactive_dedup` (من غير `_uq`)، و`ON CONFLICT ON CONSTRAINT`
-- بيطلب قيد حقيقي — الفهرس ماينفعش. يعني كل نداء بيرمي `undefined_object` (42704)
-- عند التنفيذ، و`exception when others then return` في آخر الدالتين بيبلعه.
--
-- النتيجة المقيسة (2026-09-12): الماسح الاستباقي اشتغل ~٣١٢ مرة في ٢٤ ساعة،
-- `agent_tasks` فيها **صفر** صف معلّق، وآخر `zad_insights` من 2026-08-30. الحساب كان
-- بيتعمل تمام والسطر الأخير بس هو اللي بيقع — ومحدش عرف، لأن الفشل كان صامت.
-- الأربع دوال التانية (`bill_reminder`, `warranty_reminder`, `home_weekly_digest`,
-- `listener_gap_alert`) بتستخدم الشكل الصح أصلاً، وde هو اللي بيتنسخ هنا:
--   on conflict (user_id, kind, ((created_at at time zone 'UTC')::date)) where kind in (...)
--
-- وكمان: `when others then return` هو اللي خلّى باج عمره شهر يعدّي من غير أثر. مابيتشالش
-- (فشل مستخدم واحد مالازمش يوقّع اللفة كلها) لكن بقى بيسجّل `raise warning` بالمستخدم
-- والـsqlstate الأول. نفس القاعدة اللي الجلسة اللي فاتت دفعت تمنها ست محاولات: أي آلية
-- بتفشل مقفولة لازم تقول ليه.

create or replace function public._agent_spending_ahead_for_user(p_user uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $fn$
declare
  v_state jsonb;
  v_spend numeric;
  v_limit numeric;
  v_elapsed int;
  v_days int;
  v_pace numeric;      -- pace مقبول اليوم: limit * (elapsed/days)
  v_threshold numeric;
begin
  if coalesce(nullif(trim(p_user::text), ''), '') is null then
    return;
  end if;

  -- نفس مصدر Arithmetic الرسمي اللي بيقراه app/zad-brain/Telegram — ما فيش حساب
  -- مكرّر هنا، زي ما realtime_push مبيحسبش حاجة. اللي بيحسب هو zad_budget_state.
  select public.zad_budget_state(p_user) into v_state;
  if v_state is null then
    return;
  end if;

  v_limit   := coalesce((v_state ->> 'monthly_limit')::numeric, 0);
  v_spend   := coalesce((v_state ->> 'spent')::numeric, 0);
  v_elapsed := coalesce((v_state ->> 'days_elapsed')::int, 0);
  v_days    := coalesce((v_state ->> 'days_left')::int, 0) + v_elapsed;

  if v_days <= 0 or v_elapsed < 7 then
    return; -- لسه بداية الدورة، الوتيرة مش واضحة كفاية للحكم عليها
  end if;
  if coalesce((v_state ->> 'limit_confirmed')::boolean, false) is not true then
    return;
  end if;
  if v_limit <= 0 then
    return;
  end if;

  -- pace أعلى من المتوقع النهاردة = هنعدي السقف لو الوضع استمر. هامش ٨٪ فوق الوتيرة
  -- الزمنية، مش نسبة من السقف نفسه — عشان مايتساويش مع عتبات الـtriggers (75/90/100).
  v_pace      := v_limit * (v_elapsed::numeric / v_days);
  v_threshold := v_pace * 1.08;

  if v_spend <= v_threshold then
    return; -- لسه شغالين عادي في الوتيرة
  end if;

  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'spending_ahead',
    format($t$[تنبيه استباقي] وضع الصرف أسرع من الميزانية الشهرية — صرفت %s من %s بعد %s يوم
الوتيرة دي لو استمرت هتتجاوز السقف قبل نهاية الدورة. اقترح تقليل شوية الأيام الجاية$t$,
      round(v_spend, 2), round(v_limit, 2), v_elapsed),
    now()
  )
  on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
    where kind in ('spending_ahead','med_followup','bill_reminder')
  do nothing;
exception
  when unique_violation then
    -- سبق ووجدنا نفس التنبيه لنفس اليوم بالفعل، مش لازم نكرره
    return;
  when others then
    raise warning '_agent_spending_ahead_for_user: user % failed: % (%)', p_user, sqlerrm, sqlstate;
    return;
end;
$fn$;

create or replace function public._agent_med_followup_for_user(p_user uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $fn$
begin
  -- نفس فكرة spending_ahead بالظبط، بس للدوا. بنقرا الجرعات المتجددة
  -- (is_recurring) اللي dose_log بيقول إن آخر جرعة اتاخدت مبارح أو أقدم — يعني
  -- في يوم ضاي من غير ما متسجل فيه أي خدّ (taken_at NOT NULL). لو لقينا، نزرع
  -- task kind=med_followup بنفس قفل عدم التكرار اليومي، ونفس خط المعالج يوصّلها.
  if exists (
    select 1
    from public.zad_pharmacy_items p
    left join lateral (
      select max(taken_at) as last_taken
      from public.zad_dose_log d
      where d.pharmacy_item_id = p.id
        and d.taken_at is not null
    ) d on true
    where p.user_id = p_user
      and coalesce(p.is_recurring, false) is true
      and (d.last_taken is null or d.last_taken < now() - interval '1 day')
      and not exists (
        select 1
        from public.agent_tasks t
        where t.user_id = p_user
          and t.kind = 'med_followup'
          and date_trunc('day', t.created_at) = date_trunc('day', now() at time zone 'utc')
      )
  ) then
    insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
    values (
      p_user,
      'med_followup',
      $t$[متابعة دوا] عندك دوا متجدد شغال مع جدولة، ومبيظهرش إنك سجلت أي خدّ
في آخر يوم. فكّر في مراجعة الجدولة لو الجرعة اتأجلت$t$,
      now()
    )
    on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
      where kind in ('spending_ahead','med_followup','bill_reminder')
    do nothing;
  end if;
exception
  when unique_violation then
    return;
  when others then
    raise warning '_agent_med_followup_for_user: user % failed: % (%)', p_user, sqlerrm, sqlstate;
    return;
end;
$fn$;
