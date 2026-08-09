-- ═══════════════════════════════════════════════════════════════════════════
-- agent_tasks — طابور مهام مؤجلة/مجدولة (W8، السبيك Phase 5: "requests like
-- 'راجعلي كل مصاريف الأسبوع وابعتلي تقرير الساعة ٩' become a stored task a
-- scheduled Edge Function picks up and executes later, then delivers the result
-- via notification — not just an immediate chat reply").
--
-- الفرق عن zad_brain_queue الموجود بالفعل: zad_brain_queue إعادة محاولة تقنية
-- (نداء موديل فشل، حاول تاني بعدين) — مش مرئي للعميل ولا هو اللي طلبه. agent_tasks
-- طلب صريح من العميل نفسه ("فكرني/راجعلي X الساعة ٩")، بيتنفذ لما وقته يجي، والنتيجة
-- بتوصله بإشعار حقيقي، مش نص شات محدش هيشوفه.
-- ═══════════════════════════════════════════════════════════════════════════

create table if not exists public.agent_tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  task_description text not null,
  status text not null default 'pending' check (status in ('pending','running','done','failed','cancelled')),
  scheduled_for timestamptz not null default now(),
  result text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_agent_tasks_due
  on public.agent_tasks(status, scheduled_for)
  where status = 'pending';
create index if not exists idx_agent_tasks_user_time
  on public.agent_tasks(user_id, created_at desc);

alter table public.agent_tasks enable row level security;

-- العميل بيشوف مهامه بس. الإنشاء عن طريق أداة الوكيل schedule_task (سيرفر-سايد،
-- service_role بيتخطى RLS) — مفيش INSERT policy للعميل نفسه عن قصد، عشان مفيش
-- مسار يخلي الكلاينت يزرع مهمة تتنفذ باسمه من غير ما تعدي على أدوات الوكيل
-- وتحققها (validateScheduleTask). التحديث (إلغاء) كمان سيرفر-سايد بس دلوقتي.
drop policy if exists "user_reads_own_agent_tasks" on public.agent_tasks;
create policy "user_reads_own_agent_tasks" on public.agent_tasks
  for select using (auth.uid() = user_id);

comment on table public.agent_tasks is
  'مهام مؤجلة طلبها العميل صراحة ("فكرني بكذا الساعة تسعة") — بيتنفذوا بواسطة pg_cron + zad-brain action=process_agent_tasks، والنتيجة بتوصل كإشعار app_notifications.';

-- ═══════════════════════════════════════════════════════════════════════════
-- pg_cron: كل ٥ دقايق بيندي zad-brain?action=process_agent_tasks. نفس نمط
-- الجوبات الموجودة بالفعل (telegram-checkin-daily/telegram-subscription-alerts-daily)
-- — سيكريت عشوائي في هيدر مخصص، مش الـ JWT الافتراضي، عشان محدش برّه يقدر ينادي
-- المسار ده. نفس مبدأ الأمان الموثّق في تلك الملفات بالظبط: مش الـ service-role key
-- الحقيقي، ومش قابل لإعادة الاستخدام في مسار تاني (X-Agent-Tasks-Cron-Secret بيتحقق
-- منه هنا بس، مش ZAD_AGENT_TASKS_CRON_SECRET نفسه بيفتح أي حاجة تانية). لازم يتطابق
-- مع ZAD_AGENT_TASKS_CRON_SECRET المضبوط كـ Supabase Function secret.
select cron.schedule(
  'agent-tasks-processor',
  '*/5 * * * *',
  $$
  select net.http_post(
      url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-brain',
      headers:='{"Content-Type":"application/json","X-Agent-Tasks-Cron-Secret":"92fae868116c1da855afb82be6e41ba85e789b4cf2810395bcaf3b405169eb51"}'::jsonb,
      body:='{"action":"process_agent_tasks"}'::jsonb,
      timeout_milliseconds:=55000
    ) as request_id;
  $$
);
