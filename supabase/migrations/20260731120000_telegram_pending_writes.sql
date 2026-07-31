-- Telegram expense logging (Phase B4 v3). Edge functions are stateless, so a parsed
-- spend intent has to survive the round trip between "user typed صرفت ٥٠ بقالة" and
-- "user pressed تأكيد". Telegram's callback_data is capped at 64 bytes, which can't
-- carry amount+title+category safely — so the intent is parked here and the button
-- carries only this row's id.
--
-- Nothing here is ever trusted as an identity: the row stores the user_id that was
-- already resolved through telegram_bindings, and the confirm handler re-checks that
-- the confirming chat_id still maps to that same user before writing anything.
create table if not exists public.telegram_pending_writes (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  chat_id     bigint not null,
  txn_kind    text not null check (txn_kind in ('expense', 'income')),
  amount      double precision not null check (amount > 0),
  title       text not null,
  category    text,
  confidence  real,
  status      text not null default 'pending' check (status in ('pending', 'confirmed', 'cancelled')),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default now() + interval '30 minutes'
);

create index if not exists idx_telegram_pending_writes_user on public.telegram_pending_writes(user_id, status);

alter table public.telegram_pending_writes enable row level security;

-- المستخدم يشوف طلباته هو بس. الـ edge function (service role) بتتخطى RLS زي باقي
-- الدوال، وبتعمل التحقق بنفسها عن طريق telegram_bindings.
create policy "user_own_telegram_pending_write" on public.telegram_pending_writes for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

comment on table public.telegram_pending_writes is
  'Parsed-but-unconfirmed spend intents from the Telegram bot. A row is only turned into a zad_transactions row after the user presses the confirm button, and only if their chat_id still resolves to this user_id. Expires after 30 minutes.';
