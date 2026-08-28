-- ── AD-MONETIZED ACCESS GATES & 3-HOUR RECHARGE (خطة الإعلانات الذكية) ───────────────
--
-- الطلب:
--  1) تجديد الرصيد المجاني كل 3 ساعات بدل 5 (لحد ما نغيّره لما نشهر).
--  2) فتح الفويس والصور على بوت تليجرام للمجاني: كل إعلان مُكافئ واحد يفتح
--     "بوابة وسائط" لمدة 24 ساعة (1 إعلان = يوم كامل فويس + صور).
--  3) شاشة عقل زاد (ZadIntelligenceScreen) دخولها بإعلانين، وكل طلب جواها بإعلان
--     (يُدار من العميل عبر telegram-style credits — server-side).
--
-- قواعد الأمان (نفس نمط zad_ad_reward_grant):
--  - كل منح مرتبط بإعلان حقيقي متحقق من العميل فقط عبر RPC — مفيش كتابة مباشرة.
--  - daily_cap على الإعلانات كله (منع farm الإعلانات)، min_gap 10 ثواني.
--  - الباقات المدفوعة (telegram_media = true) مش بتتأثر.

-- ── 1. دورة الشحن كل 3 ساعات ─────────────────────────────────────────────────────────
create or replace function public.zad_entitlement_refresh(p_user uuid)
returns public.zad_entitlements
language plpgsql
security definer
set search_path = public
as $$
declare
  e public.zad_entitlements;
  t public.zad_tiers;
  v_cycle_interval constant interval := interval '3 hours'; -- كان 5 ساعات
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

-- ── 2. بوابة وسائط تليجرام بالإعلان: عمود + منطق المنح ───────────────────────────────
alter table public.zad_entitlements
  add column if not exists media_pass_expires_at timestamptz;

-- منح بوابة الوسائط: يُستدعى بعد إتمام مشاهدة إعلان مُكافئ واحد (عبر Edge Function
-- بنفس نمط zad_ad_reward_grant — العميل مش بيكتب الصلاحية بنفسه).
create or replace function public.zad_media_pass_grant(p_user uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
  last_at timestamptz;
  today_count int;
  min_gap constant interval := interval '10 seconds';
  daily_cap constant int := 12; -- 12 يوم-بوابة من الإعلانات كحد أقصى يومياً
begin
  uid := coalesce(auth.uid(), p_user);
  if uid is null then return jsonb_build_object('granted', false, 'reason', 'no user'); end if;
  if auth.uid() is not null and p_user is not null and p_user <> auth.uid() then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;

  -- المشتركون أصلاً عندهم وسائط — مش محتاجين بوابة
  if exists (select 1 from public.zad_entitlements e
             join public.zad_tiers t on t.tier = e.tier
             where e.user_id = uid and t.telegram_media = true) then
    return jsonb_build_object('granted', true, 'already_paid', true,
                              'media_pass_expires_at', null);
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

  insert into public.zad_ad_grants (user_id) values (uid);
  update public.zad_entitlements set
    media_pass_expires_at = now() + interval '24 hours',
    updated_at = now()
  where user_id = uid;

  return jsonb_build_object('granted', true, 'media_pass_expires_at', now() + interval '24 hours');
end;
$$;

-- 3. تحديث zad_entitlement_status: free + media_pass شغالة = وسائط مسموحة
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
    'telegram_media', t.telegram_media = true
                      or (e.tier = 'free' and e.media_pass_expires_at is not null
                          and e.media_pass_expires_at > now()),
    'media_pass_expires_at', e.media_pass_expires_at,
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

-- ── 4. zad_entitlement_consume: وسائط المجاني عبر بوابة الإعلان ───────────────────────
-- free + media_pass شغالة → voice/scan مسموحة من بوابة الإعلان (بدون لمس باقات المشتركين).
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

  -- بوابة الوسائط بالإعلان: مستخدم مجاني فتح البوابة بإعلان → voice/scan مسموحة 24 ساعة.
  if p_kind in ('voice', 'scan') and e.tier = 'free'
     and e.media_pass_expires_at is not null and e.media_pass_expires_at > now() then
    return jsonb_build_object('allowed', true, 'reason', 'media_pass', 'tier', e.tier,
                              'pass_expires_at', e.media_pass_expires_at);
  end if;

  if p_kind = 'brain' then
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
      'ads_per_session', 3,
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