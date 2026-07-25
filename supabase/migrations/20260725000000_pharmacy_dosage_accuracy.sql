-- Task 17.2.1 — days_left was computed as remaining_quantity / daily_dose_count,
-- silently assuming one dose = one unit. units_per_dose is nullable with NO default:
-- defaulting to 1 would assert every existing medication is one-unit-per-dose, which
-- is exactly the wrong silent guess this migration exists to stop making.
alter table public.zad_pharmacy_items
  add column if not exists units_per_dose numeric,
  add column if not exists qty_confirmed_at timestamptz,
  -- Task 17.2.3 — a malformed dose_times entry used to be dropped silently by the
  -- scheduler with no trace anywhere. This makes that state visible on the item.
  add column if not exists has_invalid_dose_time boolean not null default false;

-- Task 17.2.2 — per-dose history, not just a mutated counter, so retroactive logging
-- and "how many doses did we actually log vs schedule" are both possible. The unique
-- index is what makes tapping the notification button AND the new screen button for
-- the same scheduled dose a no-op the second time, instead of double-decrementing.
create table if not exists public.zad_pharmacy_doses (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  item_id      uuid not null references public.zad_pharmacy_items(id) on delete cascade,
  scheduled_at timestamptz,
  taken_at     timestamptz,
  status       text not null check (status in ('taken','skipped','missed')),
  units        numeric not null default 1,
  created_at   timestamptz not null default now()
);

create unique index if not exists zad_doses_unique
  on public.zad_pharmacy_doses (user_id, item_id, scheduled_at)
  where scheduled_at is not null;

alter table public.zad_pharmacy_doses enable row level security;

create policy "user_own_pharmacy_doses"
  on public.zad_pharmacy_doses
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- ── One-time backfill of units_per_dose from the free-text `dosage` column ──────
-- Deliberately NOT a runtime parser (dosage is free text: "قرصين", "2 أقراص", "5ml",
-- "حبة واحدة", "١ قرص" — parsing it live would be silently wrong, differently, per
-- user). This runs once, here, and anything unmatched stays null on purpose.
-- remaining_quantity itself is never touched — we can't know if an existing value
-- already counts tablets or boxes, and guessing would destroy real data.
with normalized as (
  select id, translate(dosage, '٠١٢٣٤٥٦٧٨٩', '0123456789') as dosage_norm
  from public.zad_pharmacy_items
  where dosage is not null and units_per_dose is null
),
resolved as (
  select id,
    case
      -- volume units (ml/مل) almost certainly don't share a unit with remaining_quantity — leave null
      when dosage_norm ~* '(ml|مل)' then null
      when dosage_norm ~* '(قرصين|حبتين)' then 2
      when dosage_norm ~* '(تلات|ثلاث)' then 3
      when dosage_norm ~* '(قرص واحد|حبة واحدة|^واحد$|^واحدة$)' then 1
      when (regexp_match(dosage_norm, '(\d+)'))[1] is not null
        then (regexp_match(dosage_norm, '(\d+)'))[1]::numeric
      when dosage_norm ~* '(قرص|حبة)' then 1
      else null
    end as units
  from normalized
)
update public.zad_pharmacy_items t
set units_per_dose = r.units
from resolved r
where t.id = r.id and r.units is not null;
