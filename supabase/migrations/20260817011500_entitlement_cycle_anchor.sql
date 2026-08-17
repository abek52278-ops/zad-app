-- One anchor for the billing cycle, instead of three.
--
-- 20260817010000 shipped zad_entitlements with three different answers to "when does the
-- monthly allowance reset", and a dry run against this project's own data showed all three
-- disagreeing on the same account:
--
--   • a new row anchors on the day it was created, and zad_entitlement_refresh's roll
--     correctly preserves that day-of-month forward (16 Aug → 16 Sep). This one was right.
--   • zad_set_tier hardcoded the 1st of the month, so a customer who subscribed on the
--     28th got a fresh month's allowance three days later, for free, every month.
--   • the lapse path clamped the anchor to date_trunc('month', now()), i.e. the 1st, which
--     both contradicts the other two and hands a lapsed account a partial free month.
--
-- The rule this settles on is the one the roll already implements: the cycle resets on the
-- day-of-month it last started. Purchase on the 28th → renew on the 28th. Lapse today →
-- the free allowance runs from today. Nothing else in the file changes; both functions are
-- replaced whole rather than patched so the deployed body always matches a file in here.
--
-- No data migration needed. cycle_reset_at values already stored stay valid — they are
-- read by the same roll, which was never the broken part.

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
    -- Due now, so the roll below re-cuts the counters from the free allowance on this
    -- same pass and re-anchors the cycle to the moment the lapse was noticed.
    e.cycle_reset_at := now();
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
    cycle_reset_at = now() + interval '1 month',
    updated_at = now()
  where user_id = p_user returning * into e;

  return jsonb_build_object('ok', true, 'tier', e.tier, 'cycle_reset_at', e.cycle_reset_at);
end;
$$;
