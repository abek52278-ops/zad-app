-- 20260721160000_add_family_member_spend_limits.sql مسجّلة "applied" في
-- supabase_migrations.schema_migrations (اتأكد حي) بس daily_limit/weekly_limit
-- مش موجودين فعليًا على family_members في القاعدة الحية — انكشف عن طريق
-- schema_contract_test.ts (بند 30.1) لما اتوصّل بالـCI أول مرة: zad-brain/index.ts
-- و parent-digest/index.ts بيسألوا عن العمودين دول ويرجعوا 42703 بصمت من يوم
-- ما اتكتب الكود، يعني حد الإنفاق اليومي/الأسبوعي للأبناء عمره ما اتفعّل فعليًا.
-- نفس الـDDL بالظبط من الميجريشن الأصلية، IF NOT EXISTS فبتتطبق آمن أيًا كان
-- سبب الانحراف الأصلي.
alter table public.family_members
  add column if not exists daily_limit numeric,
  add column if not exists weekly_limit numeric;
