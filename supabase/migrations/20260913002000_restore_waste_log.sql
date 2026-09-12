-- `zad_waste_log` اتعمل في 2026-09-01 واتحذف في 2026-09-05 — **أربع أيام بعده** —
-- ضمن ميجريشن «الجداول الميتة»، وهو له كاتب حي وقارئ حي.
--
-- الكاتب: `trigger_log_inventory_waste` — ترايجر **مفعّل** BEFORE DELETE OR UPDATE على
-- `zad_inventory`. لما العميل يمسح صنف منتهي الصلاحية (أو ينزّل كميته لصفر)، الترايجر
-- بيحاول يكتب سطر هدر في جدول مش موجود، فالعملية نفسها بتفشل. يعني «نضّف المخزون من
-- المنتهي» — أكتر فعل طبيعي في الشاشة دي — مكسور من ٥ سبتمبر.
--
-- القارئ: `zad_domain_observations`، وبيتنادى من **zad-brain** (سطر 655 في بناء
-- السنابشوت) ومن **zad-telegram-bot** (سطر 369). متحقَّق منه حي النهاردة: النداء بيرمي
--   42P01: relation "zad_waste_log" does not exist
--   CONTEXT: PL/pgSQL function zad_domain_observations(uuid) line 39
-- يعني العقل فقد **كل** ملاحظات المجالات (نفاد المخزون، قرب انتهاء الصلاحية، الهدر) من
-- سياقه من ٥ سبتمبر، والنداء بيتعمل من غير فحص خطأ فالفقد كان صامت.
--
-- الجدول بيرجع بنفس تعريفه الأصلي بالحرف (20260901050000) — نفس الأعمدة والفهرس
-- والسياسة: قراءة لصاحبه بس، والكتابة سيرفر-سايد عن طريق الترايجر SECURITY DEFINER.
--
-- تصحيح لرسالة كوميت 040e4aa7: قالت ٦ دوال مكسورة وضمّنت `zad_brain_stats` — ده كان
-- إنذار كاذب، الفحص طابق اسم المتغيّر `v_active_nodes` مش جدول `nodes`. العدد الحقيقي
-- **٥**، و`zad_brain_stats` سليمة. (السبب إن `\b` في بوستجرس معناها backspace مش حدّ
-- كلمة — الفحص الصح بـ`[[:space:]]` و`[^_a-z]`.)

create table if not exists public.zad_waste_log (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  item_name text not null,
  quantity numeric not null,
  unit text,
  logged_at timestamptz not null default now()
);

create index if not exists idx_zad_waste_log_user
  on public.zad_waste_log (user_id, logged_at desc);

alter table public.zad_waste_log enable row level security;

drop policy if exists "user_own_waste_log" on public.zad_waste_log;
create policy "user_own_waste_log" on public.zad_waste_log
  for select using ((select auth.uid()) = user_id);

comment on table public.zad_waste_log is
  'أثر الهدر: صنف انتهت صلاحيته واتمسح/اتصفّر. بيتكتب من trigger_log_inventory_waste '
  'بس (SECURITY DEFINER) وبيتقرا في zad_domain_observations. اتحذف بالغلط في '
  '20260905130000 وهو عمره أربع أيام وله كاتب وقارئ حيّين، فاترجع.';
