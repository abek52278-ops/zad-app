-- Telegram Smart Medication Parsing: same round-trip problem as
-- telegram_pending_writes (edge functions are stateless, and callback_data is capped
-- at 64 bytes so it can't carry name+dosage+dose_times), same fix — park the parsed
-- medication here and let the confirm button carry only this row's id.
create table if not exists public.telegram_pending_pharmacy (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users(id) on delete cascade,
  chat_id           bigint not null,
  name              text not null,
  dosage            text,
  daily_dose_count  integer not null default 1,
  dose_times        text,
  unit              text not null default 'قرص',
  quantity          integer not null default 1,
  category          text not null default 'عام',
  status            text not null default 'pending' check (status in ('pending', 'confirmed', 'cancelled')),
  created_at        timestamptz not null default now(),
  expires_at        timestamptz not null default now() + interval '30 minutes'
);

create index if not exists idx_telegram_pending_pharmacy_user on public.telegram_pending_pharmacy(user_id, status);

alter table public.telegram_pending_pharmacy enable row level security;

create policy "user_own_telegram_pending_pharmacy" on public.telegram_pending_pharmacy for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

comment on table public.telegram_pending_pharmacy is
  'Parsed-but-unconfirmed new medications from the Telegram bot (Smart Medication Parsing). A row is only turned into a zad_pharmacy_items row after the user presses the confirm button, and only if their chat_id still resolves to this user_id. Expires after 30 minutes.';
