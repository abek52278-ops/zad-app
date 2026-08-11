-- ═══════════════════════════════════════════════════════════════════════════
-- تنبيه استباقي بمعدل الصرف + متابعة جرعات الدوا — المرحلة ٢ من "proactive
-- routines" (docs/agent/QUICK_REFERENCE.md: "مدة التنفيذ: يوم واحد يشمل edge
-- function + pg_cron job"). الاتنين بيسلّموا عن طريق زرع صف في public.agent_tasks
-- بـ scheduled_for=NOW() — ده بيستغل نفس معالج المهام الموجود (zad-brain
-- action=process_agent_tasks، كل ٥ دقايق) ونفس خط التوصيل
-- (trigger_notify_telegram_on_agent_task_notification على app_notifications →
-- ?job=realtime_push)، فمفيش أي infra جديدة لازم تتنصب عشان التنبيه يوصل.
--
-- ليه مش INSERT مباشر في app_notifications:
--   نفس سبب agent_task_telegram_delivery نفسه — app_notifications هو outbox
--   المشترك، وأي INSERT مباشر فيه بيشتغل التريجر اللي تحت وبيوصّل على طول.
--   زرع صف agent_tasks بدل كده بيفصل "الحدث استحق" (هنا، مرة واحدة بالـ UNIQUE
--   dedup اللي تحت) عن "تنسيق وتوليد الكلام" (المعالج والموديل)، وبيدي العميل
--   مكان يشوف "زاد نوى ينبهني" حتى لما الإشعار الفعلي يتأجل شوية. لو عملنا INSERT
--   في app_notifications من هنا هنخسر الطبقة دي ونرجع لنفس نمط الـ triggers
--   المتشابكة اللي single-budget-authority شالته.
-- ═══════════════════════════════════════════════════════════════════════════

-- عمود نوع المهمة + قفل عدم التكرار البشري/اليومي. task_description لسه
-- بيوصّل للموديل نص حر؛ kind بيفرّق "تنبيه استباقي تلقائي" عن reminders العميل
-- المجدولة، والـ UNIQUE partial index بيمنع تكرار نفس التنبيه لنفس اليوم حتى لو
-- الكرون ركب مرتين (net.http_post مش بينضمن تسليمه مرة واحدة).
alter table public.agent_tasks
  add column if not exists kind text not null default 'reminder';

comment on column public.agent_tasks.kind is
  'نوع المهمة: reminder = طلب العميل بنفسه، spending_ahead/med_followup = تنبيه استباقي تلقائي بيتزرع من الروتينات المجدولة في الملف ده. بيمنع تكرار نفس التنبيه في نفس اليوم عن طريق idx_agent_tasks_proactive_dedup.';

create unique index if not exists idx_agent_tasks_proactive_dedup
  -- `date_trunc` on timestamptz is STABLE because it depends on the session
  -- timezone. Pin the dedupe bucket to UTC so PostgreSQL can index it.
  on public.agent_tasks (user_id, kind, ((created_at at time zone 'UTC')::date))
  where kind in ('spending_ahead', 'med_followup');

-- UNNECESSARY-INDEX → CONSTRAINT: partial unique index لوحده مش بيدعم
-- ON CONFLICT ON CONSTRAINT؛ لازم يتحوّل لقيود حقيقي عشان
-- ON CONFLICT ON CONSTRAINT idx_agent_tasks_proactive_dedup_uq
-- تحت يشتغل. UNIQUE USING INDEX بيرفع الـ index ده ويسقّطه كـ constraint بنفس
-- الاسم، من غير ما يبني index تاني.
do $$
begin
  alter table public.agent_tasks
    add constraint idx_agent_tasks_proactive_dedup_uq
    unique using index idx_agent_tasks_proactive_dedup;
exception
  when duplicate_object then null;
  when others then null;
end;
$$;

-- التريجرات الموجودة بتغطي السقف اللحظي (notify_parents_on_child_spend عند ٧٥/٩٠/١٠٠٪)
-- والأهل لما طفل يعدّي. الملف ده بيغطي "الوتيرة اللي قبل النفاد": لو الصرف التراكمي
-- لحد دلوقت أكبر من time-boxed pace (يوم/أسبوع)، نبعت تلميح قبل ما النسبة توصل
-- للعتبات اللحظية دي. ده في نفس روح المواصفة اللي ذكرت "التنبيه الاستباقي قبل
-- الاحتياج بدل رد الفعل بعده" في ZAD_MASTER.md.
-- ═══════════════════════════════════════════════════════════════════════════

