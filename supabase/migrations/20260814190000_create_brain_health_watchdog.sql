-- =====================================================
-- حارس صحة العقل — أخطر فجوة تشغيلية في المشروع كانت إن محدّش بيبص.
--
-- ٢٢ من ٤١ تشغيلة فشلت لأسابيع ومحدّش عرف. `zad_brain_runs.status` كان بيتكتب فيه
-- 'failed' بأمانة، ورسالة الخطأ الكاملة كانت متخزّنة — **ومفيش حاجة بتقراهم**. الباج
-- (thoughtSignature) اتكشفت 2026-08-14 لأن حد سأل الداتابيز يدوي، مش لأن حاجة بلّغت.
--
-- الحارس بيشتغل كل ساعة وبيقارن نسبة الفشل آخر ٢٤ ساعة بعتبة ٢٠٪:
--   • أقل من ٥ تشغيلات → ساكت. نسبة من عيّنة صغيرة ضوضاء مش إشارة.
--   • مفتاح الجدول هو التاريخ، فمهما اشتغل بالساعة مايقدرش ينبّه أكتر من مرة في اليوم.
--   • بيبعت **أكتر رسالة خطأ متكررة** مع التنبيه — النص ده بالظبط هو اللي عرّف الباج،
--     فتنبيه من غيره بيقول "فيه مشكلة" بدل ما يقول "المشكلة دي".
--
-- التوصيل بيعيد استخدام مسار realtime_push الموجود لتيليجرام (نفس اللي
-- 20260813213858 عمله للرؤى)، وبيروح للمطوّرين في `dashboard_admins` بس — ده تنبيه
-- تشغيلي، مش رؤية للعميل، فمالوش مكان في `zad_insights`.
--
-- اتفحص على نافذة ١٠-١١ أغسطس الحقيقية: ٢٨ تشغيلة، ١٨ فاشلة، ٦٤٪ — كان هينبّه فعلاً،
-- ومعاه نص خطأ الـthoughtSignature.
-- =====================================================

CREATE TABLE IF NOT EXISTS zad_brain_health_alerts (
    alert_date DATE PRIMARY KEY,
    total_runs INTEGER NOT NULL,
    bad_runs INTEGER NOT NULL,
    bad_pct INTEGER NOT NULL,
    top_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE zad_brain_health_alerts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "brain_health_select_admin" ON zad_brain_health_alerts;
CREATE POLICY "brain_health_select_admin"
    ON zad_brain_health_alerts FOR SELECT
    USING (is_dashboard_admin());

COMMENT ON TABLE zad_brain_health_alerts IS
    'One row per day the brain failure rate crossed the alert threshold. Primary key on the date is the dedupe: the watchdog runs hourly but can only alert once a day.';

CREATE OR REPLACE FUNCTION zad_brain_health_check()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_total INT;
    v_bad INT;
    v_pct INT;
    v_top_error TEXT;
    v_admin RECORD;
    v_body TEXT;
BEGIN
    SELECT count(*),
           count(*) FILTER (WHERE status IN ('failed', 'queued'))
      INTO v_total, v_bad
      FROM zad_brain_runs
     WHERE started_at >= now() - interval '24 hours';

    IF coalesce(v_total, 0) < 5 THEN
        RETURN jsonb_build_object('checked', true, 'alerted', false,
                                  'reason', 'too few runs', 'total', coalesce(v_total, 0));
    END IF;

    v_pct := round(v_bad::numeric / v_total * 100);

    IF v_pct < 20 THEN
        RETURN jsonb_build_object('checked', true, 'alerted', false,
                                  'total', v_total, 'bad', v_bad, 'pct', v_pct);
    END IF;

    IF EXISTS (SELECT 1 FROM zad_brain_health_alerts WHERE alert_date = current_date) THEN
        RETURN jsonb_build_object('checked', true, 'alerted', false, 'reason', 'already alerted today');
    END IF;

    SELECT left(error, 200) INTO v_top_error
      FROM zad_brain_runs
     WHERE started_at >= now() - interval '24 hours'
       AND status IN ('failed', 'queued') AND error IS NOT NULL
     GROUP BY left(error, 200)
     ORDER BY count(*) DESC
     LIMIT 1;

    INSERT INTO zad_brain_health_alerts (alert_date, total_runs, bad_runs, bad_pct, top_error)
    VALUES (current_date, v_total, v_bad, v_pct, v_top_error);

    v_body := 'العقل: ' || v_bad || ' من ' || v_total || ' تشغيلة فشلت/اتعلّقت آخر ٢٤ ساعة ('
              || v_pct || '٪).' || coalesce(E'\n\nأكتر خطأ متكرر:\n' || v_top_error, '');

    FOR v_admin IN
        SELECT b.user_id FROM dashboard_admins a
        JOIN telegram_bindings b ON b.user_id = a.user_id AND b.bound_at IS NOT NULL
    LOOP
        PERFORM net.http_post(
            url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
            headers:='{"Content-Type":"application/json","X-Realtime-Push-Secret":"7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094"}'::jsonb,
            body:=jsonb_build_object('user_id', v_admin.user_id,
                                     'title', '⚠️ صحة العقل', 'body', v_body),
            timeout_milliseconds:=15000
        );
    END LOOP;

    RETURN jsonb_build_object('checked', true, 'alerted', true,
                              'total', v_total, 'bad', v_bad, 'pct', v_pct,
                              'top_error', v_top_error);
END $$;

REVOKE ALL ON FUNCTION zad_brain_health_check() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION zad_brain_health_check() TO service_role;

SELECT cron.schedule(
    'brain-health-watchdog',
    '23 * * * *',
    $cron$SELECT public.zad_brain_health_check()$cron$
);
