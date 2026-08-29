-- ═══════════════════════════════════════════════════════════════════════════
-- 20260829010000 — حلقة الأهداف: agent_tasks يبقى جزء من هدف طويل المدى.
--
-- الفجوة: agent_tasks كان بيجاوب على "فكرني بكذا الساعة ٩" بس — مفيش أي بنية
-- تخلي العقل يمسك هدف حياة ("وفّر 500 شهرياً"، "سلسلة تسبيحة 30 يوم") ويفككه
-- مهام متكررة ويتابع تقدمها ويعيد التخطيط لو اتخنق. أعمدة اختيارية كلها —
-- المهام القديمة بتفضل شغالة زي ما هي بدون أي backfill.
-- ═══════════════════════════════════════════════════════════════════════════

-- الهدف الأم (سطر واحد في جدول جديد) — العميل بيحطه، والعقل بيديره.
create table if not exists public.agent_goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (char_length(title) <= 200),
  metric text check (char_length(metric) <= 200),          -- "توفير شهري 500"
  target_value numeric,
  current_value numeric not null default 0,
  deadline_date date,
  status text not null default 'active' check (status in ('active','achieved','stalled','cancelled')),
  last_reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, title)
);

create index if not exists idx_agent_goals_active
  on public.agent_goals(user_id, status)
  where status = 'active';

alter table public.agent_goals enable row level security;
drop policy if exists "user_crud_own_agent_goals" on public.agent_goals;
create policy "user_crud_own_agent_goals" on public.agent_goals
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

comment on table public.agent_goals is
  'أهداف حياة طويلة المدى العميل حطها بنفسه — العقل بيفككها مهام agent_tasks ويجاري تقدمها ويبلغ بالتقدم أو الخنق.';

-- ربط المهمة بهدفها + تكرارها + نتيجة آخر تنفيذ.
-- done_with_issue: اتنفذت لكن في رفض/تأكيد ناقص — بتتحسب محاولة مش إنجاز،
-- والtrigger بتاع تقدم الهدف بيتفعل بس على 'done' (حماية من +1 كاذب).
alter table public.agent_tasks
  drop constraint if exists agent_tasks_status_check;
alter table public.agent_tasks
  add constraint agent_tasks_status_check
    check (status in ('pending','running','done','done_with_issue','failed','cancelled')),
  add column if not exists goal_id uuid references public.agent_goals(id) on delete set null,
  add column if not exists recurrence text
    check (recurrence in ('once','daily','weekly','monthly')) default 'once',
  add column if not exists last_run_result text;

create index if not exists idx_agent_tasks_goal
  on public.agent_tasks(goal_id)
  where goal_id is not null;

-- تقدم الهدف بيتحدّث سيرفر-سايد لما مهمة تخلص (نفس نمط process_agent_tasks).
-- current_value بيزيد بمقدار 1 لكل مهمة يومية منجزة — الأدوات الأعلى تضبطه صراحة.
create or replace function public.agent_goal_touch_progress()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.goal_id is not null and new.status = 'done' then
    update agent_goals
      set current_value = current_value + 1,
          last_reviewed_at = now(),
          updated_at = now()
      where id = new.goal_id and status = 'active';
  end if;
  return null;
end;
$$;

drop trigger if exists trg_agent_goal_progress on public.agent_tasks;
create trigger trg_agent_goal_progress
  after update of status on public.agent_tasks
  for each row
  when (new.status = 'done' and (old.status is distinct from 'done'))
  execute function public.agent_goal_touch_progress();
