-- ═══════════════════════════════════════════════════════════════════════════
-- العقل كان أعمى عن المعاملات — أعمدة مرجعية موجودة في الكود ومش موجودة في الجدول
-- ═══════════════════════════════════════════════════════════════════════════
--
-- The client model (ZadTransaction, Models.kt:133-136) and zad-brain's buildSnapshot
-- (index.ts:210) have both referenced these four columns since they shipped, but no
-- migration ever created them.
--
-- The failure was silent by construction: PostgREST answers the select with
--   400 {"code":"42703","message":"column zad_transactions.merchant_name does not exist"}
-- and supabase-js returns {data: null, error} rather than throwing, so zad-brain's
--   const transactions = txRes.data ?? [];
-- turned a hard schema error into an empty list on EVERY run, with no log line.
--
-- Downstream, inside the same buildSnapshot call:
--   spent      = 0            (sum over an empty array)
--   remaining  = full budget  (never deducts)
--   velocity   = 0  →  threat = "SAFE" permanently
--   detectCycleStartDay(incomeTx) → null, so a salary cycle can never be detected
--   detectDuplicateExpense / merchant grouping → nothing to group
-- i.e. every financial statement the brain produced was reasoning over zero transactions.
--
-- Verified against this project on 2026-08-02 before applying.

alter table public.zad_transactions
  add column if not exists merchant_name text,
  add column if not exists bank_name text,
  add column if not exists source_type text,
  add column if not exists is_verified boolean not null default false;

comment on column public.zad_transactions.merchant_name is
  'اسم التاجر من تحليل الإشعار البنكي (SaBankParser) — zad-brain بيجمّع عليه لكشف التكرارات وتفضيلات المتاجر';
comment on column public.zad_transactions.bank_name is
  'اسم البنك اللي جه منه الإشعار — للتشخيص ولعرض مصدر المعاملة';
comment on column public.zad_transactions.source_type is
  'مصدر المعاملة: notification / receipt / statement / manual. null = مكتوبة بإيد قبل ما العمود ده يتضاف';
comment on column public.zad_transactions.is_verified is
  'false = معاملة آلية لسه ما اتراجعتش بشرياً. BudgetMath.unverifiedCountInCycle بيبني عليها ثقة كارت "متاح" (Task 27.1a)';

-- Task 28 ("رفض بمعنى") — migration 20260730140000_informative_dismissal.sql exists in the
-- repo but was never applied to this project, so zad-brain's dismissed-insight query
--   sb.from("zad_insights").select("dedupe_key,dismiss_reason")...
-- also 400'd. The brain saw zero dismissals and kept re-emitting insights the user had
-- already explicitly rejected. Same CHECK constraint as the original migration.
alter table public.zad_insights
  add column if not exists dismiss_reason text;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'zad_insights_dismiss_reason_check'
  ) then
    alter table public.zad_insights
      add constraint zad_insights_dismiss_reason_check
      check (dismiss_reason is null or dismiss_reason in ('not_relevant', 'wrong_data', 'timing'));
  end if;
end $$;

comment on column public.zad_insights.dismiss_reason is
  'سبب رفض المستخدم للتنبيه — zad-brain بيقراه عشان ميعيدش نفس التنبيه المرفوض (Task 28)';

-- Every row that exists today was entered by hand: the bank-notification path never
-- reached the server, which is exactly the bug above. Marking them verified keeps the
-- "متاح" card from suddenly reporting the user's own manual entries as unconfirmed the
-- moment unverifiedCountInCycle starts reading a column that actually exists.
update public.zad_transactions set is_verified = true where source_type is null;
