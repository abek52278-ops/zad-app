-- Task 28 (PRODUCT_PLAN.md) — informative dismissal. Dismissal used to only suppress;
-- the reason is far more valuable than the fact, especially 'wrong_data' — a free bug
-- report from the person best placed to notice, previously thrown away entirely.
alter table public.zad_insights
  add column if not exists dismiss_reason text
    check (dismiss_reason in ('not_relevant', 'wrong_data', 'timing'));

comment on column public.zad_insights.dismiss_reason is
  'not_relevant (مش مهم): permanently suppress this dedupe_key, memory note about the category. wrong_data (الرقم غلط): permanently suppress AND flag the underlying data as unreliable. timing (عرفت خلاص): suppress this instance only — buildSnapshot deliberately excludes this reason from dismissed_keys, so the same dedupe_key may legitimately resurface later (e.g. next month''s occurrence upserts the row back to pending).';
