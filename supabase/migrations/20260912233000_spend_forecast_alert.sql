-- توقّع الأسبوع الجاي — تحويل التعلّم لكلام بيوصل للعميل.
--
-- `update-behavior-profile` بيشتغل كل يوم الساعة ٢٣ وبيحسب `avg_weekly_spending`
-- و`subscription_load_monthly` وأنماط الصرف. المقيس 2026-09-12: الجدول فيه صفين
-- محسوبين فعلاً، وبيتقروا في سياق الشات وفي التطبيق — **ومفيش أي حاجة بتحوّلهم لرسالة
-- استباقية**. يعني العقل بيتعلّم ويسكت، والعميل مايعرفش إنه اتعلّم.
--
-- التنبيه ده بيجاوب سؤال واحد قدّام: «الأسبوع الجاي، المتوقع يخرج أكتر من اللي معايا؟»
--   المتوقع خارج = متوسط الصرف الأسبوعي + الالتزامات المستحقة خلال ٧ أيام
--   المتاح       = zad_cash_balance (نفس الرقم اللي العميل شايفه في الكارت)
-- ولو المتوقع أكبر، بيزرع مهمة والمعالج بيوصّلها بصوت زاد زي أي تنبيه تاني.
--
-- ثلاث بوابات عشان ماتبقاش إزعاج:
--   * ملف سلوك قديم (أكتر من ١٤ يوم) أو متوسط صفر = مانتكلمش. تخمين أسوأ من سكوت.
--   * رصيد غير معروف = مانتكلمش، مش نفترض صفر.
--   * مرة كل ٧ أيام بالكتير. توقّع بيتكرر كل يوم بيتقري نقّ مش تنبيه — وده فوق قفل
--     التكرار اليومي في الفهرس الجزئي.
--
-- الفهرس الجزئي `idx_agent_tasks_proactive_dedup` مكتوب فيه أنواع التنبيهات بالاسم،
-- فلازم يتوسّع عشان يشمل النوع الجديد، وإلا النوع ده مالوش قفل يومي أصلاً.

drop index if exists public.idx_agent_tasks_proactive_dedup;
create unique index idx_agent_tasks_proactive_dedup
  on public.agent_tasks (user_id, kind, ((created_at at time zone 'UTC')::date))
  where kind in (
    'spending_ahead', 'med_followup', 'bill_reminder',
    'warranty_reminder', 'home_weekly_digest', 'listener_gap_alert',
    'spend_forecast'
  );

