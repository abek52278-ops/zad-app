-- Task 18 — close the learning loop.
--
-- Fault B (the worse one): answering "how many eggs?" updated zad_inventory.quantity but
-- left eggs with no zad_consumption row, so it stayed in stock_unknown and the brain would
-- ask again tomorrow, forever. A quantity answer's durable value is a TIMESTAMPED
-- OBSERVATION, not a memory note ("user has 12 eggs" is stale in days). Two observations
-- give one consumption sample; samples give a rate; the rate is the actual learning.
--
-- Everything here is deterministic SQL — no model discretion — because the STEP 3 failure
-- was precisely that a small model skipped an instructed-but-optional step.

create table if not exists public.zad_inventory_observations (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  item_name   text not null,
  qty         numeric not null check (qty >= 0),
  observed_at timestamptz not null default now(),
  source      text not null check (source in
                ('question_answer','camera_ocr','manual','purchase')),
  created_at  timestamptz not null default now()
);

create index if not exists zad_obs_item
  on public.zad_inventory_observations (user_id, item_name, observed_at desc);

-- RLS is the security boundary (CLAUDE.md) — enable before this ships, not after.
alter table public.zad_inventory_observations enable row level security;

drop policy if exists zad_obs_owner on public.zad_inventory_observations;
create policy zad_obs_owner on public.zad_inventory_observations
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Rate derivation. Lives in SQL, not TypeScript, so the edge function and the
-- Android client run the SAME implementation instead of two that drift.
--
-- MEDIAN, not mean: one unusual week (guests, travel, Ramadan) would otherwise
-- distort a household-scale dataset badly.
-- ─────────────────────────────────────────────────────────────────────────────
create or replace function public.zad_recompute_consumption(p_user uuid, p_item text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_median double precision;
  v_samples int;
begin
  -- SECURITY DEFINER is needed so the function can write zad_consumption, but it must not
  -- become a way for one signed-in user to recompute (and read back) another user's rates.
  -- service_role (the edge function) has a null auth.uid() and is allowed through.
  if auth.uid() is not null and auth.uid() <> p_user then
    raise exception 'not_authorized';
  end if;

  with ordered as (
    select qty,
           observed_at,
           lag(qty)         over (order by observed_at) as prev_qty,
           lag(observed_at) over (order by observed_at) as prev_at
      from public.zad_inventory_observations
     where user_id = p_user and item_name = p_item
  ),
  samples as (
    select (prev_qty - qty)
             / (extract(epoch from (observed_at - prev_at)) / 86400.0) as rate
      from ordered
     where prev_qty is not null
       -- qty went UP -> a restock happened; qty flat -> no consumption signal.
       -- Either way this pair yields no sample (never a negative or zero rate).
       and qty < prev_qty
       -- Spec says days_between > 0. A stricter floor of one hour is deliberate: a pair
       -- minutes apart is a correction/double-entry, not a day's consumption, and would
       -- inject an absurd rate (1 unit over 5 min reads as ~288/day) that a median over
       -- only 3 samples cannot absorb.
       and extract(epoch from (observed_at - prev_at)) >= 3600
  )
  select percentile_cont(0.5) within group (order by rate), count(*)
    into v_median, v_samples
    from samples;

  v_samples := coalesce(v_samples, 0);

  if v_samples = 0 then
    return jsonb_build_object('samples', 0, 'avg_daily_qty', null, 'rate_known', false);
  end if;

  insert into public.zad_consumption
    (user_id, item_name, avg_daily_qty, sample_count, rate_known, last_computed_at)
  values
    (p_user, p_item, v_median, v_samples, v_samples >= 3, now())
  on conflict (user_id, item_name) do update
    set avg_daily_qty    = excluded.avg_daily_qty,
        sample_count     = excluded.sample_count,
        rate_known       = excluded.rate_known,
        last_computed_at = excluded.last_computed_at;

  return jsonb_build_object(
    'samples', v_samples,
    'avg_daily_qty', v_median,
    'rate_known', v_samples >= 3
  );
end $$;

-- One call site for "an observation happened": insert + recompute, so no caller can do the
-- first without the second (that split is how Fault B happened in the first place).
create or replace function public.zad_record_observation(
  p_user uuid, p_item text, p_qty numeric, p_source text
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is not null and auth.uid() <> p_user then
    raise exception 'not_authorized';
  end if;

  insert into public.zad_inventory_observations (user_id, item_name, qty, source)
  values (p_user, p_item, p_qty, p_source);

  return public.zad_recompute_consumption(p_user, p_item);
end $$;
