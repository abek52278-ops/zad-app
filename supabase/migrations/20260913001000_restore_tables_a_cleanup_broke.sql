-- تنضيف «جداول ميتة» في ٢٠٢٦-٠٩-٠٥ حذف جداول **شغّالة فعلاً**، والعطل فضل مستخفي أسبوع.
--
-- `20260905130000_drop_dead_tables.sql` حذفت ١٠ جداول. تلاتة منهم مكانوش ميتين:
-- كود حي في Postgres نفسه لسه بيقرا منهم. ومفيش حاجة كشفت ده، لأن دوال plpgsql
-- **مابتتفحصش مراجع الجداول وقت الإنشاء** — المرجع بيفضل ساكت لحد ما الدالة تتنفّذ.
--
-- الفحص (2026-09-12): ٦ دوال بتشاور على جداول محذوفة:
--   `agent_proactive_scan`, `_agent_user_snoozed`        ← user_alert_snooze
--   `notify_parents_on_child_spend`                       ← sent_budget_alerts
--   `zad_brain_stats`                                     ← nodes
--   `zad_domain_observations`, `zad_log_inventory_waste`  ← zad_waste_log
--
-- الأثر المقيس، والتواريخ بتطابق الحذف بالظبط:
--   * `agent_proactive_scan` بترمي 42P01 عند **أول استعلام** فيها كل ساعة، و
--     `exception when others then return` بيبلعها. آخر مهمة استباقية اتكتبت:
--     **2026-09-05** — نفس يوم الحذف. الفحص شغّال من ساعتها وبيطلّع صفر.
--   * `notify_parents_on_child_spend` ترايجر **مفعّل** على `zad_transactions`.
--     بيرجع بدري لأي حساب مش `role='child'`، فمعاملات الكبار عدّت عادي (٣ معاملات
--     بعد الحذف، آخرها 2026-09-08) — لكن **أول مصروف يسجّله طفل عنده سقف هيفشل
--     الإدراج نفسه**. لغم مش عطل ظاهر.
--
-- الإصلاح مختلف حسب الحالة، مش نسخة واحدة للكل:
--   * `sent_budget_alerts` **بيترجع**: ده مخزن منع التكرار لتنبيه الأهل، وله كاتب حي.
--     حذفه كان غلط بسيط. و`sent_telegram_budget_alerts` (بنفس الشكل بالظبط) **مش**
--     بديل: ده مخزن مسار تليجرام، ولو اتشارك الاتنين هيخنقوا بعض عند نفس العتبة.
--   * `user_alert_snooze` **مابيرجعش**: الميزة دي مالهاش كاتب أصلاً (ولا سطر في
--     الكوتلن)، فالحذف كان صح والغلط إن الدوال ما اتحدّثتش. الشرط اتشال من
--     `agent_proactive_scan` (في 20260912210000) و`_agent_user_snoozed` بقت بترجع
--     false صراحةً.
--
-- تصحيح (كُتب بعد هذا الملف، في نفس الجلسة): الفقرة دي في الأصل سابت
-- `zad_brain_stats`، `zad_domain_observations`، و`zad_log_inventory_waste` مكسورين
-- بقصد. الاتنين الأخيرين رجعوا فعلاً في `20260913002000_restore_waste_log.sql`
-- (بترجّع `zad_waste_log` نفسه — كاتب حي `trigger_log_inventory_waste` وقارئ حي
-- `zad_domain_observations`). و`zad_brain_stats` طلعت **إنذار كاذب** أصلاً: الفحص
-- طابق اسم المتغيّر `v_active_nodes` مش جدول `nodes` — في بوستجرس `\b` في الريجيكس
-- معناها backspace مش حدّ كلمة. يعني بعد `20260913002000` **مفيش دالة متبقية
-- مكسورة** من تنضيف ٢٠٢٦-٠٩-٠٥؛ الملف ده يُقرأ كخطوة أولى في سلسلة اتقفلت بالكامل،
-- مش كقرار نهائي.

create table if not exists public.sent_budget_alerts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  threshold int not null,
  month text not null,
  created_at timestamp default now()
);

create unique index if not exists idx_sent_alerts_idempotency
  on public.sent_budget_alerts (user_id, threshold, month);

-- مفيش عميل بيقرا الجدول ده: الكاتب الوحيد ترايجر SECURITY DEFINER. RLS متفعّل من غير
-- أي سياسة = ممنوع على كل عميل، والترايجر بيعدّي لأنه بيتخطى RLS.
alter table public.sent_budget_alerts enable row level security;

comment on table public.sent_budget_alerts is
  'منع تكرار تنبيه الأهل عن صرف الطفل (عتبات 75/90/100 لكل شهر). اتحذف بالغلط في '
  '20260905130000 وهو له كاتب حي (notify_parents_on_child_spend)، فاترجع. مش نفس '
  'sent_telegram_budget_alerts — ده مخزن مسار تليجرام المنفصل.';

-- الميزة مالهاش مخزن بعد الحذف المقصود، فالجواب الصحيح "مش مكتوم" مش استعلام على
-- جدول مش موجود.
create or replace function public._agent_user_snoozed(p_user uuid)
returns boolean
language sql
immutable
security definer
set search_path to 'public'
as $fn$
  select false;
$fn$;

comment on function public._agent_user_snoozed(uuid) is
  'بترجع false دايماً: جدول user_alert_snooze اتحذف في 20260905130000 ومكانش له كاتب. '
  'الدالة سايبة عشان نقط النداء الموجودة تفضل شغالة من غير تعديل.';
