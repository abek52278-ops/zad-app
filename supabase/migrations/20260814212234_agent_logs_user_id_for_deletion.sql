-- =====================================================
-- agent_logs.user_id — عشان بيانات العميل تتمسح فعلاً لما يمسح حسابه.
--
-- الجدول اتعمل من غير أي عمود بيربطه بمستخدم خالص. و`logged()` بتكتب فيه
-- `payload: { input, output }` بتاع كل نداء AI — والـinput هو البرومبت، اللي جواه
-- ميزانية العميل ومعاملاته وأدويته.
--
-- النتيجة إن عميل يمسح حسابه، وبياناته تفضل في الجدول ده **للأبد**، ومفيش حتى طريقة
-- نعرف بيها الصفوف بتاعة مين عشان نمسحها يدوي. `delete-account` ماكانش يقدر يلمسها
-- حتى لو حد افتكر.
--
-- العمود nullable عن قصد: الـ13 صف الموجودين اتكتبوا قبل كده ومحدش يعرف صاحبهم،
-- وتخمين مالك ليهم أوحش من الاعتراف إنه مجهول. أي صف جديد بياخد صاحبه من نفس
-- `user_id` اللي الأكشن جاي بيه.
-- =====================================================

ALTER TABLE agent_logs
  ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS agent_logs_user_idx ON agent_logs (user_id);

COMMENT ON COLUMN agent_logs.user_id IS
  'Owner of the AI call this row logged. Added 2026-08-14: the table had no user column at all, so a customer who deleted their account left their prompt payloads — budgets, transactions, medications — behind permanently, with no way to identify or remove them. ON DELETE CASCADE ties them to the account.';
