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
-- returns text[]
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
  -- كلمات حشو بنكية وواجهات — بأشكالها المطبّعة (ة→ه، ى→ي حصل فوق)
  v := regexp_replace(v,
    '(بطاقه|عمليه|شراء|دفع|تحويل|خصم|استلام|مبلغ|قيمه|عبر|لصالح|تاريخ|الحساب|حسابك|رصيدك|الرصيد|المتبقي|متاح|الوقت|الساعه|متجر|مؤسسه|ريال|جنيه|درهم|دينار|ريالات|sar|egp|aed|kwd|في|من|الي|عند|company|card|account|balance|available|purchase|payment|transaction|paid|received|via|at|from|to|ref|no|num)',
    ' ', 'gi');
  -- أي شيء غير الحروف — يتشال (تواريخ، أرصدة، رموز)
  v := regexp_replace(v, '[^[:alnum:]]+', ' ', 'g');
  -- مسافات متكررة
  v := regexp_replace(v, '\s+', ' ', 'g');
  -- الأرقام المعزولة (مبالغ/أوقات/أرصدة) تتشال — المبلغ بيتقارن على مستوى الproposal
  -- بالتساوي الرقمي، واللي عايزين نلصقهم هنا هو اسم التاجر بس.
  v := regexp_replace(v, '\b[0-9]+\b', ' ', 'g');
  v := regexp_replace(v, '\s+', ' ', 'g');
  -- حروف الوصل الملتصقة المتبقية (بالبطاقه->بطاقه اتشالت فبقى 'بال'... إلخ): أي token
  -- من ٣ حروف أو أقل بعد التنظيف هو حشو — أسماء التجار الحقيقية أطول من كده.
  v := regexp_replace(v, '\b[[:alnum:]]{1,3}\b', ' ', 'g');
  v := regexp_replace(v, '\s+', ' ', 'g');
  v := trim(v);
  if v is null or v = '' then
    return null;
  end if;
  -- مصفوفة توكنات: شيل 'ال' التعريف من كل كلمة، واستبعد المكرر والقصير (<3).
  -- العقل في كود الدالة بيستخدم && (overlap) مش مساواة — فإعادة صياغة البنك اللي
  -- بتذكر كلمة زيادة أو ناقصة بتلاقي نفس التاجر، والعملية التانية بتختلف.
  -- المبلغ مش هنا — بيتقارن على مستوى الproposal بالتساوي الرقمي.
  declare
    tokens text[];
  begin
    select coalesce(array_agg(distinct t order by t), '{}')
      into tokens
      from (
        select regexp_replace(t, '^ال', '') as t
        from unnest(string_to_array(v, ' ')) as t
        where length(regexp_replace(t, '^ال', '')) >= 3
      ) s;
    if tokens is null or array_length(tokens, 1) = 0 then
      return null;
    end if;
    return tokens;
  end;
end;
$$;

alter table public.zad_notification_ingest_events
  add column if not exists fuzzy_tokens text[];

-- عمود مشتق — يتعبي بـ trigger عشان الكود القائم والأدوات تستخدمه مباشرة.
create or replace function public.zad_notif_fuzzy_key_fill()
returns trigger
language plpgsql
as $$
begin
  new.fuzzy_tokens := public.zad_notif_fuzzy_key(new.title, new.body);
  return new;
end;
$$;

drop trigger if exists trg_zad_notif_fuzzy_key on public.zad_notification_ingest_events;
create trigger trg_zad_notif_fuzzy_key
  before insert on public.zad_notification_ingest_events
  for each row
  execute function public.zad_notif_fuzzy_key_fill();

-- GIN عشان البحث بالـ overlap (&&) في استعلام الدالة.
create index if not exists idx_zad_notif_fuzzy_tokens
  on public.zad_notification_ingest_events using gin (fuzzy_tokens)
  where fuzzy_tokens is not null;

-- backfill: الصفوف القديمة بدون بصمة
update public.zad_notification_ingest_events
set fuzzy_tokens = public.zad_notif_fuzzy_key(title, body)
where fuzzy_tokens is null;
