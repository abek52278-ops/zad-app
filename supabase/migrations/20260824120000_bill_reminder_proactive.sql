-- ═══════════════════════════════════════════════════════════
-- 20260824120000 — وكيل المنزل: تنبيه استباقي للاشتراكات/الفواتير/الأقساط قبل الاستحقاق.
--
-- الطلب (2026-08-24): "الـ pay_bill تبقى تذكير بالاشتراكات والأقساط" — يعني مفيش تسجيل
-- فلوس من العقل، لكن التنبيه قبل ما الاشتراك/القسط/الفاتورة يستحق موجود وشغّال تلقائي.
--
-- نفس نمط spending_ahead/med_followup بالظبط (migration 20260810200000):
-- - روتين per-user رخيص يشتغل من agent_proactive_scan (كرون كل ساعة).
-- - task kind='bill_reminder' في agent_tasks مع قفل عدم التكرار اليومي
--   idx_agent_tasks_proactive_dedup_uq (user, kind, UTC date) — الكرون ممكن يركب
--   مرتين ومفيش تنبيه مكرر.
-- - المعالج processDueAgentTasks هو اللي بينفّذ الـ task ويحوّلها إشعار حقيقي —
--   مفيش كود توصيل جديد خالص.
--
-- القاعدة: أي اشتراك نشط أو التزام ثابت استحقاقه خلال ٣ أيام (أو فات)، والعميل
-- ماحدثش عنه في نفس اليوم، بياخد تذكير واحد فيه الاسم والمبلغ ويوم الاستحقاق.
-- ═══════════════════════════════════════════════════════════

create or replace function public._agent_bill_reminder_for_user(p_user uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_items jsonb;
begin
  if coalesce(nullif(trim(p_user::text), ''), '') is null then
    return;
  end if;

  -- اجمع المرشحين من المصدرين: الاشتراكات النشطة (بـ renewal_date نصي YYYY-MM-DD)
  -- والالتزامات الثابتة النشطة (due_day شهري → أقرب استحقاق قادم). كلهم في JSONB
  -- واحدة عشان رسالة واحدة مجمّعة بدل سبع تنبيهات مزعجة.
  select jsonb_agg(item)
    into v_items
    from (
      -- اشتراكات: renewal_date خلال ٣ أيام أو فات من غير تجديد مسجل
      select jsonb_build_object(
        'title', s.title,
        'amount', s.amount,
        'source', 'subscription',
        'due', s.renewal_date,
        'days_left', (s.renewal_date::date - (now() at time zone 'utc')::date)
      ) as item
      from public.zad_subscriptions s
      where s.user_id = p_user
        and s.is_active is true
        and s.renewal_date ~ '^\d{4}-\d{2}-\d{2}$'
        and (s.renewal_date::date - (now() at time zone 'utc')::date) between -2 and 3

      union all

      -- التزامات ثابتة: أقرب due_day جاي خلال ٣ أيام (أو عدّى على الفاضي ولسه مدفعتش
      -- معاملة سداد بنفس العنوان هذا الشهر — تقريب مقبول: التذكير مرة يومياً بس أصلاً).
      select jsonb_build_object(
        'title', o.title,
        'amount', o.amount,
        'source', 'obligation',
        'kind', o.kind,
        'due_day', o.due_day
      ) as item
      from public.zad_obligations o
      where o.user_id = p_user
        and o.active is true
        and o.due_day is not null
        and ((o.due_day - extract(day from now() at time zone 'utc')::int + 31) % 31) <= 3
    ) candidates;

  if v_items is null or v_items = '[]'::jsonb then
    return; -- مفيش حاجة مستحقة قريب — صفر تكلفة وصفر إزعاج
  end if;

  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'bill_reminder',
    format(
      $t$[تذكير مستحقات] دول المستحقات القريبة عندك:
%s
لو حاجة فيهم اتدفعت فعلاً، قولّي وأحدّث التاريخ. لو عايز تراجع التفاصيل، افتح شاشة الالتزامات.$t$,
      -- ملاحظة: ->> أعلى precedence من || — لازم أقواس حوالين كل item->>'x'
      -- وإلا '- ' || item->>'title' بتتفسر ('- ' || item) ->> 'title' وتفشل cast لـ json.
      (select string_agg(
         '- ' || (item->>'title')
         || coalesce(' (' || ((item->>'amount')::text) || ')', '')
         || case
              when jsonb_exists(item, 'days_left') and ((item->>'days_left')::int < 0)
                then ' — استحقاق فات، راجعه'
              when jsonb_exists(item, 'days_left') and ((item->>'days_left')::int = 0)
                then ' — النهاردة'
              when jsonb_exists(item, 'days_left')
                then ' — بعد ' || ((item->>'days_left')) || ' يوم'
              else ''
            end,
         E'\n'
       ) from jsonb_array_elements(v_items) as item)
    ),
    now()
  )
  on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
    where kind in ('spending_ahead','med_followup','bill_reminder')
  do nothing;
end;
$$;

comment on function public._agent_bill_reminder_for_user(uuid) is
  'وكيل المنزل — تذكير يومي واحد مجمّع بالاشتراكات/الفواتير/الأقساط المستحقة خلال ٣ أيام. تنبيه فقط: مفيش أي كتابة مالية من العقل (قرار 2026-08-24).';

-- دخّلها في نقطة الدخول الموجودة — replace بيحافظ على الصلاحيات والـ cron المجدول.
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
