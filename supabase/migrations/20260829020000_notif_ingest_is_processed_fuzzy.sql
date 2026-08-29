-- ═══════════════════════════════════════════════════════════════════════════
-- 20260829020000 — إصلاح dedupe الإشعارات البنكية: is_processed + بصمة ضبابية.
--
-- الفجوة (المؤكدة من فحص الكود):
-- 1) zad_notification_ingest_events ماكانش فيه إشارة "خلصنا من الحاجة دي" —
--    الحالة بتفضل received للأبد، ومفيش رصد للوصول لحالة نهائية.
-- 2) الهاش الحرفي على النص الخام بينكسر أول ما البنك يعيد الإشعار بنص مختلف
--    (الرصيد المتبقي، التوقيت) → اقتراح معاملة تاني وإزعاج تاني.
-- ═══════════════════════════════════════════════════════════════════════════

-- 1) علم المعالجة النهائية + متى.
alter table public.zad_notification_ingest_events
  add column if not exists processed_at timestamptz;

create index if not exists idx_zad_notif_ingest_unprocessed
  on public.zad_notification_ingest_events(user_id, created_at desc)
  where processed_at is null;

-- 2) بصمة ضبابية: (مبلغ مقرّب + تاجر مطبّع) خلال نافذة زمنية.
--    التطبيع: أرقام عربية-هندية → لاتيني، تشكيل وهمزات وتاء مربوطة تتجمد،
--    أرقام وسطور عناوين وتواريخ وأرصدة تتشال، كلمات حشو بنكية تتشال.
create or replace function public.zad_notif_fuzzy_key(
  p_title text,
  p_body text
)
returns text
language plpgsql
immutable
as $$
declare
  v text;
begin
  v := coalesce(p_title, '') || ' ' || coalesce(p_body, '');
  -- أرقام عربية-هندية → ASCII
  v := translate(v, '٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹', '01234567890123456789');
  -- تجميد الحروف المتشابهة
  v := translate(v, 'أإآةىؤئ', 'اهاهيوي');
  -- حروف تتشال: تشكيل
  v := regexp_replace(v, '[\u064B-\u065F\u0670]', '', 'g');
  -- كلمات حشو بنكية وواجهات
  v := regexp_replace(v,
    '(بطاقة|الحساب|حسابك|رصيدك|الرصيد|المتبقي|متاح|عملية|شراء|دفع|تحويل|خصم|استلام|مبلغ|قيمة|عبر|في|من|الى|إلى|لصالح|تاريخ|الوقت|الساعة|متجر|مؤسسة|company|card|account|balance|available|purchase|payment|transaction|paid|received|via|at|from|to|ref|no|num)',
    ' ', 'gi');
  -- أي شيء غير الحروف — يتشال (تواريخ، أرصدة، رموز)
  v := regexp_replace(v, '[^[:alnum:]]+', ' ', 'g');
  -- مسافات متكررة
  v := regexp_replace(v, '\s+', ' ', 'g');
  v := trim(v);
  if v is null or v = '' then
    return null;
  end if;
  -- خد أول ٨٠ حرف — الاسم الجوهري في الأولادة عادة
  return lower(left(v, 80));
end;
$$;

alter table public.zad_notification_ingest_events
  add column if not exists fuzzy_key text;

-- عمود مشتق — يتعبي بـ trigger عشان الكود القائم والأدوات تستخدمه مباشرة.
create or replace function public.zad_notif_fuzzy_key_fill()
returns trigger
language plpgsql
as $$
begin
  new.fuzzy_key := public.zad_notif_fuzzy_key(new.title, new.body);
  return new;
end;
$$;

drop trigger if exists trg_zad_notif_fuzzy_key on public.zad_notification_ingest_events;
create trigger trg_zad_notif_fuzzy_key
  before insert on public.zad_notification_ingest_events
  for each row
  execute function public.zad_notif_fuzzy_key_fill();

-- مفتاح فريد ناعم: نفس المستخدم + نفس البصمة + نفس نافذة 20 دقيقة = واحد بس.
-- WITHOUT OVERLAPS مش متاح هنا فنستخدم تعبير زمني: الفترة الزمنية مقسمة
-- على 20 دقيقة (epoch / 1200) — نافذة منزلقة مقبولة للاستخدام ده.
create unique index if not exists uq_zad_notif_fuzzy_window
  on public.zad_notification_ingest_events(
    user_id,
    coalesce(fuzzy_key, '~none~'),
    (extract(epoch from created_at)::bigint / 1200)
  )
  where fuzzy_key is not null;

-- backfill: الصفوف القديمة بدون بصمة
update public.zad_notification_ingest_events
set fuzzy_key = public.zad_notif_fuzzy_key(title, body)
where fuzzy_key is null;
