-- Internal brain RPCs run with service_role and must not be callable through PostgREST.
-- Their old auth.uid() guards treated auth.uid() = null as service_role, which is not a
-- safe distinction when anon still has EXECUTE.
revoke execute on function public.zad_forward_ledger(uuid, int, text)
  from public, anon, authenticated;
revoke execute on function public.zad_memory_conflict(uuid, text, text)
  from public, anon, authenticated;
revoke execute on function public.zad_memory_resolve_conflict(uuid, uuid, text, text, real)
  from public, anon, authenticated;

grant execute on function public.zad_forward_ledger(uuid, int, text) to service_role;
grant execute on function public.zad_memory_conflict(uuid, text, text) to service_role;
grant execute on function public.zad_memory_resolve_conflict(uuid, uuid, text, text, real) to service_role;

-- Reward completion is intentionally client-callable, but never anonymous. The function
-- binds p_user to auth.uid() and rate-limits grants server-side.
revoke execute on function public.zad_ad_reward_grant(uuid) from public, anon;
grant execute on function public.zad_ad_reward_grant(uuid) to authenticated, service_role;

alter function public.zad_text_has_negation(text)
  set search_path = public, pg_temp;

-- The 5-hour/3-ad migration replaced refresh, status and reward grant, but left this
-- function returning the old five-ad contract. Keep its behavior and correct that drift.
create or replace function public.zad_entitlement_consume(
  p_user uuid,
  p_kind text,
  p_tz text default 'UTC'
)
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
    if e.tier = 'free' and e.brain_session_expires_at is not null
       and e.brain_session_expires_at > now() then
      return jsonb_build_object(
        'allowed', true,
        'reason', 'ad_session',
        'tier', e.tier,
        'session_expires_at', e.brain_session_expires_at
      );
    end if;
    if t.brain_monthly < 0 then
      return jsonb_build_object('allowed', true, 'reason', 'unlimited', 'tier', e.tier);
    end if;
    if e.brain_left > 0 then
      update public.zad_entitlements
      set brain_left = brain_left - 1, updated_at = now()
      where user_id = p_user
      returning brain_left into left_now;
      return jsonb_build_object('allowed', true, 'reason', 'quota', 'tier', e.tier, 'left', left_now);
    end if;
    if e.tier = 'free' and (
      e.last_free_brain_at is null
      or e.last_free_brain_at < public.zad_week_start(now(), coalesce(p_tz, 'UTC'))
    ) then
      update public.zad_entitlements
      set last_free_brain_at = now(), updated_at = now()
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

  quota := case p_kind
    when 'scan' then t.scan_monthly
    when 'chat' then t.chat_monthly
    else t.voice_monthly
  end;
  if quota < 0 then
    return jsonb_build_object('allowed', true, 'reason', 'unlimited', 'tier', e.tier);
  end if;
  if quota = 0 then
    return jsonb_build_object('allowed', false, 'reason', 'premium_only', 'tier', e.tier);
  end if;

  left_now := case p_kind
    when 'scan' then e.scan_left
    when 'chat' then e.chat_left
    else e.voice_left
  end;
  if left_now <= 0 then
    return jsonb_build_object(
      'allowed', false,
      'reason', 'quota_exhausted',
      'tier', e.tier,
      'cycle_reset_at', e.cycle_reset_at
    );
  end if;

  update public.zad_entitlements set
    scan_left  = case when p_kind = 'scan' then scan_left - 1 else scan_left end,
    chat_left  = case when p_kind = 'chat' then chat_left - 1 else chat_left end,
    voice_left = case when p_kind = 'voice' then voice_left - 1 else voice_left end,
    updated_at = now()
  where user_id = p_user;

  return jsonb_build_object(
    'allowed', true,
    'reason', 'quota',
    'tier', e.tier,
    'left', left_now - 1
  );
end;
$$;

revoke execute on function public.zad_entitlement_consume(uuid, text, text)
  from public, anon, authenticated;
grant execute on function public.zad_entitlement_consume(uuid, text, text) to service_role;
