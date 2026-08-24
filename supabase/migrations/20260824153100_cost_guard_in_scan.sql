
-- 20260824153100 — حارس الكلفة يُحترم في الـ scan.
-- فوق ٩٥٪ من السقف: نوقّف الملخص الأسبوعي (الأغلى) — تنبيهات السلامة (دوا/فواتير) بتفضل شغالة
-- لأن كلفة إسكاتها أعلى من كلفة التوكنز.
create or replace function public.agent_proactive_scan()
returns void language plpgsql security definer set search_path = public as $$
declare
  v_user uuid;
  v_cost_pct int := public.agent_brain_cost_guard();
begin
  for v_user in
    select u.id from public.zad_users u
    where u.limit_confirmed_at is not null and coalesce(u.monthly_limit,0) > 0
      and not exists (
        select 1 from public.user_alert_snooze s
        where s.user_id = u.id and s.snoozed_until > now()
      )
  loop
    perform public._agent_spending_ahead_for_user(v_user);
    perform public._agent_bill_reminder_for_user(v_user);
    perform public._agent_warranty_reminder_for_user(v_user);
    -- الأغلى آخراً: يتوقف الأول عند اقتراب السقف
    if v_cost_pct < 95 then
      perform public._agent_home_weekly_digest_for_user(v_user);
    end if;
  end loop;

  if v_cost_pct >= 95 then return; end if;

  for v_user in
    select distinct p.user_id from public.zad_pharmacy_items p
    where coalesce(p.is_recurring,false) is true
      and not exists (
        select 1 from public.user_alert_snooze s
        where s.user_id = p.user_id and s.snoozed_until > now()
      )
  loop
    perform public._agent_med_followup_for_user(v_user);
  end loop;
exception when others then return;
end;
$$;
