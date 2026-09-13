-- 20260913180000 — كرون المهام مابقاش بينادي الفانكشن على طابور فاضي.
--
-- `agent-tasks-processor` كل ٥ دقايق كان بيعمل `net.http_post` لـzad-brain من غير شرط.
-- القياس (2026-09-13): آخر ٦ ساعات **64 من 64** نداء رجعوا `"processed":0` — يعني ~٢٨٨
-- نداء في اليوم على طابور فاضي. خطة التشخيص (09-12) كانت متتبّعة الصرف ده للكرون ده بالذات
-- (٣١٢ من ٣١٣ نداء zad-brain في ٢٤ ساعة كانوا على شبكة الكرون).
--
-- الشرط هنا هو **نفس اختيار الفانكشن نفسها** (`processDueAgentTasks`: `status = 'pending'`
-- و`scheduled_for <= now()`)، فمفيش أي تغيير في السلوك: كل مهمة مستحقة بتتنفذ في نفس نبضة
-- الـ٥ دقايق زي قبل كده. الفرق الوحيد إن الطابور الفاضي بقى `SELECT 0` في SQL بدل رحلة HTTP
-- + cold start + قراءة الداتابيز من الفانكشن. المهام العالقة في `running` مكانتش بتتلقط
-- قبل كده برضه (الفانكشن بتختار `pending` بس) — ده مش تغيير.
--
-- نفس حارس 20260913003000: لو السر مش في الـVault، الجدولة بتفضل زي ما هي بدل ما تتكسر.

do $$
declare
  v_has_secret boolean;
begin
  select coalesce(length(public.zad_cron_secret('zad_agent_tasks_cron_secret')), 0) > 0
    into v_has_secret;

  if not v_has_secret then
    raise warning 'zad_agent_tasks_cron_secret missing from vault — agent-tasks-processor left unchanged';
    return;
  end if;

  -- cron.schedule بنفس الاسم = تحديث الجوب الموجود (مش جوب تاني).
  perform cron.schedule(
    'agent-tasks-processor',
    '*/5 * * * *',
    $job$
    select net.http_post(
        url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-brain',
        headers := jsonb_build_object(
          'Content-Type', 'application/json',
          'X-Agent-Tasks-Cron-Secret', public.zad_cron_secret('zad_agent_tasks_cron_secret')
        ),
        body := '{"action":"process_agent_tasks"}'::jsonb,
        timeout_milliseconds := 55000
      ) as request_id
    where exists (
      select 1 from public.agent_tasks
      where status = 'pending' and scheduled_for <= now()
    );
    $job$
  );
end
$$;
