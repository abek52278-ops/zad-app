-- Entitlements: who may call the expensive paths, decided in one place.
--
-- Until now every AI path in Zad was free and unmetered for everyone: the agent loop, the
-- vision scans, the Telegram voice notes. The only ceiling was W4's per-user daily request
-- cap in zad-brain, which exists to stop a runaway ingestion loop — not to price anything.
-- This migration adds the commercial boundary underneath all three surfaces (app, brain,
-- Telegram bot) so that none of them can disagree about what a customer is owed, the same
-- rule zad_budget_state established for money.
--
-- Deliberately NOT columns on zad_users. SupabaseRepo writes that table with targeted
-- column maps precisely because a full-row upsert there clobbers whatever it did not know
-- about (see the comment on `syncMonthlyLimit`), and entitlements are exactly the kind of
-- column a client must never be able to write. A separate table with no client-writable
-- policy makes that structural instead of a convention someone has to remember.
--
-- The three questions this answers, and where each is enforced:
--   • "may this user run a deep عقل زاد consultation?"  → zad-brain, action=brain_consult
--   • "may this user send a photo/voice note to the bot?" → zad-telegram-bot
--   • "how much is left this month?"                     → the app, for the paywall UI
-- The client is told the answer; it never decides it.

-- ── Quotas as data, not as code ────────────────────────────────────────────────────────
-- Prices and limits get tuned. Putting them in a table means tuning them is an UPDATE,
-- not a migration + an edge-function deploy + an app release that all have to agree.
-- -1 means unlimited; 0 means the tier does not include the feature at all.
create table if not exists public.zad_tiers (
  tier            text primary key,
  brain_monthly   int  not null,
  scan_monthly    int  not null,
  chat_monthly    int  not null,
  voice_monthly   int  not null,
  -- Telegram media (voice notes + receipt photos) is the paid hook: the two things a
  -- customer wants when they are out of the house and away from the app.
  telegram_media  boolean not null default false,
  -- Ads are shown to free users only. A paying customer who still sees ads churns.
  ads_enabled     boolean not null default true,
  sort_order      int  not null default 0
);

insert into public.zad_tiers (tier, brain_monthly, scan_monthly, chat_monthly, voice_monthly, telegram_media, ads_enabled, sort_order)
values
  -- free: no monthly عقل زاد allowance at all. Its access comes from the weekly free
  -- consultation and from the rewarded-ad session below — both handled specially.
  ('free',     0,   15,   90,   0, false, true,  0),
  ('starter', 15,   30,  150,  30, true,  false, 1),
  ('plus',    50,  100,  500, 100, true,  false, 2),
  ('pro',    150,  300,   -1,  -1, true,  false, 3)
on conflict (tier) do nothing;

alter table public.zad_tiers enable row level security;
-- Readable by anyone signed in: the paywall screen needs to render the comparison table.
-- There is nothing secret here and nothing writable.
drop policy if exists zad_tiers_read on public.zad_tiers;
create policy zad_tiers_read on public.zad_tiers for select to authenticated using (true);

-- ── Per-user state ─────────────────────────────────────────────────────────────────────
create table if not exists public.zad_entitlements (
  user_id                  uuid primary key references auth.users(id) on delete cascade,
  tier                     text not null default 'free' references public.zad_tiers(tier),
  -- When a paid tier lapses. null = never expires (what 'free' always is). Checked on
  -- every read, so a lapsed subscription degrades on the next call, not on a cron run.
  tier_expires_at          timestamptz,
  brain_left               int not null default 0,
  scan_left                int not null default 0,
  chat_left                int not null default 0,
  voice_left               int not null default 0,
  -- The monthly counters roll forward from here. Stored rather than derived from
  -- created_at so a customer's cycle can be aligned to their real billing date later.
  --
  -- Defaults to now(), i.e. already due — which is what seeds a brand-new row. The
  -- counters above all default to 0, and if this defaulted to next month instead, the
  -- roll in zad_entitlement_refresh() would not fire until then: a new free customer
  -- would get zero scans and zero chat messages for their entire first month. Being
  -- due on creation makes the first refresh cut the real allowance from zad_tiers.
  cycle_reset_at           timestamptz not null default now(),
  -- The free tier's one deep consultation per calendar week (weeks start Saturday).
  last_free_brain_at       timestamptz,
  -- Rewarded-ad progress toward the next unlocked session, 0..ZAD_ADS_PER_SESSION-1.
  ad_watch_count           int not null default 0,
  -- While this is in the future the free user has unlimited عقل زاد. Set by the fifth
  -- rewarded ad; nothing else writes it.
  brain_session_expires_at timestamptz,
  updated_at               timestamptz not null default now()
);

