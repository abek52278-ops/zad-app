-- ═══════════════════════════════════════════════════════════
-- 20260824160000 — الذاكرة الدلالية: embeddings للـ zad_memory.
--
-- الفكرة: الاسترجاع الحالي keyword-based (rankMemoryForMessage) بيفوّت الملاحظات
-- اللي معناها قريب لكن كلماتها مختلفة ("بيحب القهوة" vs "قهوتنا الصبح"). عمود
-- embedding (768-d، موديل gemini text-embedding-004) + تطابق دلالي في الاسترجاع.
--
-- التوليد: zad-brain نفسه يولّد الـ embedding عند الكتابة (remember/learn_skill)
-- عبر Gemini embeddings API بنفس مفاتيح callModel — مفيش خدمة جديدة ولا cron جديد.
-- الملاحظات القديمة تتولّد lazily أول ما تُقرا ومالوش embedding (backfill on read).
--
-- الأمان: العمود nullable — فشل توليد embedding مايكسرش الكتابة أبداً (fail-open).
-- ═══════════════════════════════════════════════════════════

alter table public.zad_memory
  add column if not exists embedding vector(768);

create index if not exists idx_zad_memory_embedding
  on public.zad_memory using ivfflat (embedding vector_cosine_ops)
  with (lists = 100);

comment on column public.zad_memory.embedding is
  '768-d embedding لنص الملاحظة (gemini text-embedding-004). null = لسه متولدش؛ الاسترجاع الدلالي بيتخطاه وبيرجع للكلمات المفتاحية.';

-- استرجاع هجين: نصف النتيجة من التطابق الدلالي (لو فيه embeddings) ونصفها من
-- الكلمات المفتاحية الموجودة. بيرجع ids مرتبة — الكلاينت يجيب النصوص بنفسه.
create or replace function public.zad_memory_semantic_search(
  p_user uuid,
  p_query_embedding vector(768),
  p_limit int default 12
)
returns table (id uuid, note text, scope text, confidence real, evidence_count integer, similarity real)
language sql security definer set search_path = public stable as $$
  select m.id, m.note, m.scope, m.confidence, m.evidence_count,
         1 - (m.embedding <=> p_query_embedding) as similarity
    from public.zad_memory m
    where m.user_id = p_user and m.embedding is not null
    order by m.embedding <=> p_query_embedding
    limit p_limit;
$$;

revoke execute on function public.zad_memory_semantic_search(uuid, vector(768), int)
  from public, anon, authenticated;

-- ربط embedding بملاحظة (بالمطابقة النصية — نفس note اللي اتكتبت للتو)
create or replace function public.zad_memory_set_embedding(
  p_user uuid, p_note text, p_vec vector(768)
)
returns void language sql security definer set search_path = public as $$
  update public.zad_memory
    set embedding = p_vec
    where user_id = p_user and note = p_note and embedding is null;
$$;

revoke execute on function public.zad_memory_set_embedding(uuid, text, vector(768))
  from public, anon, authenticated;
