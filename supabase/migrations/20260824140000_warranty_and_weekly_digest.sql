-- ═══════════════════════════════════════════════════════════
-- 20260824140000 — التحسينات الثورية (دفعة 1):
--   1. تنبيه ضمان الأجهزة قبل الانتهاء بـ 14 يوم
--   2. ملخص أسبوعي استباقي (home_weekly_digest)
--
-- نفس نمط bill_reminder بالظبط: روتين per-user رخيص من agent_proactive_scan،
-- task في agent_tasks مع قفل عدم التكرار اليومي، والمعالج الموجود هو اللي يوصّل.
-- ═══════════════════════════════════════════════════════════

-- ── 1) ضمان الأجهزة: تذكير قبل الانتهاء بـ 14 يوم أو أقل ──
create or replace function public._agent_warranty_reminder_for_user(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_items jsonb;
begin
  if coalesce(nullif(trim(p_user::text), ''), '') is null then
    return;
  end if;

  select jsonb_agg(jsonb_build_object(
    'name', m.name,
    'days_left', (m.warranty_expiry_date::date - (now() at time zone 'utc')::date)
  ))
    into v_items
    from public.zad_maintenance_items m
    where m.user_id = p_user
      and m.warranty_expiry_date ~ '^\d{4}-\d{2}-\d{2}$'
      and (m.warranty_expiry_date::date - (now() at time zone 'utc')::date) between -2 and 14;

  if v_items is null then
    return;
  end if;

  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'warranty_reminder',
    format(
      $t$[ضمانات قربت] الأجهزة دي ضمانتها هتنتهي قريب:
%s
لو الجهاز فيه عطل دلوقتي، ده آخر وقت تستخدم الضمان — بعدها الصيانة على حسابك.$t$,
      (select string_agg(
         '- ' || (item->>'name')
         || case
              when ((item->>'days_left')::int < 0) then ' — الضمان انتهى'
              when ((item->>'days_left')::int = 0) then ' — النهاردة آخر يوم!'
              else ' — بعد ' || ((item->>'days_left')) || ' يوم'
            end,
         E'\n')
       from jsonb_array_elements(v_items) as item)
    ),
    now()
  )
  on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
    where kind in ('spending_ahead','med_followup','bill_reminder','warranty_reminder','home_weekly_digest')
  do nothing;
end;
$$;

comment on function public._agent_warranty_reminder_for_user(uuid) is
  'تنبيه ضمان الأجهزة قبل الانتهاء بـ 14 يوم — فرصة أخيرة لاستخدام الضمان مجاناً.';

-- ── 2) الملخص الأسبوعي: صورة كاملة للبيت مرة كل أسبوع ──
create or replace function public._agent_home_weekly_digest_for_user(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_spent numeric;
  v_last numeric;
  v_low_stock int;
  v_due_soon int;
  v_meds_low int;
begin
  if coalesce(nullif(trim(p_user::text), ''), '') is null then
    return;
  end if;

  -- صرف الأسبوع الحالي vs الأسبوع اللي فاته
  select coalesce(sum(amount), 0) into v_spent
    from public.zad_transactions
    where user_id = p_user and is_expense is true
      and created_at >= now() - interval '7 days';
  select coalesce(sum(amount), 0) into v_last
    from public.zad_transactions
    where user_id = p_user and is_expense is true
      and created_at >= now() - interval '14 days'
      and created_at < now() - interval '7 days';

  select count(*) into v_low_stock
    from public.zad_inventory
    where user_id = p_user and quantity <= coalesce(low_stock_threshold, 1);

  select count(*) into v_due_soon from (
    select 1 from public.zad_subscriptions s
      where s.user_id = p_user and s.is_active is true
        and s.renewal_date ~ '^\d{4}-\d{2}-\d{2}$'
        and (s.renewal_date::date - (now() at time zone 'utc')::date) between -2 and 7
    union all
    select 1 from public.zad_obligations o
      where o.user_id = p_user and o.active is true and o.due_day is not null
        and ((o.due_day - extract(day from now() at time zone 'utc')::int + 31) % 31) <= 7
  ) d;

  select count(*) into v_meds_low
    from public.zad_pharmacy_items
    where user_id = p_user and remaining_quantity <= coalesce(daily_dose_count, 1) * 5;

  -- مفيش حاجة تستاهل ملخص؟ متبعتش حاجة خالص — مينفعش نختلق محتوى
  if v_spent = 0 and v_low_stock = 0 and v_due_soon = 0 and v_meds_low = 0 then
    return;
  end if;

  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'home_weekly_digest',
    format(
      $t$[ملخص البيت الأسبوعي]
• مصروف الأسبوع: %s (الأسبوع اللي فات: %s%s)
• أصناف قربت تخلص من المخزون: %s
• مستحقات خلال ٧ أيام: %s
• أدوية قربت: %s

حلّل الأرقام دي للعميل في جملتين، وقول أهم حاجة واحدة يعملها الأسبوع الجاي.$t$,
      round(v_spent)::text,
      round(v_last)::text,
      case when v_last > 0
        then '، ' || case when v_spent > v_last then 'زادت' else 'قلّت' end
             || ' ' || abs(round(v_spent - v_last))::text else '' end,
      v_low_stock, v_due_soon, v_meds_low
    ),
    now()
  )
  on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
    where kind in ('spending_ahead','med_followup','bill_reminder','warranty_reminder','home_weekly_digest')
  do nothing;
exception
  when others then
    return;
end;
$$;

comment on function public._agent_home_weekly_digest_for_user(uuid) is
  'ملخص أسبوعي مجمّع: صرف مقارن + مخزون ناقص + مستحقات + أدوية. مفيش ملخص لو مفيش محتوى.';

-- نقطة الدخول الوحيدة — بنضيف الروتينين الجداد جنب القديم
create or replace function public.agent_proactive_scan()
returns void language plpgsql security definer set search_path = public as $$
declare
  v_user uuid;
begin
  for v_user in
    select id from public.zad_users
    where limit_confirmed_at is not null
      and coalesce(monthly_limit, 0) > 0
  loop
    perform public._agent_spending_ahead_for_user(v_user);
    perform public._agent_bill_reminder_for_user(v_user);
    perform public._agent_warranty_reminder_for_user(v_user);
    perform public._agent_home_weekly_digest_for_user(v_user);
  end loop;

  for v_user in
    select distinct user_id from public.zad_pharmacy_items
    where coalesce(is_recurring, false) is true
  loop
    perform public._agent_med_followup_for_user(v_user);
  end loop;
exception
  when others then
    return;
end;
$$;

-- وسّع الـ dedup index ليضم الأنواع الجديدة
drop index if exists idx_agent_tasks_proactive_dedup;
create unique index if not exists idx_agent_tasks_proactive_dedup
  on public.agent_tasks (user_id, kind, ((created_at at time zone 'UTC')::date))
  where kind in ('spending_ahead','med_followup','bill_reminder','warranty_reminder','home_weekly_digest');
