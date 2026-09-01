-- zad_match_bnpl_obligation (اتضافت في الميجريشن اللي فاتت، نفس اليوم) طلعت anon/authenticated-
-- executable زي أي SECURITY DEFINER جديد من غير revoke صريح — نفس النمط اللي
-- 20260813190358_lock_down_internal_agent_functions.sql اتعمل عشانه بالظبط. الفانكشن دي
-- بتتنده جوه private.zad_resolve_transaction_proposal_impl بس (SECURITY DEFINER هي كمان،
-- فبتنفّذ بصلاحية المالك مش المستخدم اللي نادها، فقفل EXECUTE هنا ملوش أي أثر على المسار
-- الحقيقي). من غير القفل، أي حد معاه anon key بس (من غير تسجيل دخول) كان يقدر ينده
-- /rest/v1/rpc/zad_match_bnpl_obligation بـ uuid أي عميل ويعرف لو عنده خطة تقسيط نشطة
-- بمزوّد ومبلغ معينين — تسريب معلومة صغير بس حقيقي.
revoke execute on function public.zad_match_bnpl_obligation(uuid, text, numeric) from public, anon, authenticated;

-- نفس الحكاية، اتلقت في نفس المسح: populate_family_inventory (BEFORE INSERT trigger على
-- zad_inventory، من 20260901010000_family_shared_inventory.sql) و zad_log_inventory_waste
-- (نفس الشيء، من 20260901050000_waste_tracking.sql) — التريجرين دول بيتنفذوا عن طريق
-- Postgres نفسه لما صف يتغيّر في zad_inventory، مش عن طريق نداء RPC مباشر من حد. نفس
-- السابقة بالظبط اللي notify_telegram_on_transaction اتقفلت عشانها في
-- 20260813190358_lock_down_internal_agent_functions.sql: قفل EXECUTE المباشر ملوش أي أثر
-- على التريجر، بيقفل بس مسار /rest/v1/rpc اللي محدش المفروض يستخدمه أصلاً.
revoke execute on function public.populate_family_inventory() from public, anon, authenticated;
revoke execute on function public.zad_log_inventory_waste() from public, anon, authenticated;
