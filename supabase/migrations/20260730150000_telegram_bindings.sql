-- Phase B4 (PRODUCT_PLAN.md) — Telegram bot infrastructure. Per EPIC_1_4.md's own
-- warning: "a chat_id is never an identity" — this table is the one-time binding code
-- that turns a chat_id into a proven link to a zad user, not the client trusting
-- whatever chat_id shows up in a webhook payload.
--
-- Named telegram_bindings (not zad_-prefixed like every other table in this schema) per
-- explicit user instruction — flagging the inconsistency here rather than silently
-- deviating from the app's own convention without a note.
create table if not exists public.telegram_bindings (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users(id) on delete cascade,
  chat_id          bigint,
  binding_code     text not null,
  code_expires_at  timestamptz not null,
  bound_at         timestamptz,
  created_at       timestamptz not null default now()
);

create unique index if not exists idx_telegram_bindings_code on public.telegram_bindings(binding_code);
-- chat_id واحد مربوط بمستخدم واحد بس، ومستخدم واحد مربوط بـ chat واحد بس وقت من الأوقات
create unique index if not exists idx_telegram_bindings_chat_bound
  on public.telegram_bindings(chat_id) where bound_at is not null;
create unique index if not exists idx_telegram_bindings_user_bound
  on public.telegram_bindings(user_id) where bound_at is not null;

alter table public.telegram_bindings enable row level security;
-- المستخدم بس يشوف/يعمل كود لحسابه هو — الـ edge function (service role) بتدور بـ
-- binding_code/chat_id من غير معرفة user_id الأول، فبتتخطى RLS زي zad-brain بالظبط.
create policy "user_own_telegram_binding" on public.telegram_bindings for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

comment on table public.telegram_bindings is
  'One-time binding code linking a zad user_id to a Telegram chat_id. bound_at null = code generated but not yet used. chat_id is only trustworthy once bound_at is set — see zad-telegram-bot edge function.';
