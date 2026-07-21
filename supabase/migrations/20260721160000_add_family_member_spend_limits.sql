-- إضافة حد إنفاق يومي/أسبوعي اختياري لكل عضو (خصوصاً الأبناء) — الأب يحدده من شاشة العائلة.
-- الاستهلاك نفسه بيتحسب من رسائل PURCHASE_REQUEST الموافق عليها (chat_messages)، مفيش داعي لجدول منفصل.
ALTER TABLE family_members
  ADD COLUMN IF NOT EXISTS daily_limit NUMERIC,
  ADD COLUMN IF NOT EXISTS weekly_limit NUMERIC;