create or replace function public._agent_spending_ahead_for_user(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
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

  -- نشتغلش على سقف مش متأكد (limit_confirmed=false في v_state) ولا سقف صفر/سالب:
  -- دي نفس نقطة الحذر اللي 20260808160000 شدّدت عليها لـ notify_telegram_on_transaction.
  if v_days <= 0 or v_elapsed < 7 then
    return; -- لسه بداية الدورة، الوتيرة مش واضحة كفاية للحكم عليها
  end if;
  if coalesce((v_state ->> 'limit_confirmed')::boolean, false) is not true then
    return;
  end if;
  if v_limit <= 0 then
    return;
  end if;

  -- pace أعلى من المتوقع النهاردة = هنعدي السقف لو الوضع استمر. ما نستخدمش
  -- monthly_limit * 0.85 كـ عتبة ثابتة عشان نتفادي إنها تتساوى مع العتبات اللحظية
  -- (75/90/100) اللي الـ triggers الموجودة بتنفذها — دي تنبيه قبل النفاد مش عند
  -- النسبة.
  v_pace      := v_limit * (v_elapsed::numeric / v_days);
  v_threshold := v_pace * 1.08; -- هامش ٨٪ فوق الوتيرة الزمنية، مش نسبة من السقف نفسه

  if v_spend <= v_threshold then
    return; -- لسه شغالين عادي في الوتيرة
  end if;

  -- الزرع الفعلي — الـ UNIQUE partial index فوق بيمنع تكرار نفس التنبيه لنفس اليوم
  -- even لو الكرون ركب مرتين أو أكتر في نفس اليوم. والمعالج بيلقط المهمة ويوصّلها
  -- عن طريق الموديل بنفس نبرتها اللي بيستخدمها أي مهمة عميل مجدولة.
  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'spending_ahead',
    format($t$[تنبيه استباقي] وضع الصرف أسرع من الميزانية الشهرية — صرفت %s من %s بعد %s يوم
الوتيرة دي لو استمرت هتتجاوز السقف قبل نهاية الدورة. اقترح تقليل شوية الأيام الجاية$t$,
      round(v_spend, 2), round(v_limit, 2), v_elapsed),
    now()
  )
  on conflict on constraint idx_agent_tasks_proactive_dedup_uq do nothing;
exception
  when unique_violation then
    -- سبق ووجدنا نفس التنبيه لنفس اليوم بالفعل، مش لازم نكرره
    return;
  when others then
    -- أي خطأ في الحساب/الإدراج ما يمنعش باقي المستخدمين في نفس اللفة
    return;
end;
$$;

create or replace function public._agent_med_followup_for_user(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
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
    on conflict on constraint idx_agent_tasks_proactive_dedup_uq do nothing;
  end if;
exception
  when unique_violation then
    return;
  when others then
    return;
end;
$$;

-- نقطة دخول واحدة للكرون: بتمشي على كل مستخدم مؤكد سقفه (لـ spending_ahead) أو
-- عنده دوا متجدد (لـ med_followup) — الاتنين استعلاماتهم رخيصة وبتتفحص لكل مستخدم
-- لوحده، وكل FUNCTION بيتعامل مع أخطاء نفس الـ user لوحده عشان ما يقوّضش الباقي.
create or replace function public.agent_proactive_scan()
returns void language plpgsql security definer set search_path = public as $$
declare
  v_user uuid;
begin
  for v_user in
    select id from public.zad_users
    where limit_confirmed_at is not null
      and coalesce(monthly_limit, 0) > 0
  loop
    perform public._agent_spending_ahead_for_user(v_user);
  end loop;

  for v_user in
    select distinct user_id from public.zad_pharmacy_items
    where coalesce(is_recurring, false) is true
  loop
    perform public._agent_med_followup_for_user(v_user);
  end loop;
exception
  when others then
    return;
end;
$$;

-- UNIQUE constraint حقيقي هو البديل الوحيد اللي بيمنع تكرار نفس التنبيه في نفس
-- اليوم بشكل مضمون (الـ partial index مش بيدي constraint يتسنّد عليه في
-- ON CONFLICT). بنضيفه هنا كـ concurrent-friendly alter: لو موجود من قبل،
--exception مش هيقطع. وجوده هو اللي بيخلي ON CONFLICT فوق آمن فعلاً.
do $$
begin
  alter table public.agent_tasks
    add constraint idx_agent_tasks_proactive_dedup_uq
    unique using index idx_agent_tasks_proactive_dedup;
exception
  when duplicate_object then
    -- الـ constraint اتضاف من تنفيذ سابق للملف ده، تخطّاه
    null;
  when others then
    -- أي حاجة تانية (بنية جدول مختلفة مثلاً) ما توقفش المايجريشن هنا
    null;
end;
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- pg_cron: كل ساعة يعمل scan. نفس نمط الجوبات الموجودة (agent-tasks-processor،
-- telegram-checkin-daily) — هيدر بسيكريت مخصص للوظيفة دي بالذات، مش الـ service
-- role. نفس الجدول الزمني مش هيبقى مفرط لأن الكلفة الليلية MTD هي اللي بتتحاسب،
-- والفحص لكل مستخدم بيتعمل في schema واحد صغير.
select cron.schedule(
  'agent-proactive-scan-hourly',
  '7 * * * *',
  $$
  select net.http_post(
      url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-brain',
      headers := '{"Content-Type":"application/json","ZAD-PROACTIVE-CRON-SECRET":"d4f3a9b8c7e6d5f4102a93b7c8e9d0a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2"}'::jsonb,
      body := '{"action":"run_proactive_scan"}'::jsonb,
      timeout_milliseconds := 55000
    ) as request_id;
  $$
);
