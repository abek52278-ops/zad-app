-- حارس الصمت: العقل «ناجح» وبيطلّع صفر — والووتشدوج الحالي مايشوفش ده.
--
-- `zad_brain_health_check` بيقيس **نسبة الفشل** في `zad_brain_runs` (أدوار الشات)،
-- وبيسكت لو أقل من ٥ تشغيلات في ٢٤ ساعة. العطل اللي كلّفنا الجلسة دي شكله مختلف تماماً:
-- كل حاجة بترجع "نجحت" والناتج صفر. المقيس 2026-09-12:
--   * آخر مهمة استباقية: 2026-09-05 (٧ أيام)
--   * آخر `zad_insights`: 2026-08-30 (١٣ يوم)
--   * `zad_brain_runs` آخر ٢٤ ساعة: **صفر** — يعني الووتشدوج بيرجع "too few runs"
--     كل ساعة وبيعتبر نفسه خلص شغله
-- يعني الآلية اللي المفروض تحرس، كانت هي كمان صامتة.
--
-- الحارس ده بيسأل سؤال تاني خالص: **إمتى آخر مرة العقل قال حاجة لعميل؟** لو عدّى
-- ٣ أيام وفيه حسابات نشطة مفروض تتفحص، دي حالة تستاهل تنبيه — حتى لو ولا تشغيلة
-- واحدة رجعت خطأ. ٣ أيام مش ١٣: العتبة دي كانت هتولّع يوم 2026-09-08.
--
-- بيتنادى من جوه `zad_brain_health_check` (الكرون بتاعه SQL خالص، مفيش سر ولا HTTP في
-- الجدولة) وملفوف في handler عشان ما يقدرش يوقّع الفحص الأصلي.

-- kind عشان نوعين التنبيه ما يخنقوش بعض في قفل «مرة واحدة في اليوم».
alter table public.zad_brain_health_alerts
  add column if not exists kind text not null default 'failure_rate';

-- صفوف الصمت مالهاش أرقام تشغيلات — الأعمدة دي NOT NULL، فبناخد صفر افتراضي بدل ما
-- نحشر فيها أرقام مالهاش معنى في السياق ده.
alter table public.zad_brain_health_alerts alter column total_runs set default 0;
alter table public.zad_brain_health_alerts alter column bad_runs  set default 0;
alter table public.zad_brain_health_alerts alter column bad_pct   set default 0;

create or replace function public.zad_proactive_silence_check()
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $fn$
declare
  v_last_task timestamptz;
  v_last_insight timestamptz;
  v_last_output timestamptz;
  v_days int;
  v_scannable int;
  v_admin record;
  v_body text;
begin
  select max(created_at) into v_last_task
  from public.agent_tasks
  where kind in (
    'spending_ahead', 'med_followup', 'bill_reminder',
    'warranty_reminder', 'home_weekly_digest', 'listener_gap_alert', 'spend_forecast'
  );

  select max(created_at) into v_last_insight from public.zad_insights;

  v_last_output := greatest(coalesce(v_last_task, 'epoch'::timestamptz),
                            coalesce(v_last_insight, 'epoch'::timestamptz));

  -- مفيش حسابات نشطة = الصمت طبيعي مش عطل.
  select count(*) into v_scannable
  from public.zad_users u;
  if coalesce(v_scannable, 0) = 0 then
    return jsonb_build_object('checked', true, 'alerted', false, 'reason', 'no scannable users');
  end if;

  v_days := extract(day from now() - v_last_output)::int;
  if v_days < 3 then
    return jsonb_build_object('checked', true, 'alerted', false, 'days_silent', v_days);
  end if;

  if exists (
    select 1 from public.zad_brain_health_alerts
    where alert_date = current_date and kind = 'proactive_silence'
  ) then
    return jsonb_build_object('checked', true, 'alerted', false, 'reason', 'already alerted today');
  end if;

  v_body := 'العقل ساكت من ' || v_days || ' يوم: مفيش أي تنبيه استباقي ولا رؤية اتكتبت، '
            || 'رغم إن الماسح شغال و' || v_scannable || ' حساب مفروض يتفحصوا. '
            || 'ده مش فشل في التشغيل — ده إنتاج صفر، وبيحتاج فحص يدوي.';

  insert into public.zad_brain_health_alerts (alert_date, kind, top_error)
  values (current_date, 'proactive_silence', left(v_body, 200));

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

  return jsonb_build_object('checked', true, 'alerted', true, 'days_silent', v_days,
                            'scannable_users', v_scannable);
