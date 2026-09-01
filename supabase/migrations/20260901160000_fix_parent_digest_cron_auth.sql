-- بند 34.3 — الكرون الأصلية (20260823231000) كانت بتحاول
-- current_setting('app.settings.jwt_secret')، GUC مش متظبط على المشروع ده. اتأكد حي:
-- cron.job_run_details بيقول الجوب الوحيد اللي اتنفذ (2026-08-28) فشل بـ
-- "unrecognized configuration parameter" — يعني zad_parent_digests فاضي من يوم ما
-- الجدول اتعمل مش لعدم وجود أسر مؤهلة، لكن لأن الكرون نفسها معطوبة من الأول.
--
-- الحل: نفس نمط X-Checkin-Cron-Secret الموجود فعلاً في المشروع ده (سيكريت مخصص plain
-- literal + verify_jwt=false على الدالة، اتظبطت في نفس الكوميت ده) — مش
-- current_setting ولا app.settings.jwt_secret تاني.
select cron.unschedule('parent-digest-weekly');

select cron.schedule(
  'parent-digest-weekly',
  '0 9 * * 5',
  $$
  select net.http_post(
    url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/parent-digest',
    headers := jsonb_build_object(
      'X-Parent-Digest-Cron-Secret', '3302c068f8ce3057f9122ebc90b19113f1dbb0d7417e49848daee1122b530cfec5',
      'Content-Type', 'application/json'
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 55000
  );
  $$
);
