
-- 20260824152000 — وضع الإجازة يُحترم في نقطة الدخول الوحيدة (agent_proactive_scan).
create or replace function public.agent_proactive_scan()
returns void language plpgsql security definer set search_path = public as $$
declare v_user uuid;
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
    perform public._agent_home_weekly_digest_for_user(v_user);
  end loop;

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
