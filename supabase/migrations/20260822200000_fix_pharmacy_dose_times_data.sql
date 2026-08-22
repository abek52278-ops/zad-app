-- ============================================================
-- تنظيف أوقات جرعات الصيدلية المتضررة.
--
-- الفحص الحي (2026-08-22) لقا صفين معطوبين:
-- 1. "مسكن" بساعة بأرقام عربية هندية (٠٣:٤٩) — الـ AlarmManager مش هيفهمها
--    والجرعة مش هتنبه أبداً.
-- 2. "كتافلام" daily_dose_count=1 من غير dose_times خالص — تناقض.
--
-- الإصلاح الجذري في الكود (validators.ts) بيتعامل مع الحالتين للمستقبل؛
-- الـ migration ده بيصلح البيانات الموجودة:
-- - الأرقام العربية الهندية بتتحمل لأرقام ASCII.
-- - صف من غير أوقات لكن عليه عدد جرعات: بنصفّر has_invalid_dose_time
--   عشان التطبيق يطلب الميعاد من العميل بدل ما يخمّن.
-- ============================================================

-- 1) تطبيع الأرقام العربية الهندية في dose_times (٠-٩ → 0-9)
update zad_pharmacy_items
set dose_times = translate(
      dose_times,
      '٠١٢٣٤٥٦٧٨٩',
      '0123456789'
    ),
    has_invalid_dose_time = false
where dose_times ~ '[٠-٩]';

-- 2) صفوف من غير أوقات لكن ليها عدد جرعات → علمها كميعاد ناقص يُطلب من العميل
update zad_pharmacy_items
set has_invalid_dose_time = true
where dose_times is null
  and daily_dose_count > 0
  and coalesce(has_invalid_dose_time, false) = false;
