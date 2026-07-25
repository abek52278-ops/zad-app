-- ZAD Brain: background LLM analysis layer.
-- Tables the zad-brain Edge Function reads/writes, plus Task 16 reliability
-- infrastructure (retry queue, run audit log, memory dedup).

create extension if not exists pg_trgm;

-- ═══════════════════════════════════════════════════════════
-- zad_insights: stored decisions the brain surfaces to the UI.
-- One bell, one list — screens read this, never the LLM directly.
-- ═══════════════════════════════════════════════════════════
create table if not exists public.zad_insights (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  kind         text not null check (kind in ('insight','question','alert')),
  surface      text not null check (surface in ('home_card','bell','voice')),
  priority     text not null default 'normal' check (priority in ('normal','critical')),
  title        text not null,
  body         text not null,
  dedupe_key   text not null,
  status       text not null default 'pending' check (status in ('pending','seen','acted','dismissed')),
  action_type  text,               -- kind='question' only: 'number' | 'yes_no' | 'camera'
  about_item   text,               -- inventory item name the question concerns, if any
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

-- نفس dedupe_key متكرر لنفس المستخدم = نفس الرؤية، مش رؤية جديدة (يمنع تكرار التنبيه)
create unique index if not exists idx_zad_insights_user_dedupe on public.zad_insights(user_id, dedupe_key);
create index if not exists idx_zad_insights_user_status on public.zad_insights(user_id, status, created_at desc);

alter table public.zad_insights enable row level security;
create policy "user_own_insights" on public.zad_insights for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ═══════════════════════════════════════════════════════════
-- zad_memory: ما تعلّمه العقل عن الأسرة — طويل الأجل، مُوحَّد بالتشابه النصي
-- (trigram) بدل ما يتكرر بصيغ مختلفة لنفس الملاحظة
-- ═══════════════════════════════════════════════════════════
create table if not exists public.zad_memory (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  scope          text not null default 'general',
  note           text not null,
  confidence     real not null default 0.5,
  evidence_count int not null default 1,
  last_seen      timestamptz not null default now(),
  created_at     timestamptz not null default now()
);
create index if not exists idx_zad_memory_note_trgm on public.zad_memory using gin (note gin_trgm_ops);
create index if not exists idx_zad_memory_user_scope on public.zad_memory(user_id, scope);

alter table public.zad_memory enable row level security;
create policy "user_own_memory" on public.zad_memory for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ═══════════════════════════════════════════════════════════
-- zad_consumption: معدل استهلاك متعلّم لكل صنف — بيتكتب من ZadFacts (Kotlin)،
-- بيتقرا من العقل عشان يعرف يسأل بس عن الأصناف اللي معدلها لسه مش معروف
-- ═══════════════════════════════════════════════════════════
create table if not exists public.zad_consumption (
  user_id           uuid not null references auth.users(id) on delete cascade,
  item_name         text not null,
  avg_daily_qty     double precision not null default 0,
  sample_count      int not null default 0,
  rate_known        boolean not null default false,
  last_computed_at  timestamptz not null default now(),
  primary key (user_id, item_name)
);

alter table public.zad_consumption enable row level security;
create policy "user_own_consumption" on public.zad_consumption for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ═══════════════════════════════════════════════════════════
-- zad_brain_runs: سجل تدقيق — كل استدعاء للعقل، إيه اللي غيّره، تكلفة التوكنز،
-- و(Task 16) رفض التحقق لمراجعة أسبوعية
-- ═══════════════════════════════════════════════════════════
create table if not exists public.zad_brain_runs (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  trigger        text not null check (trigger in ('daily','event','chat')),
  started_at     timestamptz not null default now(),
  finished_at    timestamptz,
  input_tokens   int not null default 0,
  output_tokens  int not null default 0,
  mutations      jsonb not null default '[]'::jsonb,
  rejections     jsonb not null default '[]'::jsonb,
  status         text not null default 'running' check (status in ('running','success','failed','queued')),
  error          text
);
create index if not exists idx_zad_brain_runs_user_time on public.zad_brain_runs(user_id, started_at desc);

alter table public.zad_brain_runs enable row level security;
create policy "user_own_brain_runs" on public.zad_brain_runs for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ═══════════════════════════════════════════════════════════
-- zad_brain_queue (Task 16.3): تشغيلات فشلت بعد كل المحاولات، مستنية إعادة محاولة
-- ═══════════════════════════════════════════════════════════
create table if not exists public.zad_brain_queue (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  trigger      text not null,
  user_message text,
  attempts     int not null default 0,
  last_error   text,
  created_at   timestamptz not null default now()
);

alter table public.zad_brain_queue enable row level security;
create policy "user_own_brain_queue" on public.zad_brain_queue for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ═══════════════════════════════════════════════════════════
-- zad_memory_upsert (Task 16.2): دمج بالتشابه بدل insert أعمى — لو الملاحظة الجديدة
-- ≥0.6 شبه ملاحظة موجودة في نفس الـ scope، بيقوّي الصف القديم بدل ما يضيف واحد جديد
-- ═══════════════════════════════════════════════════════════
create or replace function public.zad_memory_upsert(
  p_user uuid, p_scope text, p_note text, p_conf real default 0.5
) returns text language plpgsql security definer as $$
declare v_id uuid; v_sim real;
begin
  select id, similarity(note, p_note) into v_id, v_sim
    from public.zad_memory
   where user_id = p_user and scope = p_scope
   order by similarity(note, p_note) desc limit 1;

  if v_id is not null and v_sim >= 0.6 then
    update public.zad_memory
       set evidence_count = evidence_count + 1,
           confidence = least(1.0, confidence + 0.1),
           last_seen = now(),
           note = p_note
     where id = v_id;
    return 'strengthened';
  end if;

  insert into public.zad_memory(user_id, scope, note, confidence)
  values (p_user, p_scope, p_note, coalesce(p_conf, 0.5));
  return 'inserted';
end $$;

-- تقليم الذاكرة: أعلى 60 صف للمستخدم بـ confidence*evidence_count — استدعاء دالة محلية
-- بحتة، بلا أي سر أو استدعاء شبكة، فآمن نجدولها فعلياً هنا (خلاف كرون استدعاء الـ Edge
-- Function نفسها — ده محتاج مفتاح service role في Vault، مش هفبركه، انظر تقرير التنفيذ)
create or replace function public.zad_memory_prune() returns void language plpgsql security definer as $$
begin
  delete from public.zad_memory m
  where id in (
    select id from (
      select id, row_number() over (
        partition by user_id order by confidence * evidence_count desc, last_seen desc
      ) as rn
      from public.zad_memory
    ) ranked
    where rn > 60
  );
end $$;

do $$
begin
  if not exists (select 1 from cron.job where jobname = 'zad-memory-prune-weekly') then
    perform cron.schedule(
      'zad-memory-prune-weekly',
      '0 3 * * 0',
      $cron$select public.zad_memory_prune()$cron$
    );
  end if;
end $$;

-- ═══════════════════════════════════════════════════════════
-- zad_brain_self_review: العقل يشوف نتيجة كلامه القديم — مش بس سلوك العميل.
-- تحذيرات سرعة الصرف أول الشهر: اتأكدت لو الشهر خلص فوق الميزانية، غلط لو خلص تحتها.
-- تنبيهات نواقص المخزون: اتأكدت لو الصنف اتشرى بعدها، غلط لو مفيش شراء وخلصت مهلة كافية.
-- buildSnapshot في index.ts بينادي الدالة دي ويحقن نتيجتها كحقل "self_review" —
-- ده اللي بيخلي العقل يقول "تحذيراتي دي طلعت غلط ٣ مرات" بدل ما يكرر نفس الغلطة.
-- ═══════════════════════════════════════════════════════════
create or replace function public.zad_brain_self_review(p_user uuid)
returns jsonb language sql stable as $$
  with velocity_checks as (
    select i.id, extract(year from i.created_at)::int as yr, extract(month from i.created_at)::int as mo
    from public.zad_insights i
    where i.user_id = p_user and i.dedupe_key like 'velocity_%'
      and extract(day from i.created_at) <= 10
      and i.created_at < date_trunc('month', now())
      and i.created_at >= now() - interval '75 days'
  ),
  velocity_outcomes as (
    select v.id,
      case when coalesce((
        select sum(t.amount) from public.zad_transactions t
        where t.user_id = p_user and t.is_expense
          and date_trunc('month', t.created_at) = make_date(v.yr, v.mo, 1)
      ), 0) <= coalesce((select budget from public.zad_users where id = p_user), 0)
      then 'incorrect' else 'correct' end as verdict
    from velocity_checks v
  ),
  low_stock_checks as (
    select i.id, i.about_item, i.created_at
    from public.zad_insights i
    where i.user_id = p_user and i.dedupe_key like 'low_stock_%' and i.about_item is not null
      and i.created_at between now() - interval '21 days' and now() - interval '5 days'
  ),
  low_stock_outcomes as (
    select c.id,
      case when exists (
        select 1 from public.zad_inventory inv
        where inv.user_id = p_user and inv.item_name = c.about_item and inv.created_at > c.created_at
      ) then 'correct' else 'incorrect' end as verdict
    from low_stock_checks c
  )
  select jsonb_build_object(
    'velocity_warnings', jsonb_build_object(
      'correct', (select count(*) from velocity_outcomes where verdict = 'correct'),
      'incorrect', (select count(*) from velocity_outcomes where verdict = 'incorrect')
    ),
    'low_stock_warnings', jsonb_build_object(
      'correct', (select count(*) from low_stock_outcomes where verdict = 'correct'),
      'incorrect', (select count(*) from low_stock_outcomes where verdict = 'incorrect')
    )
  );
$$;

-- ═══════════════════════════════════════════════════════════
-- VERIFICATION QUERY (Task 8a) — شغّلها بعد الـ migration مباشرة.
-- كل جدول zad_* لازم rls_enabled = true وعنده policy واحدة على الأقل.
-- ═══════════════════════════════════════════════════════════
select
  t.tablename,
  t.rowsecurity as rls_enabled,
  count(p.policyname) as policy_count
from pg_tables t
left join pg_policies p on p.tablename = t.tablename and p.schemaname = 'public'
where t.schemaname = 'public' and t.tablename like 'zad_%'
group by t.tablename, t.rowsecurity
order by t.tablename;
