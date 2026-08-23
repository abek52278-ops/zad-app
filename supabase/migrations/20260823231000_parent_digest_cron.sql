-- تشغيل تقرير الوالدين كل جمعة ٩ صباحاً UTC
select cron.schedule(
  'parent-digest-weekly',
  '0 9 * * 5',
  $$
  select net.http_post(
    url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/parent-digest',
    headers := jsonb_build_object(
      'Authorization', 'Bearer ' || current_setting('app.settings.jwt_secret'),
      'Content-Type', 'application/json'
    ),
    body := '{}'::jsonb
  );
  $$
);
