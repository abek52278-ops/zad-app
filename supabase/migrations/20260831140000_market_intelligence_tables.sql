-- Market Intelligence — أسعار الأسواق الحية والتوقعات الذكية
-- Phase 0: Data ingestion tables لـ zad-market-intelligence function
-- 4 جداول: currency rates, price index, market snapshots, price alerts

-- ═══════════════════════════════════════════════════════════════════════════════

-- 1. currency_rates — تاريخ أسعار الصرف (global, لا user_id)
-- كل API call يخزن rate جديد؛ الدماغ يقرأ الأحدث
create table if not exists public.currency_rates (
  id bigserial primary key,
  from_currency text not null,
  to_currency text not null,
  rate double precision not null,
  source text not null default 'currency-api', -- 'currency-api', 'exchangerate-host', 'frankfurter'
  timestamp timestamptz not null default now()
);

comment on table public.currency_rates is 'Real-time exchange rates from free APIs. Used by zad-brain for conversion alerts and forecasting.';

-- Index للـ queries الشائعة (from/to pair + latest rate)
create index if not exists idx_currency_rates_from_to_timestamp
  on public.currency_rates(from_currency, to_currency, timestamp desc);

-- لو أردنا تنظيف بيانات قديمة (30 يوم keep)
create index if not exists idx_currency_rates_timestamp
  on public.currency_rates(timestamp desc);

alter table public.currency_rates enable row level security;

-- Public read (أي user يقدر يشوف أسعار الصرف)
create policy "currency_rates_public_read" on public.currency_rates
  for select using (true);

-- Server-only write (service-role من الـ function)
create policy "currency_rates_server_write" on public.currency_rates
  for insert with check (true); -- validate via function, not policy

-- ═══════════════════════════════════════════════════════════════════════════════

-- 2. price_index — تاريخ أسعار السلع (global + crowdsource)
-- user_id اختياري: لو جاية من crowdsource user، وإلا من API
create table if not exists public.price_index (
  id bigserial primary key,
  item_name text not null,
  item_category text, -- 'bread', 'milk', 'eggs', 'vegetables', etc
  price double precision not null,
  currency text not null default 'EGP',
  user_id uuid references auth.users(id) on delete set null, -- null = من API, not null = crowdsource
  location text, -- 'Cairo', 'Riyadh', 'Istanbul' (من crowdsource أو API)
  source text not null default 'usda', -- 'usda', 'open-food-facts', 'crowdsource'
  timestamp timestamptz not null default now()
);

comment on table public.price_index is 'Food & commodity prices from APIs + user crowdsourcing. zad-brain uses latest prices for trend analysis and alerts.';

-- Indexes للـ queries الشائعة
create index if not exists idx_price_index_item_timestamp
  on public.price_index(item_category, timestamp desc);

create index if not exists idx_price_index_user_crowdsource
  on public.price_index(user_id, timestamp desc) where user_id is not null;

alter table public.price_index enable row level security;

-- Public read (أي user يقدر يشوف الأسعار العام)
create policy "price_index_public_read" on public.price_index
  for select using (true);

-- Users write crowdsource prices
create policy "price_index_user_crowdsource_write" on public.price_index
  for insert with check (
    user_id is null or auth.uid() = user_id
  );

-- Server write (APIs)
create policy "price_index_server_write" on public.price_index
  for insert with check (user_id is null);

-- ═══════════════════════════════════════════════════════════════════════════════

-- 3. market_snapshot — مؤشرات السوق الدورية (per user, يومي)
-- Summary calculated by zad-brain: inflation index, food price changes, etc
create table if not exists public.market_snapshot (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,

  -- Aggregate metrics (calculated by zad-brain from price_index + currency_rates)
  inflation_index double precision, -- 0-100 scale
  food_price_change_pct double precision, -- % change last 30 days
  commodity_price_change_pct double precision, -- metals, oils, etc
  average_shopping_cost double precision, -- avg user's basket cost

  -- Weather context
  weather_condition text, -- 'normal', 'heat_wave', 'cold_snap', 'drought'
  expected_impact text, -- 'food_price_up', 'utility_up', etc

  -- Confidence
  data_freshness text not null default 'current', -- 'current', 'stale', 'missing'
  created_at timestamptz not null default now(),

  unique(user_id, date(created_at)) -- One snapshot per user per day
);

comment on table public.market_snapshot is 'Daily market conditions + inflation + price trends. zad-brain reads to detect anomalies and alert users.';

create index if not exists idx_market_snapshot_user_created
  on public.market_snapshot(user_id, created_at desc);

alter table public.market_snapshot enable row level security;

-- Each user sees their own snapshot + family members (via family_id join)
create policy "market_snapshot_own" on public.market_snapshot
  for select using (auth.uid() = user_id);

-- Server write (zad-brain)
create policy "market_snapshot_server_write" on public.market_snapshot
  for insert with check (true);

-- ═══════════════════════════════════════════════════════════════════════════════

-- 4. price_alerts — تنبيهات أسعار للعائلة (per user)
-- يُشغّل من price_index عند تغيير ملحوظ أو machine learning score عالي
create table if not exists public.price_alerts (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,

  -- Alert content
  item_name text not null,
  item_category text,
  alert_type text not null, -- 'price_drop', 'price_surge', 'below_avg', 'deal_found', 'nearby_merchant'
  old_price double precision,
  new_price double precision,
  percentage_change double precision, -- -12.5 = 12.5% cheaper, +18 = 18% expensive

  -- Context
  merchant_name text, -- 'Carrefour Cairo', 'Spinneys', 'Noon'
  location text, -- اختياري: distance/branch info
  savings_amount double precision, -- مثلاً 150 جنيه توفير

  -- Status
  dismissed_at timestamptz,
  acted_on_at timestamptz, -- user confirmed شراء

  created_at timestamptz not null default now()
);

