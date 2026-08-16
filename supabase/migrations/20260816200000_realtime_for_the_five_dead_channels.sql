-- الأبليكيشن بيفتح ٩ قنوات Realtime (RealtimePersonalRepo)، وتلاتة بس منهم على جداول
-- موجودة فعلاً في publication الـ supabase_realtime: zad_transactions (اتضاف في
-- 20260808220000) و zad_inventory + zad_pharmacy_items (في 20260809000000).
--
-- الستة الباقيين — zad_subscriptions, zad_obligations, zad_debts,
-- zad_maintenance_items, zad_shopping_list, zad_users — الاشتراك عليهم بينجح من غير
-- أي خطأ وبعدين مابيوصلوش أي حدث أبداً، لأن Postgres مش بيبث تغييرات جدول مش في
-- الـ publication. النتيجة اللي العميل شافها: البوت بيضيف اشتراك، بيرد "✅ اتسجل"،
-- والصف فعلاً بيتكتب في الداتابيز صح — والتطبيق مش شايفه، لأن الحدث اللي المفروض
-- ينده syncData() عمره ما اتبعت. فشل صامت في الطبقتين: الاشتراك مابيفشلش، والقناة
-- مابتقولش إنها ميتة.
--
-- ADD TABLE بيرمي خطأ لو الجدول عضو أصلاً، والـ migration لازم تفضل تتعاد بأمان.

DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'zad_subscriptions',
    'zad_obligations',
    'zad_debts',
    'zad_maintenance_items',
    'zad_shopping_list',
    'zad_users'
  ]
  LOOP
    -- الجدول لازم يكون موجود: نسخ أقدم من السكيما ممكن ماتكونش وصلت لكل جدول هنا،
    -- وفشل الـ migration كلها بسبب واحد ناقص بيوقف الديبلوي على حاجة مالهاش لازمة.
    IF NOT EXISTS (
      SELECT 1 FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relname = t AND c.relkind = 'r'
    ) THEN
      RAISE NOTICE 'realtime: table %.% not found, skipped', 'public', t;
      CONTINUE;
    END IF;

    IF EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t
    ) THEN
      CONTINUE;
    END IF;

    EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
    RAISE NOTICE 'realtime: added public.%', t;
  END LOOP;
END $$;
