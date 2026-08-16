-- =====================================================
-- آخر موقع معروف للعميل — عشان العقل يقدر يرشّح محل قريب.
--
-- `nearby_pois` مبنية في zad-core-intelligence من زمان، بس العقل مكانش عنده أي طريقة
-- يعرف بيها العميل فين، فمكانش يقدر ينادي عليها أصلاً. العمودين دول هما الحلقة الناقصة.
--
-- **من تثبيتة وقت الاستخدام بس** — مفيش صلاحية موقع في الخلفية داخلة في ده.
-- `ACCESS_BACKGROUND_LOCATION` لسه قرار منتج مفتوح (PLAY_CONSOLE_BACKGROUND_LOCATION.md)
-- وجوجل بتراجعها يدوي، فالميزة دي اتبنت على اللي التطبيق بياخده فعلاً دلوقتي.
--
-- والعقل بيرفض أي تثبيتة أقدم من ٦ ساعات: موقع بايت بيطلّع نصيحة واثقة وغلط ("عدّي على
-- المحل اللي جنبك") عن مكان العميل مشي منه من امبارح، وده أوحش من "مش عارف انت فين".
-- =====================================================

ALTER TABLE zad_users
  ADD COLUMN IF NOT EXISTS last_lat DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS last_lon DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS last_location_at TIMESTAMPTZ;

COMMENT ON COLUMN zad_users.last_location_at IS
  'When last_lat/last_lon were written. The brain refuses to use a fix older than a few hours: a stale location produces confidently wrong advice ("pass by the market on your way") about a place the customer left yesterday. Written by the client from a foreground fix only — no background location permission is involved.';
