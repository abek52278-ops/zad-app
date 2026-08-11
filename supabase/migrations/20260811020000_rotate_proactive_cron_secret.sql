-- The proactive cron secret is generated/stored out-of-band in Supabase Vault.
-- Keep the scheduled request free of plaintext credentials in migrations.
select cron.unschedule('agent-proactive-scan-hourly');

select cron.schedule(
  'agent-proactive-scan-hourly',
  '7 * * * *',
  $$
  select net.http_post(
      url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-brain',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'ZAD-PROACTIVE-CRON-SECRET',
          (select decrypted_secret from vault.decrypted_secrets where name = 'zad_proactive_cron_secret')
      ),
      body := '{"action":"run_proactive_scan"}'::jsonb,
      timeout_milliseconds := 55000
    ) as request_id;
  $$
);
