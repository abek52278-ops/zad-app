-- Stage 2 — Android NotificationListenerService ingress.
--
-- The listener can receive the same bank notification more than once (service reconnect,
-- SMS app repost, notification style expansion). This table is the server-side idempotency
-- guard before zad-brain writes a transaction.

create table if not exists public.zad_notification_ingest_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  dedupe_hash text not null,
  package_name text not null,
  title text,
  body text not null,
  client_classification text,
  status text not null default 'received'
    check (status in ('received','logged','ignored','ambiguous','rejected')),
  rejection_reason text,
  transaction_id uuid references public.zad_transactions(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, dedupe_hash)
);

create index if not exists idx_zad_notification_ingest_events_user_time
  on public.zad_notification_ingest_events(user_id, created_at desc);

alter table public.zad_notification_ingest_events enable row level security;

drop policy if exists "user_reads_own_notification_ingest_events" on public.zad_notification_ingest_events;
create policy "user_reads_own_notification_ingest_events" on public.zad_notification_ingest_events
  for select using (auth.uid() = user_id);

comment on table public.zad_notification_ingest_events is
  'Server-side idempotency and audit staging for Android bank notification ingestion.';
