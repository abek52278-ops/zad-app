-- ═══════════════════════════════════════════════════════════
-- 20260824170000 — فجوة الرصد: العميل بيدفع والمستمع صامت.
--
-- الحقيقة من الإنتاج (2026-08-24): إشعارات بنكية بتوصل للهاتف لكن
-- zad_notification_ingest_events فاضي تقريبًا (حدث واحد من 8 أغسطس). يعني عمليات
-- دفع حقيقية مش بتترصد ولا العميل بيتسأل عنها — وده جوهر التطبيق.
--
-- التنبيه ده بيكشف القطيعة: لو فيه معاملات يدوية/شات جديدة آخر ٣ أيام (يعني
-- المستخدم نشط) وصفر أحداث ingest (يعني المستمع صامت) → إشعار واحد على
-- تليجرام يشرح المشكلة وزرار الإصلاح فين.
-- ═══════════════════════════════════════════════════════════

create or replace function public._agent_listener_gap_alert_for_user(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_manual_txs int;
  v_ingest int;
begin
  if coalesce(nullif(trim(p_user::text), ''), '') is null then return; end if;

  -- نشاط مالي يدوي آخر ٣ أيام (شات/تسجيل يدوي) = مستخدم عايش وبيدفع فعلاً
  select count(*) into v_manual_txs
    from public.zad_transactions
    where user_id = p_user and created_at > now() - interval '3 days';

  if v_manual_txs < 2 then return; end if;

  -- صفر أحداث رصد في نفس الفترة = المستمع مش شغال أو مش بيوصّل
  select count(*) into v_ingest
    from public.zad_notification_ingest_events
    where user_id = p_user and created_at > now() - interval '3 days';

  -- فيه رصد؟ مفيش فجوة. مفيش رصد + فيه إنفاق = فجوة مؤكدة.
  if v_ingest > 0 then return; end if;

  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'listener_gap_alert',
    format(
      $t$[مهم: رصد الدفع واقف] أنا مش بقدر أسمع إشعارات الدفع على هاتفك من ٣ أيام —
آخر %s عملية اتسجلت يدويًا من غير ما أوصلهالك تلقائيًا.

السبب الغالب: صلاحية "الوصول للإشعارات" بتاعة زاد وقعت أو متفعلةش
(أندرويد بيقلعها بعد التحديثات أحيانًا).

الحل: Settings ← Notification access ← فعّل "Zad Bank Listener"
أو من التطبيق: البروفايل ← حالة الاستماع ← زر الإصلاح.$t$,
      v_manual_txs
    ),
    now()
  )
  on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
    where kind in ('spending_ahead','med_followup','bill_reminder','warranty_reminder','home_weekly_digest','listener_gap_alert')
  do nothing;
end;
$$;

comment on function public._agent_listener_gap_alert_for_user(uuid) is
  'كشف فجوة الرصد: نشاط مالي يدوي بدون أي ingest = المستمع صامت → تذكير يومي واحد بإصلاح الصلاحية.';

-- وسّع الـ dedup index وأدخِل الروتين في نقطة الدخول
drop index if exists idx_agent_tasks_proactive_dedup;
create unique index if not exists idx_agent_tasks_proactive_dedup
  on public.agent_tasks (user_id, kind, ((created_at at time zone 'UTC')::date))
  where kind in ('spending_ahead','med_followup','bill_reminder','warranty_reminder','home_weekly_digest','listener_gap_alert');

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
    perform public._agent_listener_gap_alert_for_user(v_user);
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