alter table public.zad_entitlements enable row level security;
-- Read-only to its owner. Every mutation goes through the SECURITY DEFINER functions
-- below, so there is deliberately no insert/update/delete policy for `authenticated`:
-- a client that could UPDATE this table could grant itself the pro tier.
drop policy if exists zad_entitlements_read_own on public.zad_entitlements;
create policy zad_entitlements_read_own on public.zad_entitlements
  for select to authenticated using (user_id = (select auth.uid()));

-- ── Rewarded-ad grant log ──────────────────────────────────────────────────────────────
-- Exists to rate-limit the grant, not to audit it. The client calls zad_ad_reward_grant()
-- when AdMob fires onUserEarnedReward, and a modified client can call it without watching
-- anything — so the row count per window is the only thing standing between the honest
-- path and a free pro tier. AdMob server-side verification (SSV) is the real fix and
-- would replace the client call with a signed callback from Google; the interval and the
-- daily ceiling here are what holds until that is configured in the AdMob console.
create table if not exists public.zad_ad_grants (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references auth.users(id) on delete cascade,
  granted_at timestamptz not null default now()
);
create index if not exists zad_ad_grants_user_time on public.zad_ad_grants (user_id, granted_at desc);
alter table public.zad_ad_grants enable row level security;
-- No policy at all: only the SECURITY DEFINER grant function touches this.

-- ── Saturday-based week start ──────────────────────────────────────────────────────────
-- Postgres's date_trunc('week') is Monday-based. Zad's customers are in Egypt and the
-- Gulf, where the week starts Saturday, and "your free weekly consultation renewed" has
-- to land on the day they think of as the start of the week.
create or replace function public.zad_week_start(p_at timestamptz, p_tz text default 'UTC')
returns timestamptz
language sql
immutable
set search_path = public
as $$
  -- isodow: Mon=1 … Sun=7. (isodow + 1) % 7 is the number of days back to last Saturday.
  select (date_trunc('day', (p_at at time zone p_tz))
          - make_interval(days => ((extract(isodow from (p_at at time zone p_tz))::int + 1) % 7)))
         at time zone p_tz;
$$;

-- ── The row, refreshed ─────────────────────────────────────────────────────────────────
-- Every entry point goes through this: it creates the row if the user predates this
-- migration, downgrades a lapsed paid tier, and rolls the monthly counters forward. Doing
-- it lazily on read means there is no cron job whose failure silently freezes everyone's
-- quota at zero.
create or replace function public.zad_entitlement_refresh(p_user uuid)
returns public.zad_entitlements
language plpgsql
security definer
set search_path = public
as $$
declare
  e public.zad_entitlements;
  t public.zad_tiers;
begin
  insert into public.zad_entitlements (user_id) values (p_user)
  on conflict (user_id) do nothing;

  select * into e from public.zad_entitlements where user_id = p_user for update;

  -- A lapsed subscription becomes free immediately, and its counters are re-cut from the
  -- free allowance on the same pass rather than keeping the paid numbers until month end.
  if e.tier <> 'free' and e.tier_expires_at is not null and e.tier_expires_at <= now() then
    e.tier := 'free';
    e.tier_expires_at := null;
    e.cycle_reset_at := date_trunc('month', now());
  end if;

  select * into t from public.zad_tiers where tier = e.tier;

  if e.cycle_reset_at <= now() then
    e.brain_left := greatest(t.brain_monthly, 0);
    e.scan_left  := greatest(t.scan_monthly, 0);
    e.chat_left  := greatest(t.chat_monthly, 0);
    e.voice_left := greatest(t.voice_monthly, 0);
    -- Walk forward whole months rather than adding one: a dormant account coming back
    -- after four months should land on the next real boundary, not three months ago.
    e.cycle_reset_at := date_trunc('month', now()) + interval '1 month'
                        + (e.cycle_reset_at - date_trunc('month', e.cycle_reset_at));
    if e.cycle_reset_at <= now() then
      e.cycle_reset_at := date_trunc('month', now()) + interval '1 month';
    end if;
  end if;

  update public.zad_entitlements set
    tier = e.tier, tier_expires_at = e.tier_expires_at,
    brain_left = e.brain_left, scan_left = e.scan_left,
    chat_left = e.chat_left, voice_left = e.voice_left,
    cycle_reset_at = e.cycle_reset_at, updated_at = now()
  where user_id = p_user
  returning * into e;

  return e;