end;
$fn$;

comment on function public.zad_proactive_silence_check() is
  'بيتنبه لما العقل يفضل ٣ أيام من غير أي مهمة استباقية ولا رؤية، حتى لو مفيش أخطاء. '
  'مكمّل لـ zad_brain_health_check اللي بيقيس نسبة الفشل بس — عطل «ناجح وبيطلّع صفر» '
  'كان بره تغطيته بالكامل.';

-- إعادة تعريف الووتشدوج: نفس المنطق بالحرف + قفل التكرار بقى مقيّد بالنوع + نداء حارس
-- الصمت في الآخر. الـ handler حواليه مقصود: حارس جديد ما ينفعش يوقّع حارس شغال.
create or replace function public.zad_brain_health_check()
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $fn$
declare
    v_total int;
    v_bad int;
    v_pct int;
    v_top_error text;
    v_admin record;
    v_body text;
    v_silence jsonb;
    v_result jsonb;
begin
    select count(*),
           count(*) filter (where status in ('failed', 'queued'))
      into v_total, v_bad
      from zad_brain_runs
     where started_at >= now() - interval '24 hours';

    if coalesce(v_total, 0) < 5 then
        v_result := jsonb_build_object('checked', true, 'alerted', false,
                                       'reason', 'too few runs', 'total', coalesce(v_total, 0));
    elsif round(v_bad::numeric / v_total * 100) < 20 then
        v_result := jsonb_build_object('checked', true, 'alerted', false,
                                       'total', v_total, 'bad', v_bad,
                                       'pct', round(v_bad::numeric / v_total * 100));
    elsif exists (
        select 1 from zad_brain_health_alerts
        where alert_date = current_date and kind = 'failure_rate'
    ) then
        v_result := jsonb_build_object('checked', true, 'alerted', false,
                                       'reason', 'already alerted today');
    else
        v_pct := round(v_bad::numeric / v_total * 100);

        select left(error, 200) into v_top_error
          from zad_brain_runs
         where started_at >= now() - interval '24 hours'
           and status in ('failed', 'queued') and error is not null
         group by left(error, 200)
         order by count(*) desc
         limit 1;

        insert into zad_brain_health_alerts (alert_date, kind, total_runs, bad_runs, bad_pct, top_error)
        values (current_date, 'failure_rate', v_total, v_bad, v_pct, v_top_error);

        v_body := 'العقل: ' || v_bad || ' من ' || v_total || ' تشغيلة فشلت/اتعلّقت آخر ٢٤ ساعة ('
                  || v_pct || '٪).' || coalesce(E'\n\nأكتر خطأ متكرر:\n' || v_top_error, '');

        for v_admin in
            select b.user_id from dashboard_admins a
            join telegram_bindings b on b.user_id = a.user_id and b.bound_at is not null
        loop
            perform net.http_post(
                url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
                headers := jsonb_build_object('Content-Type', 'application/json',
                                              'X-Realtime-Push-Secret', public.zad_cron_secret('zad_realtime_push_secret')),
                body := jsonb_build_object('user_id', v_admin.user_id,
                                           'title', '⚠️ صحة العقل', 'body', v_body),
                timeout_milliseconds := 15000
            );
        end loop;

        v_result := jsonb_build_object('checked', true, 'alerted', true,
                                       'total', v_total, 'bad', v_bad, 'pct', v_pct,
                                       'top_error', v_top_error);
    end if;

    begin
        v_silence := public.zad_proactive_silence_check();
    exception when others then
        raise warning 'zad_proactive_silence_check failed: % (%)', sqlerrm, sqlstate;
        v_silence := jsonb_build_object('checked', false, 'error', sqlerrm);
    end;

    return v_result || jsonb_build_object('silence', v_silence);
end;
$fn$;
