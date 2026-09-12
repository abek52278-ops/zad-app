-- آخر كرون لسه شايل سرّه نص صريح — بيتنقل للڤولت زي الستة التانيين.
--
-- المسح (2026-09-12) على `cron.job`: سبع وظايف بتبعت سرّ، **ستة منهم بيقروا من Vault**
-- (`zad_cron_secret(...)` أو `vault.decrypted_secrets` مباشرة) وواحدة بس لسه شايلة
-- القيمة مكتوبة في `command`: `agent-tasks-processor` بهيدر `X-Agent-Tasks-Cron-Secret`
-- (٦٤ حرف hex). ميجريشن 20260905150000 نقلت الباقيين وفاتتها دي.
--
-- القيمة نفسها **مش في الملف ده ولا في git**: اتحطّت في Vault باسم
-- `zad_agent_tasks_cron_secret` عن طريق `execute_sql` مباشرة على الداتابيز — نفس
-- الطريقة اللي 20260905150000 وصفتها بالحرف، والقيمة اتقرت من `cron.job.command`
-- نفسها جوّه بوستجرس من غير ما تعدّي على أي جلسة. الملف ده بيشاور بالاسم بس.
--
-- ⚠️ **ده تقوية مش تدوير.** نفس القيمة لسه مكتوبة نص صريح في
-- `20260809200000_agent_tasks.sql` وفي تاريخ git، يعني محروقة. اللي بيتقفل هنا هو نسخة
-- `cron.job` بس. التدوير الحقيقي محتاج خطوة يدوية (سرّ مشروع جديد لـ zad-brain + تحديث
-- نفس القيمة في Vault) — مفيش أداة في بيئة التطوير دي بتضبط أسرار المشروع.
--
-- الحارس تحت مش تجميل: لو السر مش موجود في الڤولت، إعادة الجدولة هتركّب هيدر فاضي
-- والوظيفة هتاخد 401 كل ٥ دقايق بصمت. في الحالة دي بنسيب الوظيفة زي ما هي ونطلّع
-- warning بدل ما نكسرها.
do $$
declare
  v_has_secret boolean;
begin
  select coalesce(length(public.zad_cron_secret('zad_agent_tasks_cron_secret')), 0) > 0
    into v_has_secret;

  if not v_has_secret then
    raise warning 'zad_agent_tasks_cron_secret missing from vault — agent-tasks-processor left on its inline literal';
    return;
  end if;

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
      ) as request_id;
    $job$
  );
end
$$;
