-- Telegram Micro-Checkins Pipeline — proactive daily low-stock check-ins over the bot,
-- extending the existing reactive zad-telegram-bot (inline keyboards, callback_query
-- dispatch, telegram_bindings chat_id resolution — all reused, not rebuilt).
--
-- Detection lives server-side against zad_inventory + zad_consumption (Task 18's learning
-- loop, zad_recompute_consumption) rather than reimplementing ConsumptionLearner's
-- on-device SharedPreferences model in SQL — the two are different models by design, and
-- zad_consumption is already the source of truth the server side (zad-brain) reads.
--
-- telegram_checkin_prompts mirrors telegram_pending_writes' shape: a durable row per
-- outstanding question, addressed by a short uuid in callback_data (Telegram caps
-- callback_data at 64 bytes — cramming item_name/qty in there directly would risk
-- truncation on a long Arabic name).
create table if not exists public.telegram_checkin_prompts (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users(id) on delete cascade,
  item_name           text not null,
  quantity_at_prompt  numeric not null,
  status              text not null default 'pending'
                        check (status in ('pending','answered_yes','answered_no','expired')),
  sent_at             timestamptz not null default now(),
  answered_at         timestamptz,
  expires_at          timestamptz not null default (now() + interval '3 days')
);

-- One outstanding prompt per item per user at a time — the daily job's candidate query
-- already excludes items with a pending row, but this is the hard guarantee against a
-- race (two overlapping cron runs) rather than relying on the query alone.
create unique index if not exists idx_checkin_prompts_pending_item
  on public.telegram_checkin_prompts(user_id, item_name) where status = 'pending';

alter table public.telegram_checkin_prompts enable row level security;
create policy "user_own_checkin_prompts" on public.telegram_checkin_prompts for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

comment on table public.telegram_checkin_prompts is
  'Outstanding "is <item> still in stock?" Telegram prompts sent by the daily check-in cron. Answered via callback_query (ck:<id>:y|n) in zad-telegram-bot.';

-- Scheduled trigger — same net.http_post + pg_cron pattern as update-behavior-profile-daily
-- (the only existing precedent in this repo). zad-telegram-bot has verify_jwt=false (it has
-- to, so Telegram's own webhook — which carries no Supabase JWT — can reach it), so the
-- platform-level JWT check that gates other functions doesn't apply here. This cron's
-- request is instead authorized by a secret bearer token unique to this one endpoint
-- (matched against a literal of the same value hardcoded in zad-telegram-bot/index.ts,
-- CHECKIN_CRON_SECRET — not the project's real service-role key, and not reusable for
-- anything beyond triggering this one job, so committing it here is a much smaller blast
-- radius than a leaked service-role key would be; the existing sibling cron job below
-- already established the precedent of a bearer literal committed in this migrations
-- folder).
select cron.schedule(
  'telegram-checkin-daily',
  '0 18 * * *', -- 18:00 UTC daily — early evening, before dinner prep in most GCC/EG timezones
  $$
  select net.http_post(
      url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=daily_checkins',
      headers:='{"Content-Type":"application/json","X-Checkin-Cron-Secret":"5bbebc0b2acb1099e758e822be15144f9bf30175da67a459dd8511b0139c8ec5"}'::jsonb,
      body:='{}'::jsonb,
      timeout_milliseconds:=55000
    ) as request_id;
  $$
);
