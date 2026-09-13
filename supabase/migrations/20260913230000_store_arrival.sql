-- 20260913230000 — وصول لمحل (مرحلة ٢ بند ٣): اسم النوع للرفض، وماتخبّيش سكوت الماسح.
--
-- `store_arrival` نوع مهمة جديد بيتسجّل (status=done) كل ما zad-brain يبعت قايمة النواقص
-- لتليجرام وقت دخول نطاق محل. نوعين تعديل صغيرين:
--
--   ١. اسم مقروء في رد البوت بعد الرفض («قايمة النواقص جنب المحلات» بدل store_arrival خام).
--   ٢. فحص سكوت العقل (`zad_proactive_silence_check`) كان بيعتبر أي `kind <> 'reminder'` إشارة
--      حياة. صف store_arrival مالوش علاقة بشغل الماسح — بيتولد من حركة العميل بس — فزيارة محل
--      واحدة كانت هتصفّر عدّاد السكوت وتخبّي ماسح ميت. نفس الفخ اللي اتقفل النهاردة مع رؤى
--      الشات. `delivery_test` (صفوف اختبار التوصيل) مستبعد لنفس السبب. الشاشة
--      (BrainHealth.kt) اتعدلت بنفس القايمة في نفس الكومِت.

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
    when 'store_arrival'      then 'قايمة النواقص جنب المحلات'
    else p_kind
  end;
$$;

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
  where kind not in ('reminder', 'store_arrival', 'delivery_test');

  -- ولا مهمة استباقية خالص = نظام لسه ماطلّعش حاجة، مش عطل (زي NO_PROACTIVE_YET في الشاشة).
  if v_last_task is null then
    return jsonb_build_object('checked', true, 'alerted', false, 'reason', 'no proactive output yet');
  end if;

  with days as (
    select distinct (created_at at time zone 'utc')::date as d
    from public.agent_tasks
    where kind not in ('reminder', 'store_arrival', 'delivery_test')
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
