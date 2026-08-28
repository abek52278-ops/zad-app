-- ── حلقة الحلم الليلي (nightly dream) — إيقاظ التأمل الليلي المستقل ─────────────
-- المشكلة: endpoint nightly_dream_reflection موجود في zad-brain (بيحوّل نواقص
-- المخزون لقائمة تسوق تلقائياً + يستنتج معدل الصرف ويحفظه ذاكرة) لكن مفيش أي
-- cron بيصحّيه — الحلم كان نايم.
--
-- الإصلاح: pg_cron يومي 03:17 UTC بينادي الـ endpoint بنفس سيكريت الفحص الاستباقي
-- المختوم في vault (zad_proactive_cron_secret — من 20260811020000) بعد ما zad-brain
-- اتحدّث لياخد السيكريت ده على الـ dream endpoint. نمط مطابق لـ agent-proactive-scan-hourly.

-- idempotent re-run: امسح أي جدولة قديمة لنفس الاسم
select cron.unschedule('nightly-dream-reflection')
where exists (select 1 from cron.job where jobname = 'nightly-dream-reflection');

select cron.schedule(
  'nightly-dream-reflection',
  '17 3 * * *',
  $$
  select net.http_post(
      url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-brain',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'ZAD-PROACTIVE-CRON-SECRET',
          (select decrypted_secret from vault.decrypted_secrets where name = 'zad_proactive_cron_secret')
      ),
      body := '{"action":"nightly_dream_reflection"}'::jsonb,
      timeout_milliseconds := 55000
    ) as request_id;
  $$
);
