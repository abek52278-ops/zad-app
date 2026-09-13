-- 20260913162000 — قفل دالتين اتعملوا من غير revoke.
--
-- Supabase بيدّي `anon`/`authenticated` صلاحية EXECUTE افتراضيًا على أي دالة جديدة في
-- schema `public`، و20260813190358 (lock_down_internal_agent_functions) قفل دوال
-- العقل الداخلية بـrevoke صريح لكل واحدة. دالتين اتضافوا بعدها من غير الـrevoke:
--
--   _agent_spend_forecast_for_user(uuid)  — 20260912233000
--   zad_proactive_silence_check()         — 20260913000000
--
-- الاتنين `security definer`، يعني بيشتغلوا بصلاحيات المالك مش المتصل. ومكشوفين عبر
-- PostgREST على `/rest/v1/rpc/...` لأي حد معاه الـanon key (اللي موجود في كل APK):
--   - الأولى بتكتب مهمة `spend_forecast` في agent_tasks لأي `p_user` يتبعت — كل مهمة
--     بتتحول لنداء موديل + إشعار للمستخدم ده. سبام وتضخيم تكلفة على حساب أي حد.
--   - التانية بتبعت تنبيه تليجرام للأدمن (مرة في اليوم بسبب الـdedupe).
--
-- اتكشفت 2026-09-13 بفحص شامل: كل دالة security definer داخلية في public عليها
-- EXECUTE لـanon — دول الاتنين بس. محدش بيناديهم غير SQL (agent_proactive_scan و
-- zad_brain_health_check، الاتنين security definer بمالك postgres)، فالقفل مابيكسرش حاجة.

revoke execute on function public._agent_spend_forecast_for_user(uuid) from public, anon, authenticated;
revoke execute on function public.zad_proactive_silence_check() from public, anon, authenticated;
grant execute on function public._agent_spend_forecast_for_user(uuid) to service_role;
grant execute on function public.zad_proactive_silence_check() to service_role;