end;
$$;

-- ── Status (read path, for the UI) ─────────────────────────────────────────────────────
-- Everything the paywall needs in one call, including the constants — ads_per_session
-- lives here rather than being written into Kotlin as well, so changing it is a server
-- change and old app builds stay correct.
create or replace function public.zad_entitlement_status(p_user uuid default null, p_tz text default 'UTC')
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
  e public.zad_entitlements;
  t public.zad_tiers;
  free_ready boolean;
begin
  -- Same guard shape as zad_forward_ledger: a signed-in caller may only ask about
  -- themselves; service_role (auth.uid() is null) may ask about anyone.
  uid := coalesce(p_user, auth.uid());
  if uid is null then return jsonb_build_object('error', 'no user'); end if;
  if auth.uid() is not null and auth.uid() <> uid then
    return jsonb_build_object('error', 'forbidden');
  end if;

  e := public.zad_entitlement_refresh(uid);
  select * into t from public.zad_tiers where tier = e.tier;

  free_ready := e.tier = 'free'
    and (e.last_free_brain_at is null
         or e.last_free_brain_at < public.zad_week_start(now(), coalesce(p_tz, 'UTC')));

  return jsonb_build_object(
    'tier', e.tier,
    'tier_expires_at', e.tier_expires_at,
    'ads_enabled', t.ads_enabled,
    'telegram_media', t.telegram_media,
    'brain_left', case when t.brain_monthly < 0 then -1 else e.brain_left end,
    'scan_left',  case when t.scan_monthly  < 0 then -1 else e.scan_left  end,
    'chat_left',  case when t.chat_monthly  < 0 then -1 else e.chat_left  end,
    'voice_left', case when t.voice_monthly < 0 then -1 else e.voice_left end,
    'cycle_reset_at', e.cycle_reset_at,
    'weekly_free_ready', free_ready,
    'next_weekly_free_at', case when free_ready then null
      else public.zad_week_start(now(), coalesce(p_tz, 'UTC')) + interval '7 days' end,
    'ad_watch_count', e.ad_watch_count,
    'ads_per_session', 5,
    'session_hours', 24,
    'brain_session_expires_at', e.brain_session_expires_at,
    -- Same free-tier-only scoping as zad_entitlement_consume, so the paywall never
    -- shows a subscriber an "ad session" they are not actually being served by.
    'brain_session_active', e.tier = 'free' and e.brain_session_expires_at is not null
                            and e.brain_session_expires_at > now(),
    -- The single question every caller actually asks. Computed here so the app, the bot
    -- and the brain cannot each get it subtly wrong.
    'brain_available', (e.tier = 'free' and e.brain_session_expires_at is not null
                        and e.brain_session_expires_at > now())
                       or t.brain_monthly < 0 or e.brain_left > 0 or free_ready
  );
end;
$$;

-- ── Consume (the gate) ─────────────────────────────────────────────────────────────────
-- Called by the edge functions with the service role, immediately before spending a model
-- call. Returns { allowed, reason, ... } and has already decremented when allowed=true.
--
-- The order of the brain checks is the product, in code: an unlocked ad session is free
-- and unlimited while it lasts (the customer already paid in attention), then the paid
-- monthly allowance, and the weekly freebie is spent only when nothing else covers it —
-- so a subscriber never burns it and still has it if they downgrade.
create or replace function public.zad_entitlement_consume(p_user uuid, p_kind text, p_tz text default 'UTC')
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  e public.zad_entitlements;
  t public.zad_tiers;
  quota int;
  left_now int;