create or replace function public._agent_spend_forecast_for_user(p_user uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $fn$
declare
  v_avg_weekly numeric;
  v_profile_age interval;
  v_currency text;
  v_balance numeric;
  v_due_soon numeric;
  v_projected numeric;
begin
  select b.avg_weekly_spending, now() - b.last_updated_at
    into v_avg_weekly, v_profile_age
  from public.user_behavior_profile b
  where b.user_id = p_user;

  -- ملف مش موجود أو قديم أو صفر = مفيش أساس للتوقع.
  if v_avg_weekly is null or v_avg_weekly <= 0 then
    return;
  end if;
  if v_profile_age is null or v_profile_age > interval '14 days' then
    return;
  end if;

  -- مرة كل ٧ أيام بالكتير.
  if exists (
    select 1 from public.agent_tasks t
    where t.user_id = p_user
      and t.kind = 'spend_forecast'
      and t.created_at > now() - interval '7 days'
  ) then
    return;
  end if;

  select u.currency into v_currency from public.zad_users u where u.id = p_user;

  -- نفس الرقم اللي الكارت بيعرضه للعميل — مش حساب تاني موازي.
  select public.zad_cash_balance(p_user) into v_balance;
  -- صفر أو سالب مش «رصيد قليل» — دي في الغالب «مفيش رصيد متسجّل أصلاً» (حساب ما ثبّتش
  -- نقطة رصيد). التجربة الجافة 2026-09-12 لقت حساب متوسطه ١٣٦ ورصيده 0.00، وكان هياخد
  -- تنبيه «ناقص ١٣٦» كل أسبوع مبني على رقم مش حقيقي. التنبيه ده للفرق بين رقمين
  -- معروفين؛ الحساب اللي مالوش رصيد بتغطيه تنبيهات الميزانية التانية.
  if v_balance is null or v_balance <= 0 then
    return;
  end if;

  -- الالتزامات المؤكَّدة المستحقة خلال ٧ أيام. `least(due_day, 28)` بيتفادى تواريخ
  -- غير صالحة (٣٠ فبراير) — تقريب مقصود، والفرق يوم أو يومين مايغيّرش قرار التنبيه.
  select coalesce(sum(x.amount), 0) into v_due_soon
  from (
    select o.amount,
           case
             when o.due_day >= extract(day from current_date)::int
               then make_date(
                 extract(year from current_date)::int,
                 extract(month from current_date)::int,
                 least(o.due_day, 28)
               )
             else (date_trunc('month', current_date) + interval '1 month')::date
                  + (least(o.due_day, 28) - 1)
           end as next_due
    from public.zad_obligations o
    where o.user_id = p_user
      and coalesce(o.active, true) is true
      and coalesce(o.confirmed, false) is true
      and o.amount is not null
      and o.due_day is not null
  ) x
  where x.next_due <= current_date + 7;

  v_projected := v_avg_weekly + v_due_soon;

  if v_projected <= v_balance then
    return; -- الأسبوع الجاي مغطّى، مفيش داعي لكلام
  end if;

  insert into public.agent_tasks (user_id, kind, task_description, scheduled_for)
  values (
    p_user,
    'spend_forecast',
    format(
      $t$[توقّع الأسبوع الجاي] على معدل صرفك المحسوب (%s %s في الأسبوع) وبإضافة %s %s التزامات مستحقة خلال ٧ أيام، المتوقع يخرج %s %s والمتاح دلوقتي %s %s.
يعني ناقص حوالي %s %s. اقترح على العميل تعديل بسيط في الأيام الجاية أو تأجيل مصروف مؤجَّل، وقول له الرقم اللي اتبنى عليه الكلام.$t$,
      round(v_avg_weekly, 2), coalesce(v_currency, ''),
      round(v_due_soon, 2), coalesce(v_currency, ''),
      round(v_projected, 2), coalesce(v_currency, ''),
      round(v_balance, 2), coalesce(v_currency, ''),
      round(v_projected - v_balance, 2), coalesce(v_currency, '')
    ),
    now()
  )
  on conflict (user_id, kind, ((created_at at time zone 'UTC')::date))
    where kind in (
      'spending_ahead', 'med_followup', 'bill_reminder',
      'warranty_reminder', 'home_weekly_digest', 'listener_gap_alert',
      'spend_forecast'
    )
  do nothing;
exception
  when unique_violation then
    return;
  when others then
    raise warning '_agent_spend_forecast_for_user: user % failed: % (%)', p_user, sqlerrm, sqlstate;
    return;
end;
$fn$;

comment on function public._agent_spend_forecast_for_user(uuid) is
  'توقّع أسبوعي: متوسط الصرف المحسوب + الالتزامات المستحقة خلال ٧ أيام مقابل الرصيد المتاح. '
  'بيسكت لو الملف السلوكي قديم أو الرصيد غير معروف — التخمين أسوأ من السكوت.';

-- ضمّ التنبيه للماسح. البوابة هنا هي عمر الملف السلوكي نفسه، مش السقف الشهري:
-- التوقع ده مبني على متوسط محسوب من المعاملات الحقيقية، فبيشتغل لأي حساب فيه نشاط
-- حتى لو ما صرّحش بسقف — نفس منطق توسيع البوابة في 20260912210000.
create or replace function public.agent_proactive_scan()
returns void
language plpgsql
security definer
set search_path to 'public'
as $fn$
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

      if v_has_limit then
        perform public._agent_spending_ahead_for_user(v_user);
      end if;

      perform public._agent_bill_reminder_for_user(v_user);
      perform public._agent_warranty_reminder_for_user(v_user);
      perform public._agent_listener_gap_alert_for_user(v_user);
      perform public._agent_spend_forecast_for_user(v_user);

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
$fn$;
