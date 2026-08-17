-- ── 5-HOUR RECHARGE CYCLE & 3-AD ENERGY BATTERY ─────────────────────────────────────
--
-- Replaces 24-hour / weekly lockout with a 5-hour natural recharge cycle for free users.
-- Free users receive 5 AI queries every 5 hours, bringing them back throughout the day.
-- Watching 3 rewarded video ads (e.g. 0/3 battery) grants an instant +5 queries & Brain pass.

create or replace function public.zad_entitlement_refresh(p_user uuid)
returns public.zad_entitlements
language plpgsql
security definer
set search_path = public
as $$
declare
  e public.zad_entitlements;
  t public.zad_tiers;
  v_cycle_interval constant interval := interval '5 hours';
  v_free_chat_refill constant int := 5;
begin
  insert into public.zad_entitlements (user_id) values (p_user)
  on conflict (user_id) do nothing;

  select * into e from public.zad_entitlements where user_id = p_user for update;

  -- A lapsed subscription becomes free immediately
  if e.tier <> 'free' and e.tier_expires_at is not null and e.tier_expires_at <= now() then
    e.tier := 'free';
    e.tier_expires_at := null;
    e.cycle_reset_at := now();
  end if;

  select * into t from public.zad_tiers where tier = e.tier;

  -- Refresh rolling quotas
  if e.cycle_reset_at <= now() then
    if e.tier = 'free' then
      e.chat_left := v_free_chat_refill;
      e.scan_left := greatest(e.scan_left, 1);
      e.cycle_reset_at := now() + v_cycle_interval;
    else
      e.brain_left := greatest(t.brain_monthly, 0);
      e.scan_left  := greatest(t.scan_monthly, 0);
      e.chat_left  := greatest(t.chat_monthly, 0);
      e.voice_left := greatest(t.voice_monthly, 0);
      e.cycle_reset_at := now() + interval '1 month';
    end if;
  end if;

  update public.zad_entitlements set
    tier = e.tier,
    tier_expires_at = e.tier_expires_at,
    brain_left = e.brain_left,
    scan_left = e.scan_left,
    chat_left = e.chat_left,
    voice_left = e.voice_left,
    cycle_reset_at = e.cycle_reset_at,
    updated_at = now()
  where user_id = p_user
  returning * into e;

  return e;
end;
$$;

-- ── 3-Ad Rewarded Grant ─────────────────────────────────────────────────────────────
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
  ads_per_session constant int := 3;
  min_gap constant interval := interval '10 seconds';
  daily_cap constant int := 30;
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
      chat_left = chat_left + 5,
      brain_session_expires_at = greatest(coalesce(brain_session_expires_at, now()), now()) + interval '12 hours',
      updated_at = now()
    where user_id = uid;

    return jsonb_build_object(
      'granted', true,
      'unlocked', true,
      'ad_watch_count', 0,
      'ads_per_session', ads_per_session,
      'chat_left', e.chat_left + 5
    );
  else
    update public.zad_entitlements set
      ad_watch_count = e.ad_watch_count + 1,
      updated_at = now()
    where user_id = uid;

    return jsonb_build_object(
      'granted', true,
      'unlocked', false,
      'ad_watch_count', e.ad_watch_count + 1,
      'ads_per_session', ads_per_session
    );
  end if;
end;
$$;

-- ── Entitlement Status (3 Ads Per Session) ──────────────────────────────────────────
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
    'ads_per_session', 3,
    'session_hours', 12,
    'brain_session_expires_at', e.brain_session_expires_at,
    'brain_session_active', e.tier = 'free' and e.brain_session_expires_at is not null
                            and e.brain_session_expires_at > now(),
    'brain_available', (e.tier = 'free' and e.brain_session_expires_at is not null
                        and e.brain_session_expires_at > now())
                       or t.brain_monthly < 0 or e.brain_left > 0 or free_ready
  );
end;
$$;
