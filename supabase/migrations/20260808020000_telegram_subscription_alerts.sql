-- Telegram subscription/bill renewal alerts — same net.http_post + pg_cron pattern as
-- telegram_checkin_pipeline.sql (the only existing precedent). Closes the gap documented
-- in docs/agent/PLAN_2026_08_06_rebuild.md's subscriptions-hub review: renewal reminders
-- already exist in-app (ZadCentralBrain.fullAnalysis / ZadViewModel.generateSmartNotifications,
-- daysLeft in 0..3) but never reached Telegram — only a local Android notification / in-app
-- row. zad-telegram-bot's new runDailySubscriptionAlerts (?job=subscription_alerts) does the
-- actual send; this just schedules the daily trigger, authorized by its own secret bearer
-- token (SUBSCRIPTION_CRON_SECRET in index.ts) — not the checkin job's secret, so a leak of
-- one can't be used to trigger the other.
select cron.schedule(
  'telegram-subscription-alerts-daily',
  '0 9 * * *', -- 09:00 UTC — morning, before renewal-day bills typically charge
  $$
  select net.http_post(
      url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=subscription_alerts',
      headers:='{"Content-Type":"application/json","X-Subscription-Cron-Secret":"2ceb272a5a1b12cce797b99f3e6d07a79b540cb95a5823ac9155588269214d55"}'::jsonb,
      body:='{}'::jsonb,
      timeout_milliseconds:=55000
    ) as request_id;
  $$
);