comment on table public.price_alerts is 'Price change notifications + deal alerts. Users dismiss or confirm actions. Feeds gamification leaderboard.';

create index if not exists idx_price_alerts_user_created
  on public.price_alerts(user_id, created_at desc);

create index if not exists idx_price_alerts_dismissed
  on public.price_alerts(user_id, dismissed_at, created_at desc) where dismissed_at is null;

alter table public.price_alerts enable row level security;

-- Each user sees their own alerts
create policy "price_alerts_own" on public.price_alerts
  for select using (auth.uid() = user_id);

-- Users dismiss/confirm their alerts
create policy "price_alerts_own_update" on public.price_alerts
  for update using (auth.uid() = user_id);

-- Server write (zad-market-intelligence + zad-brain)
create policy "price_alerts_server_write" on public.price_alerts
  for insert with check (true);

-- ═══════════════════════════════════════════════════════════════════════════════

-- Housekeeping: Clean old data (optional, can be run as separate job)
-- currency_rates: keep 90 days
-- price_index: keep 365 days (for yearly trends)
-- market_snapshot: keep 180 days (half-year trends)
-- price_alerts: keep 365 days (gamification leaderboard history)

-- No automatic triggers here — let zad-market-intelligence manage retention
-- via Deno job during cron, so we don't hammer the DB on read-heavy queries.

-- ═══════════════════════════════════════════════════════════════════════════════

-- 5. fcm_tokens — Firebase Cloud Messaging tokens للإشعارات (Phase 1)
-- كل جهاز يسجل token الخاص به لاستقبال notifications
create table if not exists public.fcm_tokens (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,

  token text not null unique,
  device_name text, -- "iPhone 12", "Samsung Galaxy", etc
  device_type text not null check (device_type in ('ios', 'android', 'web')),

  active boolean not null default true,
  last_used_at timestamptz default now(),
  created_at timestamptz not null default now()
);

comment on table public.fcm_tokens is 'Firebase Cloud Messaging tokens for push notifications. Each device/user pair maintains one token.';

create index if not exists idx_fcm_tokens_user_active
  on public.fcm_tokens(user_id, active) where active = true;

create index if not exists idx_fcm_tokens_token
  on public.fcm_tokens(token);

alter table public.fcm_tokens enable row level security;

-- Users manage their own tokens
create policy "fcm_tokens_own" on public.fcm_tokens
  for select using (auth.uid() = user_id);

create policy "fcm_tokens_own_write" on public.fcm_tokens
  for insert with check (auth.uid() = user_id);

create policy "fcm_tokens_own_update" on public.fcm_tokens
  for update using (auth.uid() = user_id);

-- Server read (zad-market-intelligence)
create policy "fcm_tokens_server_read" on public.fcm_tokens
  for select using (true);

-- ═══════════════════════════════════════════════════════════════════════════════

-- 6. user_achievements — إنجازات المستخدم والـ gamification (Phase 3)
-- تتبع الشارات والإنجازات التي فتحها المستخدم
create table if not exists public.user_achievements (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,

  achievement_id text not null, -- 'first_step', 'rising_star', etc
  points_earned int not null default 0,

  unlocked_at timestamptz not null default now()
);

comment on table public.user_achievements is 'Gamification achievements and points for crowdsourcing contributions.';

create index if not exists idx_user_achievements_user_id
  on public.user_achievements(user_id);

create unique index if not exists idx_user_achievements_unique
  on public.user_achievements(user_id, achievement_id);

alter table public.user_achievements enable row level security;

-- Users see their own achievements
create policy "user_achievements_own" on public.user_achievements
  for select using (auth.uid() = user_id);

-- Server write (zad-market-intelligence)
create policy "user_achievements_server_write" on public.user_achievements
  for insert with check (true);

-- ═══════════════════════════════════════════════════════════════════════════════

-- 7. shopping_recommendations — توصيات شراء ذكية من Gemini (Phase 4)
-- استخدم market data + family budget لتقديم توصيات personalized
create table if not exists public.shopping_recommendations (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,

  -- Recommendation details
  item_name text not null,
  recommendation_type text not null check (recommendation_type in
    ('buy_now', 'wait', 'bulk_buy', 'avoid', 'substitute')),
  reasoning text, -- Arabic explanation

  -- Financial context
  estimated_savings double precision,
  urgency text not null check (urgency in ('low', 'medium', 'high')),

  -- Store info (optional)
  best_store text,
  best_price double precision,

  -- Tracking
  acted_on_at timestamptz,
  dismissed_at timestamptz,

  created_at timestamptz not null default now()
);

comment on table public.shopping_recommendations is 'AI-powered shopping recommendations from Gemini analysis of prices, weather, budget, and family preferences.';

create index if not exists idx_shopping_recommendations_user
  on public.shopping_recommendations(user_id, created_at desc);

create index if not exists idx_shopping_recommendations_urgency
  on public.shopping_recommendations(urgency) where acted_on_at is null;

alter table public.shopping_recommendations enable row level security;

-- Users see their own recommendations
create policy "shopping_recommendations_own" on public.shopping_recommendations
  for select using (auth.uid() = user_id);

-- Users mark as acted/dismissed
create policy "shopping_recommendations_own_update" on public.shopping_recommendations
  for update using (auth.uid() = user_id);

-- Server write (zad-market-intelligence)
create policy "shopping_recommendations_server_write" on public.shopping_recommendations
  for insert with check (true);
