-- ═══════════════════════════════════════════════════════════
-- 20260824130000 — zad_skills: مهارات متعلمة منفصلة عن الذاكرة (نمط Hermes skills).
--
-- ليه جدول منفصل بدل scope في zad_memory؟
-- - zad_memory ملاحظات حقائق/أحداث عن العميل، بتُدمج بالتشابه (similarity merge).
-- - المهارات إجراءات نجح فيها العقل ("أسلوب تذكير X ردّ عليه") — ليها دورة حياة مختلفة:
--   evidence_count بيزيد بالنجاح مش بالتكرار، وليها last_used_at للترتيب، وبتقدر تتقافي.
-- ═══════════════════════════════════════════════════════════

create table if not exists public.zad_skills (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    -- slug ثابت يمنع التكرار: نفس المهارة بتتقوّى بدل صف جديد
    skill_key text not null,
    note text not null check (char_length(note) between 10 and 200),
    evidence_count integer not null default 1,
    confidence real not null default 0.5,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_used_at timestamptz,
    retired_at timestamptz,          -- مهارة بقت غلط → تقافى مش تتحذف (تاريخ تعلّم)
    unique (user_id, skill_key)
);

alter table public.zad_skills enable row level security;
drop policy if exists "user_own_skills" on zad_skills;
create policy "user_own_skills" on zad_skills for all using (auth.uid() = user_id);

create index if not exists idx_zad_skills_user_active
  on public.zad_skills (user_id, evidence_count desc)
  where retired_at is null;

comment on table public.zad_skills is
  'مهارات متعلمة: إجراءات نجح فيها العقل مع العميل مرتين+. تُحمّل في برومبت المحادثة (حتى ٨) وتُقوى بالنجاح. التقاعد بـ retired_at مش الحذف.';

-- upsert بالمفتاح: موجودة → قوّيها؛ جديدة → اعملها. يرجع 'inserted'|'strengthened'.
create or replace function public.zad_skill_upsert(
  p_user uuid, p_key text, p_note text, p_conf real default 0.5
) returns text language plpgsql security definer set search_path = public as $$
begin
  insert into public.zad_skills (user_id, skill_key, note, confidence)
  values (p_user, p_key, p_note, least(1.0, greatest(0.0, coalesce(p_conf, 0.5))))
  on conflict (user_id, skill_key) do update
    set note = excluded.note,
        evidence_count = zad_skills.evidence_count + 1,
        confidence = least(1.0, zad_skills.confidence + 0.1),
        retired_at = null,
        updated_at = now();
  if exists (select 1 from public.zad_skills where user_id = p_user and skill_key = p_key and evidence_count > 1) then
    return 'strengthened';
  end if;
  return 'inserted';
end;
$$;

-- تعليم استخدامها (اختياري): آخر مرة اتنفذت فعلاً
create or replace function public.zad_skill_touch(p_user uuid, p_key text)
returns void language sql security definer set search_path = public as $$
  update public.zad_skills set last_used_at = now() where user_id = p_user and skill_key = p_key;
$$;

-- ترحيل: أي ملاحظات scope='skill' موجودة في zad_memory تنتقل هنا مرة واحدة
insert into public.zad_skills (user_id, skill_key, note, evidence_count, confidence, created_at, updated_at)
select m.user_id,
       'migrated_' || substr(m.id::text, 1, 8),
       m.note,
       greatest(m.evidence_count, 1),
       m.confidence,
       coalesce(m.last_seen, now()),
       now()
from public.zad_memory m
where m.scope = 'skill'
on conflict (user_id, skill_key) do nothing;
