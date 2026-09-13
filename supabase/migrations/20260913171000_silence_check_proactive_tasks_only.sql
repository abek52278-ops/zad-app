-- 20260913171000 — فحص سكوت العقل بقى بيقيس المهام الاستباقية بس، بنفس منطق شاشة الصحة.
--
-- `zad_proactive_silence_check()` (20260913000000) كان بيحسب "آخر إنتاج" = الأحدث بين
-- مهام من قايمة أنواع مكتوبة يدويًا **و أي صف في `zad_insights`**. مشكلتين:
--
--   ١. أي رؤية بتصفّر عدّاد السكوت، فنشاط من مسار تاني ممكن يخبّي ماسح استباقي ميت —
--      نفس الفخ اللي النسخة الأولى من شاشة الصحة وقعت فيه (257 من 263 تشغيلة كانت
--      شات، وكانت بتخبّي عقل ساكت). قرار المستخدم 2026-09-13: الإشارة = المهام
--      الاستباقية الحقيقية بس.
--   ٢. قايمة الأنواع المكتوبة يدويًا بتتقادم: أي نوع مهمة استباقية جديد مابيتحسبش لحد ما
--      حد يفتكر يضيفه هنا. الشاشة بتستخدم `kind <> 'reminder'` (reminder = اللي المستخدم
--      طلبه بنفسه، مش استباقي)، ودلوقتي الفحص كمان.
--
-- والعتبة بقت بإيقاع النظام الفعلي بدل ٣ أيام ثابتة — نفس `expectedProactiveSilenceHours`
-- في `app/.../data/BrainHealth.kt`: أكبر قيمة بين ٣٦ ساعة و١.٥ × **وسيط** الفجوة بين أيام
-- UTC اللي فيها مهام استباقية آخر ٦٠ يوم. الوسيط مش المتوسط عشان فجوة العطل نفسها
-- ماتكبّرش العتبة.
--
-- القياس على عطل 09-06..09-12 (آخر مهمة استباقية 09-05 00:07، وسيط الفجوة يوم واحد):
-- المنطق القديم كان هيولّع 09-08 00:07 (٣ أيام كاملة)، والجديد 09-06 12:07 (٣٦ ساعة) —
-- يوم ونص أبدر. (للأمانة: في العطل ده الرؤى كانت واقفة من 08-30، فالقديم ماكانش هيتخبّى؛
-- الفحص نفسه اتعمل بعد العطل. الإخفاء بالرؤى خطر كامن مش حادثة اتقاست.)

create or replace function public.zad_proactive_silence_check()
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_last_task timestamptz;
  v_median_gap numeric;
  v_allowed_hours int;
  v_silent_hours int;
  v_scannable int;
  v_admin record;
  v_body text;
begin
  -- حسابات حقيقية بس — نفس فلتر الماسح (20260913161000).
  select count(*) into v_scannable
  from public.zad_users u
  join auth.users a on a.id = u.id;
  if coalesce(v_scannable, 0) = 0 then
    return jsonb_build_object('checked', true, 'alerted', false, 'reason', 'no scannable users');
  end if;

  select max(created_at) into v_last_task
  from public.agent_tasks
  where kind <> 'reminder';

  -- ولا مهمة استباقية خالص = نظام لسه ماطلّعش حاجة، مش عطل (زي NO_PROACTIVE_YET في الشاشة).
  if v_last_task is null then
    return jsonb_build_object('checked', true, 'alerted', false, 'reason', 'no proactive output yet');
  end if;

  with days as (
    select distinct (created_at at time zone 'utc')::date as d
    from public.agent_tasks
    where kind <> 'reminder'
      and created_at >= now() - interval '60 days'
  ), gaps as (
    select d - lag(d) over (order by d) as gap from days
  )
  select percentile_cont(0.5) within group (order by gap)
    into v_median_gap
  from gaps
  where gap is not null;

  -- أقل من يومين مميّزين (الوسيط null) = الإيقاع مش معروف → الحد الأدنى الآمن.
  v_allowed_hours := greatest(36, coalesce(v_median_gap, 0) * 24 * 1.5)::int;
  v_silent_hours := floor(extract(epoch from now() - v_last_task) / 3600)::int;

  if v_silent_hours <= v_allowed_hours then
    return jsonb_build_object('checked', true, 'alerted', false,
                              'silent_hours', v_silent_hours, 'allowed_hours', v_allowed_hours);
  end if;

  if exists (
    select 1 from public.zad_brain_health_alerts
    where alert_date = current_date and kind = 'proactive_silence'
  ) then
    return jsonb_build_object('checked', true, 'alerted', false, 'reason', 'already alerted today',
                              'silent_hours', v_silent_hours, 'allowed_hours', v_allowed_hours);
  end if;

  v_body := 'العقل الاستباقي ساكت من ' || v_silent_hours || ' ساعة (الطبيعي لحد '
            || v_allowed_hours || ' ساعة): مفيش ولا مهمة استباقية اتكتبت، رغم إن '
            || v_scannable || ' حساب مفروض يتفحصوا. ده إنتاج صفر مش فشل تشغيل — '
            || 'شوف zad_brain_health_alerts (proactive_scan_failure) ولوج الماسح.';

  insert into public.zad_brain_health_alerts (alert_date, kind, total_runs, top_error)
  values (current_date, 'proactive_silence', v_scannable, left(v_body, 200));

  for v_admin in
    select b.user_id from public.dashboard_admins a
    join public.telegram_bindings b on b.user_id = a.user_id and b.bound_at is not null
  loop
    perform net.http_post(
      url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Realtime-Push-Secret', public.zad_cron_secret('zad_realtime_push_secret')
      ),
      body := jsonb_build_object('user_id', v_admin.user_id,
                                 'title', '🔇 العقل ساكت', 'body', v_body),
      timeout_milliseconds := 15000
    );
  end loop;

  return jsonb_build_object('checked', true, 'alerted', true,
                            'silent_hours', v_silent_hours, 'allowed_hours', v_allowed_hours,
                            'scannable_users', v_scannable);
end;
$function$;

comment on function public.zad_proactive_silence_check() is
  'سكوت العقل الاستباقي على مستوى النظام: agent_tasks بـkind <> reminder بس (مش رؤى ولا شات). '
  'العتبة = max(36h, 1.5 × وسيط الفجوة بين أيام النشاط آخر 60 يوم) — نفس BrainHealth.kt.';
