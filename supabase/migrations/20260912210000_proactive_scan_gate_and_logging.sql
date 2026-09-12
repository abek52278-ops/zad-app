-- الفحص الاستباقي: بوابة أضيق مما يجب، وابتلاع أخطاء صامت.
--
-- قياس 2026-09-12 على الإنتاج: الكرون بينده الدالة دي كل ساعة (~312 تشغيلة في
-- 24 ساعة)، وآخر صف في zad_insights مكتوب من 2026-08-30 — تلتاشر يوم صفر إنتاج.
-- السبب اتنين، والاتنين هنا مش في الذكاء:
--
-- 1. **البوابة كانت بتقفل اللفة كلها.** الشرط
--    `limit_confirmed_at is not null and monthly_limit > 0` كان على الـloop نفسه،
--    فحساب ما أكّدش سقف شهري ماكانش بياخد ولا تذكير فاتورة ولا ضمان ولا تنبيه
--    انقطاع المستمع ولا ملخص أسبوعي — مع إن ولا واحد فيهم محتاج سقف أصلاً. المقيس:
--    ٢ من ٦ حسابات بس كانوا بيعدّوا. السقف دلوقتي شرط على
--    `_agent_spending_ahead_for_user` لوحده، وهو الوحيد اللي بيقارن برقم السقف.
--
-- 2. **`exception when others then return` كان بيبلع كل خطأ.** أي فشل في أي
--    مستخدم كان بينهي الفحص كله من غير صف ولا لوج ولا أثر. ده بالظبط النمط اللي
--    الجلسة اللي فاتت دفعت تمنه ست محاولات: آلية بتفشل مقفولة من غير ما تقول ليه.
--    الحل مش إزالة الحماية — الفشل لازم مايوقعش الكرون — لكن الحماية بقت **لكل
--    مستخدم**، بتسجّل `RAISE WARNING` بالمستخدم والرسالة، وبتكمل لللي بعده.
--
-- وسطر لوج واحد في الآخر بيقول: اتفحص كام، اتخطى كام، فشل كام. من غيره أي تشخيص
-- جاي هيبقى تخمين زي المرة دي بالظبط.

create or replace function public.agent_proactive_scan()
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_user uuid;
  v_has_limit boolean;
  v_cost_pct int := public.agent_brain_cost_guard();
  v_scanned int := 0;
  v_failed int := 0;
begin
  for v_user, v_has_limit in
    select u.id,
           (u.limit_confirmed_at is not null and coalesce(u.monthly_limit, 0) > 0)
    from public.zad_users u
    where not exists (
      select 1 from public.user_alert_snooze s
      where s.user_id = u.id and s.snoozed_until > now()
    )
  loop
    begin
      v_scanned := v_scanned + 1;

      -- ده الوحيد اللي محتاج سقف مؤكَّد: من غير رقم مصرّح بيه مافيش "قدام الميزانية".
      if v_has_limit then
        perform public._agent_spending_ahead_for_user(v_user);
      end if;

      perform public._agent_bill_reminder_for_user(v_user);
      perform public._agent_warranty_reminder_for_user(v_user);
      perform public._agent_listener_gap_alert_for_user(v_user);

      if v_cost_pct < 95 then
        perform public._agent_home_weekly_digest_for_user(v_user);
      end if;
    exception when others then
      v_failed := v_failed + 1;
      raise warning 'agent_proactive_scan: user % failed: % (%)', v_user, sqlerrm, sqlstate;
    end;
  end loop;

  if v_cost_pct < 95 then
    for v_user in
      select distinct p.user_id from public.zad_pharmacy_items p
      where coalesce(p.is_recurring, false) is true
        and not exists (
          select 1 from public.user_alert_snooze s
          where s.user_id = p.user_id and s.snoozed_until > now()
        )
    loop
      begin
        perform public._agent_med_followup_for_user(v_user);
      exception when others then
        v_failed := v_failed + 1;
        raise warning 'agent_proactive_scan: med followup for user % failed: % (%)',
          v_user, sqlerrm, sqlstate;
      end;
    end loop;
  end if;

  raise log 'agent_proactive_scan: scanned=% failed=% cost_pct=%', v_scanned, v_failed, v_cost_pct;
end;
$function$;

comment on function public.agent_proactive_scan() is
  'الفحص الاستباقي بالساعة. الحماية من الأخطاء لكل مستخدم مش للفحص كله، وكل فشل '
  'بيتسجل RAISE WARNING باسم المستخدم. سقف الميزانية شرط على تنبيه "قدام الميزانية" '
  'بس — باقي التنبيهات مالهاش علاقة بالسقف.';
