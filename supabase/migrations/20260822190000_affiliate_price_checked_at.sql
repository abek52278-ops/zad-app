-- أسعار أمازون بشفافية: تاريخ آخر تحقق لكل سعر.
-- الكارت كان بيعرض average_price_sar كأنه السعر الحالي — ده ادعاء. من دلوقتي
-- الكلاينت يعرض "منذ X يوم" ويطلب refresh لما السعر يتقدم في العمر.
alter table affiliate_products
  add column if not exists price_checked_at timestamptz;

comment on column affiliate_products.price_checked_at is
  'آخر مرة اتأكد فيها السعر (أدمن أو تحديث يدوي). null = السعر قديم جداً أو مكتوب مرة واحدة وقت الإنشاء.';
