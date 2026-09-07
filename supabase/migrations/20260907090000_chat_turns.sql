-- ── zad_chat_turns — تاريخ محادثة تليجرام ─────────────────────────────────────
--
-- zad-brain.handleAgentTurn بيدعم تاريخ المحادثة من زمان: بيقرا body.history،
-- بيتحقق من الأدوار، وبياخد آخر ٨ لفات. تطبيق أندرويد بيبعتها من Room المحلي
-- (ZadAiRepository.kt). تليجرام **مالوش تخزين محلي خالص**، فكان بيبعت الرسالة
-- الحالية لوحدها — والنتيجة موثّقة في محادثة حقيقية 2026-09-06:
--
--   المستخدم: هلا اخصم 50 جنيه
--   البوت:   جاهزة أخصم 50 جنيه. تحب أسجلها بوصف إيه؟
--   المستخدم: مصروف
--   البوت:   إيه المصروف اللي عايز تسجله؟ (قولي المبلغ والوصف)   ← المبلغ ضاع
--
-- البوت مش "بينسى" — الرسالة الأولى عمرها ما وصلت العقل. الجدول ده هو المكان
-- الوحيد الناقص في السلسلة؛ العقد مع العقل ما اتغيّرش.

create table if not exists public.zad_chat_turns (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  -- نفس الأدوار اللي handleAgentTurn بيقبلها؛ أي حاجة تانية بيرميها بصمت،
  -- فالقيد هنا بيمنعها من الدخول أصلاً بدل ما تتخزن وتتجاهل بعدين.
  role       text not null check (role in ('user','assistant')),
  text       text not null check (char_length(text) <= 4000),
  created_at timestamptz not null default now()
);

-- الاستعلام الوحيد: آخر ٨ لفات لمستخدم واحد.
create index if not exists idx_chat_turns_recent
  on public.zad_chat_turns (user_id, created_at desc);

alter table public.zad_chat_turns enable row level security;

-- (select auth.uid()) مش auth.uid() — نفس تصحيح 20260905140000_rls_initplan:
-- النداء المباشر بيتقيّم لكل صف، واللفّة بتخليه initplan يتقيّم مرة واحدة.
create policy "own chat turns select" on public.zad_chat_turns
  for select using ((select auth.uid()) = user_id);
create policy "own chat turns insert" on public.zad_chat_turns
  for insert with check ((select auth.uid()) = user_id);
create policy "own chat turns delete" on public.zad_chat_turns
  for delete using ((select auth.uid()) = user_id);

-- ── التنظيف ──────────────────────────────────────────────────────────────────
-- العقل بيقرا آخر ٨ لفات وبس، فأي حاجة أقدم من كده مالهاش مستهلك. من غير تنظيف
-- الجدول بيكبر بلا سقف على مستخدم بيتكلم كل يوم.
--
-- التقليم بيحصل عند الكتابة (trigger) مش بـ pg_cron: كده الحجم محدود لكل مستخدم
-- بشكل مؤكد ومن غير ما نعتمد على جدولة ممكن توقف، والتكلفة سطر واحد لكل كتابة.
create or replace function public.zad_chat_turns_trim()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.zad_chat_turns
  where user_id = new.user_id
    and id not in (
      select id from public.zad_chat_turns
      where user_id = new.user_id
      order by created_at desc
      limit 40
    );
  return null;
end;
$$;

-- ٤٠ مش ٨: العقل بيقرا ٨، والهامش بيسيب مجال لتصحيح/إعادة قراءة من غير ما
-- الجدول يكبر. after-trigger عشان الصف الجديد يكون داخل العد.
drop trigger if exists trg_chat_turns_trim on public.zad_chat_turns;
create trigger trg_chat_turns_trim
  after insert on public.zad_chat_turns
  for each row execute function public.zad_chat_turns_trim();

comment on table public.zad_chat_turns is
  'تاريخ محادثة تليجرام — المصدر الوحيد لـ body.history في مسار البوت. التطبيق بيقرا من Room بدلاً منه.';