begin
  if p_kind not in ('brain', 'scan', 'chat', 'voice') then
    return jsonb_build_object('allowed', false, 'reason', 'unknown_kind');
  end if;

  e := public.zad_entitlement_refresh(p_user);
  select * into t from public.zad_tiers where tier = e.tier;

  if p_kind = 'brain' then
    -- Free tier only. A subscriber spends the allowance they paid for; letting a
    -- leftover ad session answer for them would leave their monthly count untouched
    -- and make the tier they bought look like it does nothing.
    if e.tier = 'free' and e.brain_session_expires_at is not null and e.brain_session_expires_at > now() then
      return jsonb_build_object('allowed', true, 'reason', 'ad_session', 'tier', e.tier,
                                'session_expires_at', e.brain_session_expires_at);
    end if;
    if t.brain_monthly < 0 then
      return jsonb_build_object('allowed', true, 'reason', 'unlimited', 'tier', e.tier);
    end if;
    if e.brain_left > 0 then
      update public.zad_entitlements set brain_left = brain_left - 1, updated_at = now()
      where user_id = p_user returning brain_left into left_now;
      return jsonb_build_object('allowed', true, 'reason', 'quota', 'tier', e.tier, 'left', left_now);
    end if;
    if e.tier = 'free' and (e.last_free_brain_at is null
        or e.last_free_brain_at < public.zad_week_start(now(), coalesce(p_tz, 'UTC'))) then
      update public.zad_entitlements set last_free_brain_at = now(), updated_at = now()
      where user_id = p_user;
      return jsonb_build_object('allowed', true, 'reason', 'weekly_free', 'tier', e.tier);
    end if;
    return jsonb_build_object(
      'allowed', false,
      'reason', case when e.tier = 'free' then 'needs_ads_or_upgrade' else 'quota_exhausted' end,
      'tier', e.tier,
      'ad_watch_count', e.ad_watch_count,
      'ads_per_session', 5,
      'next_weekly_free_at', public.zad_week_start(now(), coalesce(p_tz, 'UTC')) + interval '7 days',
      'cycle_reset_at', e.cycle_reset_at
    );
  end if;

  quota := case p_kind when 'scan' then t.scan_monthly
                       when 'chat' then t.chat_monthly
                       else t.voice_monthly end;
  if quota < 0 then
    return jsonb_build_object('allowed', true, 'reason', 'unlimited', 'tier', e.tier);
  end if;
  -- A tier whose allowance for this feature is zero has never had it. That reads to the
  -- customer as "upgrade to unlock", not as "you ran out" — a different message entirely.
  if quota = 0 then
    return jsonb_build_object('allowed', false, 'reason', 'premium_only', 'tier', e.tier);
  end if;

  left_now := case p_kind when 'scan' then e.scan_left
                          when 'chat' then e.chat_left
                          else e.voice_left end;
  if left_now <= 0 then
    return jsonb_build_object('allowed', false, 'reason', 'quota_exhausted', 'tier', e.tier,
                              'cycle_reset_at', e.cycle_reset_at);
  end if;

  update public.zad_entitlements set
    scan_left  = case when p_kind = 'scan'  then scan_left  - 1 else scan_left  end,
    chat_left  = case when p_kind = 'chat'  then chat_left  - 1 else chat_left  end,
    voice_left = case when p_kind = 'voice' then voice_left - 1 else voice_left end,
    updated_at = now()
  where user_id = p_user;

  return jsonb_build_object('allowed', true, 'reason', 'quota', 'tier', e.tier, 'left', left_now - 1);
end;
$$;

