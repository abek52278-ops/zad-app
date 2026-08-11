-- Server-side observability for replies that promise an action without a tool call.
create table if not exists public.agent_drift_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  run_id uuid references public.zad_brain_runs(id) on delete set null,
  source text not null,
  message text not null,
  matched_patterns text[] not null default '{}',
  tool_calls text[] not null default '{}',
  created_at timestamptz not null default now()
);

create index if not exists idx_agent_drift_events_user_time
  on public.agent_drift_events(user_id, created_at desc);

alter table public.agent_drift_events enable row level security;
drop policy if exists "user_reads_own_agent_drift_events" on public.agent_drift_events;
create policy "user_reads_own_agent_drift_events"
  on public.agent_drift_events for select using (auth.uid() = user_id);
