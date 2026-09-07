-- ── دوال المُشغِّلات ماينفعش تتنادى من الـAPI ────────────────────────────────
--
-- مسح 2026-09-07 لقى ١٤ دالة `returns trigger` في `public`، **٨ منهم مُلغى عنهم
-- التنفيذ خلاص** و٦ لأ. يعني الاتفاقية موجودة في المشروع من زمان والستة دول
-- انحرفوا عنها، مش قرار جديد بيتاخد هنا.
--
-- التلاتة الأولانيين مكشوفين لـ`anon` كمان — يعني من غير تسجيل دخول أصلاً:
--   zad_chat_turns_trim         (اتضاف في 84c3d7b، الجلسة دي)
--   zad_enforce_txn_kind
--   zad_notif_fuzzy_key_fill
-- والتلاتة التانيين لـ`authenticated`:
--   zad_normalize_transaction_currency · zad_provision_user_row
--   zad_retire_market_insight
--
-- **حجم الخطر بالظبط، من غير تهويل:** PostgREST مابيعرضش الدوال اللي بترجّع
-- `trigger` كنقط RPC أصلاً، وكلهم بيقروا `new`/`old` فنداء من غير سياق مُشغِّل
-- هيقع. يعني ده **تقوية استباقية واتساق مع الاتفاقية**، مش ثغرة مفتوحة. اتعمل
-- عشان المنحة نفسها مالهاش أي سبب تفضل موجودة، ومنع الصلاحية أرخص من الاعتماد
-- على إن الطبقة اللي فوق مابتعرضهاش.
--
-- **الـtriggers مابتتأثرش.** بوستجرس بيفحص EXECUTE على دالة المُشغِّل وقت
-- `create trigger` مش وقت ما بتولّع، والمالك بيفضل معاه الصلاحية دايماً.
-- واتأكدنا إن ولا واحدة فيهم متنادية كـRPC من الكلاينت ولا من edge function.

revoke execute on function public.zad_chat_turns_trim() from anon, authenticated, public;
revoke execute on function public.zad_enforce_txn_kind() from anon, authenticated, public;
revoke execute on function public.zad_notif_fuzzy_key_fill() from anon, authenticated, public;
revoke execute on function public.zad_normalize_transaction_currency() from anon, authenticated, public;
revoke execute on function public.zad_provision_user_row() from anon, authenticated, public;
revoke execute on function public.zad_retire_market_insight() from anon, authenticated, public;
