-- مراجعة أخطر الدوال (٢٠٢٦-٠٩-٠١، بند ٣٠.٤ من ZAD_V2_MASTER_PLAN.md): zad_resolve_transaction_proposal
-- و zad_agent_undo آمنين تصميميًا (بياخدوا الهوية من auth.uid() بس، مفيش p_user من العميل خالص).
-- zad_ad_reward_grant و zad_entitlement_status لسه شكلهم coalesce(p_user, auth.uid()) القديم
-- اللي كان سبب ثغرة zad_media_pass_grant، لكن اتأكد حي إن anon EXECUTE متقفول عليهم فعلاً
-- (has_function_privilege('anon', ..., 'EXECUTE') = false) — يعني مش قابلين للاستغلال دلوقتي.
-- المشكلة إن الحماية دي كلها على مستوى الـgrant بس، مش مكتوبة جوه الفانكشن — لو حد رجّع
-- grant لـanon يوم ما (زي اللي حصل بالظبط مع zad_media_pass_grant قبل كده)، مفيش خط دفاع
-- تاني. بنضيف هنا نفس الفحص الصريح اللي zad_media_pass_grant اتصلح بيه، دفاع في العمق
-- مش رد فعل على استغلال فعلي.

create or replace function public.zad_ad_reward_grant(p_user uuid default null::uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  uid uuid;
  e public.zad_entitlements;
  last_at timestamptz;
  today_count int;
  ads_per_session constant int := 3;
  min_gap constant interval := interval '10 seconds';
  daily_cap constant int := 30;
begin
  if auth.uid() is null and coalesce(auth.role(), '') <> 'service_role' then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;
  if auth.uid() is not null and p_user is not null and p_user <> auth.uid() then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;

  uid := coalesce(auth.uid(), p_user);
  if uid is null then return jsonb_build_object('granted', false, 'reason', 'no user'); end if;

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
$function$;

create or replace function public.zad_entitlement_status(p_user uuid default null::uuid, p_tz text default 'UTC'::text)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  uid uuid;
  e public.zad_entitlements;
  t public.zad_tiers;
  free_ready boolean;
begin
  if auth.uid() is null and coalesce(auth.role(), '') <> 'service_role' then
    return jsonb_build_object('error', 'forbidden');
  end if;
  if auth.uid() is not null and p_user is not null and p_user <> auth.uid() then
    return jsonb_build_object('error', 'forbidden');
  end if;

  uid := coalesce(p_user, auth.uid());
  if uid is null then return jsonb_build_object('error', 'no user'); end if;

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
$function$;
