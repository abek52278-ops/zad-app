-- FCM device tokens — الوعي اللحظي للأيدجنت.
-- كل جهاز عندو توكن واحد؛ upsert على (user_id, token) عشان الجهاز نفسه ميكررش صفوف.
-- Token unique عشان مايتبعتش إشعار مرتين لو نفس الجهاز اتنقل بين حسابين.

create table if not exists public.zad_fcm_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  token text not null unique,
  platform text not null default 'android',
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists idx_zad_fcm_tokens_user on public.zad_fcm_tokens(user_id);

alter table public.zad_fcm_tokens enable row level security;

-- العميل يكتب/يحذف توكنه هو بس (هويته من JWT) — والتوكِن نص حساس فممنوع القراءة الجماعية.
create policy "fcm_tokens_own_write" on public.zad_fcm_tokens
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- القراءة محصورة بمالك التوكن (السيرفر بيبص بـ service-role فمش محتاج policy له).
create policy "fcm_tokens_own_read" on public.zad_fcm_tokens
  for select using (auth.uid() = user_id);