-- ── Rewarded-ad grant ──────────────────────────────────────────────────────────────────
-- The one function the client is allowed to call that changes an entitlement, which is
-- why it is the one with a rate limit. Five grants unlock a 24-hour عقل زاد session.
create or replace function public.zad_ad_reward_grant(p_user uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
  e public.zad_entitlements;
  last_at timestamptz;
  today_count int;
  ads_per_session constant int := 5;
  -- A rewarded video cannot be completed faster than this, so anything quicker is not a
  -- watched ad. Generous enough not to punish a slow round-trip on a real one.
  min_gap constant interval := interval '15 seconds';
  daily_cap constant int := 40;
begin
  uid := coalesce(auth.uid(), p_user);
  if uid is null then return jsonb_build_object('granted', false, 'reason', 'no user'); end if;
  if auth.uid() is not null and p_user is not null and p_user <> auth.uid() then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;

  select max(granted_at) into last_at from public.zad_ad_grants where user_id = uid;
  if last_at is not null and last_at > now() - min_gap then
    return jsonb_build_object('granted', false, 'reason', 'too_fast');
  end if;

  select count(*) into today_count from public.zad_ad_grants
  where user_id = uid and granted_at >= (now() at time zone 'utc')::date;
  if today_count >= daily_cap then
    return jsonb_build_object('granted', false, 'reason', 'daily_ad_cap');
  end if;

  e := public.zad_entitlement_refresh(uid);
  insert into public.zad_ad_grants (user_id) values (uid);

  if e.ad_watch_count + 1 >= ads_per_session then
    update public.zad_entitlements set
      ad_watch_count = 0,
      -- Extends rather than replaces, so a customer who tops up mid-session is not
      -- punished for it by having the remaining hours thrown away.
      brain_session_expires_at = greatest(coalesce(brain_session_expires_at, now()), now()) + interval '24 hours',
      updated_at = now()
    where user_id = uid returning * into e;
    return jsonb_build_object('granted', true, 'session_unlocked', true,
      'ad_watch_count', 0, 'ads_per_session', ads_per_session,
      'brain_session_expires_at', e.brain_session_expires_at);
  end if;

  update public.zad_entitlements set ad_watch_count = ad_watch_count + 1, updated_at = now()
  where user_id = uid returning * into e;
  return jsonb_build_object('granted', true, 'session_unlocked', false,
    'ad_watch_count', e.ad_watch_count, 'ads_per_session', ads_per_session);
end;
$$;

-- ── Tier assignment (billing's entry point) ────────────────────────────────────────────
-- Google Play Billing is not wired up yet; when it is, its purchase-verification callback
-- is the only thing that should call this. service_role only, deliberately — an
-- `authenticated` grant here would let any client hand itself the pro tier.
create or replace function public.zad_set_tier(p_user uuid, p_tier text, p_expires timestamptz default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  e public.zad_entitlements;
  t public.zad_tiers;
begin
  select * into t from public.zad_tiers where tier = p_tier;
  if not found then return jsonb_build_object('ok', false, 'reason', 'unknown_tier'); end if;

  perform public.zad_entitlement_refresh(p_user);
  update public.zad_entitlements set
    tier = p_tier,
    tier_expires_at = p_expires,
    -- A new tier starts its allowance now. Without this the customer pays today and gets
    -- the new quota whenever their old cycle happened to end.
    brain_left = greatest(t.brain_monthly, 0),
    scan_left  = greatest(t.scan_monthly, 0),
    chat_left  = greatest(t.chat_monthly, 0),
    voice_left = greatest(t.voice_monthly, 0),
    cycle_reset_at = date_trunc('month', now()) + interval '1 month',
    updated_at = now()
  where user_id = p_user returning * into e;

  return jsonb_build_object('ok', true, 'tier', e.tier, 'cycle_reset_at', e.cycle_reset_at);
end;
$$;

-- ── Provisioning ───────────────────────────────────────────────────────────────────────
-- Backfill everyone who already exists, then keep new signups covered. zad_entitlement_
-- refresh() creates the row on demand anyway, so this is belt-and-braces — but it means a
-- plain `select` from the app on a brand-new account returns a row instead of nothing.
insert into public.zad_entitlements (user_id)
select id from auth.users on conflict (user_id) do nothing;

create or replace function public.zad_provision_entitlement()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.zad_entitlements (user_id) values (new.id) on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists zad_provision_entitlement_trg on auth.users;
create trigger zad_provision_entitlement_trg
  after insert on auth.users
  for each row execute function public.zad_provision_entitlement();

-- ── Grants ─────────────────────────────────────────────────────────────────────────────
-- Default is `execute to public` on a new function, which for a SECURITY DEFINER function
-- means anon can call it. Revoke first, then grant only what each caller needs — the
-- same lesson as 20260817003500_revoke_public_not_just_anon.sql.
revoke execute on function public.zad_entitlement_refresh(uuid) from public, anon, authenticated;
revoke execute on function public.zad_entitlement_consume(uuid, text, text) from public, anon, authenticated;
revoke execute on function public.zad_set_tier(uuid, text, timestamptz) from public, anon, authenticated;
revoke execute on function public.zad_provision_entitlement() from public, anon, authenticated;
revoke execute on function public.zad_entitlement_status(uuid, text) from public, anon;
revoke execute on function public.zad_ad_reward_grant(uuid) from public, anon;
revoke execute on function public.zad_week_start(timestamptz, text) from public, anon;

grant execute on function public.zad_entitlement_status(uuid, text) to authenticated, service_role;
grant execute on function public.zad_ad_reward_grant(uuid) to authenticated, service_role;
grant execute on function public.zad_week_start(timestamptz, text) to authenticated, service_role;
grant execute on function public.zad_entitlement_refresh(uuid) to service_role;
grant execute on function public.zad_entitlement_consume(uuid, text, text) to service_role;
grant execute on function public.zad_set_tier(uuid, text, timestamptz) to service_role;
