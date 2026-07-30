-- Task 26 (PRODUCT_PLAN.md) — committed obligations and the "available" number.
-- available = remaining - committed, where committed sums confirmed+active obligations
-- and active subscriptions due before the cycle end (Task 25's cycleEnd). Unconfirmed
-- auto-detected rows (confirmed=false) never enter that sum — see zad-brain's
-- confirm_obligation tool, the only path that flips confirmed to true.
create table if not exists public.zad_obligations (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  title        text not null,
  amount       numeric not null check (amount > 0),
  kind         text not null check (kind in
                 ('rent','installment','debt','tuition','utility','other')),
  due_day      int check (due_day between 1 and 31),
  due_date     date,                      -- for one-off obligations
  recurrence   text not null default 'monthly'
               check (recurrence in ('monthly','quarterly','yearly','once')),
  auto_detected boolean not null default false,
  confirmed    boolean not null default false,
  active       boolean not null default true,
  created_at   timestamptz not null default now()
);

create index if not exists idx_zad_obligations_user on public.zad_obligations(user_id);

alter table public.zad_obligations enable row level security;
drop policy if exists "user_own_obligations" on public.zad_obligations;
create policy "user_own_obligations" on public.zad_obligations for all using (auth.uid() = user_id);

comment on table public.zad_obligations is
  'Recurring/one-off committed spend (rent, installments, debt...) — feeds the "available" figure (Task 26). Subscriptions stay in zad_subscriptions, not duplicated here.';
comment on column public.zad_obligations.confirmed is
  'Auto-detected rows start false and must never count toward "available" until the user confirms via zad-brain''s confirm_obligation tool.';
