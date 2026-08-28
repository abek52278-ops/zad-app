-- ── zad_agent_messages — صندوق بريد الأيدجنتس (Phase 3: cross-agent comms) ──────────
-- كل أداة بتتنفذ بنجاح بتكتب سطر تقرير هنا، والعقل بيقرا غير المقروء قبل كل رد.
-- ده اللي يخلي العقل "واعي" بشغل أيدجنتته بين فتحتين التطبيق (نمط Hermes).

create table if not exists public.zad_agent_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  sender text not null check (sender in ('finance','pantry','pharmacy','family','home','brain')),
  subject text not null check (char_length(subject) <= 200),
  detail text check (detail is null or char_length(detail) <= 500),
  read_by_brain boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_agent_messages_unread
  on public.zad_agent_messages (user_id, read_by_brain, created_at desc);

alter table public.zad_agent_messages enable row level security;

create policy "own agent mail select" on public.zad_agent_messages
  for select using (auth.uid() = user_id);
create policy "agent sender insert" on public.zad_agent_messages
  for insert with check (auth.uid() = user_id);
create policy "agent mail mark read" on public.zad_agent_messages
  for update using (auth.uid() = user_id);

-- منع السبام: أقصى 40 تقرير/يوم/مستخدم (الأيدجنتس بتتكلم مش بتغرق)
create index if not exists idx_agent_messages_created on public.zad_agent_messages (user_id, created_at);