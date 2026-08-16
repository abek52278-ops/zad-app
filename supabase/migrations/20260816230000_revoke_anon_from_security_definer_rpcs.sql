-- `anon` كان يقدر ينده أربع دوال SECURITY DEFINER عبر /rest/v1/rpc/ من غير تسجيل دخول.
-- الدالة بتشتغل بصلاحيات صاحبها، يعني RLS مابيتطبقش عليها — وده بالظبط سبب وجودها،
-- بس معناه إن إتاحتها لـ anon بتلغي الحد الأمني كله للبيانات اللي بتلمسها.
--
-- أخطرهم `zad_behavior_patterns(p_user uuid)`: بياخد معرّف مستخدم **كباراميتر**. أي حد
-- على الإنترنت معاه الـ anon key (وهي متشحنة جوه الـ APK) كان يقدر يبعت أي uuid ويقرا
-- نمط إنفاق صاحبه. مش تسريب محتمل — ده قراءة مباشرة.
--
-- والتلاتة التانيين بيكتبوا: `zad_provision_user_row` بتنشئ صف مستخدم،
-- `zad_normalize_transaction_currency` بتعدّل عملة المعاملات، و`zad_retire_market_insight`
-- بتغيّر حالة رؤية. مفيش سبب واحد يخلي زائر مش مسجّل يقدر ينفّذ أي واحدة فيهم.
--
-- الإلغاء على `anon` بس. `authenticated` و`service_role` سايبينهم زي ما هم — التطبيق
-- والـ edge functions بيعتمدوا عليهم، وتقييد `authenticated` محتاج مراجعة كل موقع نداء
-- على حدة وده شغل تاني منفصل. الفرق إن `authenticated` بيبقى عارف مين هو؛ `anon` لأ.

revoke execute on function public.zad_behavior_patterns(uuid) from anon;
revoke execute on function public.zad_provision_user_row() from anon;
revoke execute on function public.zad_normalize_transaction_currency() from anon;
revoke execute on function public.zad_retire_market_insight() from anon;

-- search_path متغيّر في دالة SECURITY DEFINER معناه إن حد يقدر يزرع schema قبل public
-- ويخلي الدالة تنادي كوده هو. الدالة دي بتحوّل نص لتاريخ وبس، بس القاعدة مالهاش استثناء.
alter function public.zad_try_date(text) set search_path = public, pg_temp;
