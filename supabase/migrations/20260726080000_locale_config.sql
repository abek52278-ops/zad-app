-- Task 20 — dedupe parameters as per-country data, not hardcoded constants.
-- Values match what's already live in TxDeduplicator (commit f5139a5): 36h window,
-- 5% tolerance. This table is what makes them tunable per market without a release —
-- the constants stay as the safe fallback when this table is unreachable (offline,
-- cold start before the client's ever fetched it), not the primary source anymore.
create table if not exists public.zad_locale_config (
  country              text primary key,
  currency             text not null,
  dedupe_window_hours  int not null default 36,
  amount_tolerance_pct numeric not null default 5,
  updated_at           timestamptz not null default now()
);

insert into public.zad_locale_config (country, currency) values
  ('EG','EGP'), ('SA','SAR'), ('TR','TRY')
on conflict (country) do nothing;

alter table public.zad_locale_config enable row level security;
-- Read-only reference data, not user-scoped — every authenticated client needs to
-- read its own market's row. No write policy: only changed via migration/dashboard.
create policy "authenticated_read_locale_config" on public.zad_locale_config
  for select using (auth.role() = 'authenticated');
