-- =====================================================
-- بند BE-04 — مسح الجداول اللي مالهاش أي مرجع في الكود (قرار المالك، 2026-09-05).
--
-- المعيار: صفر مرجع في `app/src/**/*.kt` وفي `supabase/functions/**/*.ts`. اتأكدت من
-- كل واحد فيهم بالاسم الكامل، مش بمطابقة جزئية.
--
-- ⚠️ zad_fx_rates **مش في القايمة دي عن قصد.** فيه ٢٠ صف داتا حقيقية، ومحدش بيقراه —
-- بس ده مش سبب لمسحه، ده سبب لتوصيله. الأسعار حالياً مطبوعة في
-- `app/src/main/java/com/example/data/CurrencyExchange.kt` وبتتحدّث يدوي، يعني سعر
-- الجنيه مايتصحّحش غير بإصدار جديد على Play. البند مسجّل في
-- docs/agent/BACKLOG_fx_rates.md — "وصّل zad_fx_rates بدل الأسعار المطبوعة".
--
-- ── nodes و edges ──────────────────────────────────────────────────────────────
-- دول **مش في قايمة المالك الأصلية**، وضفتهم بسبب واحد تقني: عندهم foreign key على
-- `workflows`، فمسح workflows لوحده مستحيل من غيرهم. وهم نفس الدرجة من الموت:
-- صفر صفوف، صفر مراجع، وتلاتتهم نظام واحد (رسم بياني لسير عمل) اتبنى وماتوصلش.
--
-- لاحظ إن ZadKnowledgeMapScreen عنده متغيرات اسمها `nodes` و`edges` — دي متغيرات
-- Compose محلية جوّه الشاشة، **مالهاش أي علاقة بالجداول دي**. اتأكدت من ده قبل المسح:
-- مفيش `postgrest["nodes"]` ولا `postgrest["edges"]` ولا `postgrest["workflows"]` في
-- التطبيق كله.
--
-- ── brain_notes و note_links ───────────────────────────────────────────────────
-- الاتنين كانوا في `supabase_realtime` publication كمان، يعني كان فيه تكلفة replication
-- على جداول ميتة. `drop table` بيشيلهم من الـpublication تلقائياً.
--
-- zad_orphaned_rows: أرشيف بطبيعته، ومتوقع يكون فاضي — بس محدش بيكتب فيه ولا بيقرا منه.
-- =====================================================

-- المجموعة الأولى: نظام سير العمل الميت (الترتيب مهم — الأبناء قبل الأب)
drop table if exists public.edges     cascade;
drop table if exists public.nodes     cascade;
drop table if exists public.workflows cascade;

-- المجموعة التانية: جداول مستقلة مالهاش أي مرجع
drop table if exists public.sent_budget_alerts   cascade;
drop table if exists public.shared_grocery_items cascade;
drop table if exists public.user_alert_snooze    cascade;
drop table if exists public.zad_waste_log        cascade;
drop table if exists public.zad_orphaned_rows    cascade;
drop table if exists public.brain_notes          cascade;
drop table if exists public.note_links           cascade;
