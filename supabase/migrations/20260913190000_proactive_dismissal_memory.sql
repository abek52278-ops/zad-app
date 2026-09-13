-- 20260913190000 — الذاكرة مربوطة بالماسح الاستباقي: رفض العميل بيوقف تكرار نفس التنبيه.
--
-- قبل كده (قياس 2026-09-13):
--   * الماسح بيمنع التكرار في **نفس اليوم** بس (idx_agent_tasks_proactive_dedup). توقّع الصرف
--     اللي العميل مش عايزه بيرجع بكرة وبعده، ومفيش أي طريقة يقول "كفاية".
--   * رسايل المبادرات بتوصل تليجرام نص من غير أزرار، فمفيش حتى إشارة رفض.
--   * آلية Task 28 (رفض بسبب → ملاحظة في zad_memory) موجودة لـzad_insights بس، والملاحظة
--     نص حر (`مش مهتم بتنبيهات زي "<عنوان>"`) — الموديل بيفهمه في الشات، والماسح (SQL)
--     مايقدرش يطابق عليه بأمان.
--   * «وضع الإجازة» (user_alert_snooze) اتشال عن قصد 09-05 لأن مكانش ليه كاتب. يعني مفيش
--     أي كتم متاح للعميل خالص.
--
-- الحل: نفس صف الذاكرة بيحمل الاتنين — ملاحظة مقروءة للموديل (scope = proactive_dismissal)
-- ومفتاح مهيكل للماسح (`subject_kind` + `suppress_until`). الأعمدة nullable، فكل الصفوف
-- والكتّاب الموجودين مايتأثروش.
--
-- المدد (قرار المستخدم 2026-09-13):
--   «مش مهم»      not_relevant → 30 يوم، بتتضاعف مع كل رفض لنفس النوع (30/60/120، سقف 180)
--   «عرفت خلاص»   timing       → 3 أيام
--   «الرقم غلط»   wrong_data   → 7 أيام، والملاحظة بتقول للموديل إن البيانات محتاجة مراجعة
--   متابعة الدوا (med_followup) سقفها 3 أيام أياً كان السبب — جرعة فايتة مش تفضيل.

alter table public.zad_memory
  add column if not exists subject_kind text,
  add column if not exists suppress_until timestamptz;

comment on column public.zad_memory.subject_kind is
  'نوع المهمة الاستباقية (agent_tasks.kind) اللي الملاحظة دي بتخصها — بيقراه agent_proactive_scan.';
comment on column public.zad_memory.suppress_until is
  'الماسح مابيبعتش subject_kind ده للعميل قبل الوقت ده. null = مفيش كتم.';

create index if not exists zad_memory_proactive_suppression_idx
  on public.zad_memory (user_id, subject_kind, suppress_until)
  where subject_kind is not null;

create or replace function public._agent_proactive_kind_label(p_kind text)
returns text
language sql
immutable
set search_path to 'public'
as $$
  select case p_kind
    when 'home_weekly_digest' then 'ملخص البيت'
    when 'spend_forecast'     then 'توقّع مصروف الأسبوع'
    when 'spending_ahead'     then 'الصرف أسرع من الميزانية'
    when 'med_followup'       then 'متابعة الدوا'
    when 'bill_reminder'      then 'تذكير الفواتير'
    when 'warranty_reminder'  then 'تذكير الضمان'
    when 'listener_gap_alert' then 'وقوف إشعارات البنك'
    else p_kind
  end;
$$;

create or replace function public._agent_proactive_suppressed(p_user uuid, p_kind text)
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $$
  select exists (
    select 1 from public.zad_memory m
    where m.user_id = p_user
      and m.subject_kind = p_kind
      and m.suppress_until > now()
  );
$$;

-- بيسجّل رفض العميل لرسالة استباقية. الملكية بتتحقق هنا (المهمة لازم تكون بتاعة p_user)،
-- مش في المتصل بس — البوت بيبعت user_id المحلول من chat_id، والدالة مابتصدّقش غير الجدول.
create or replace function public.zad_memory_record_proactive_dismissal(
  p_user uuid, p_task_id uuid, p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  v_kind text;
  v_label text;
  v_existing_id uuid;
  v_count int;
  v_days int;
  v_until timestamptz;
  v_note text;
  v_conf real;
begin
  if p_reason is null or p_reason not in ('not_relevant', 'timing', 'wrong_data') then
    return jsonb_build_object('ok', false, 'error', 'unknown_reason');
  end if;

  select kind into v_kind from public.agent_tasks where id = p_task_id and user_id = p_user;
  if v_kind is null then
    return jsonb_build_object('ok', false, 'error', 'task_not_found');
  end if;
  if v_kind = 'reminder' then
    return jsonb_build_object('ok', false, 'error', 'not_proactive');
  end if;

  v_label := public._agent_proactive_kind_label(v_kind);

  select m.id, m.evidence_count into v_existing_id, v_count
  from public.zad_memory m
  where m.user_id = p_user and m.scope = 'proactive_dismissal' and m.subject_kind = v_kind
  order by m.last_seen desc nulls last
  limit 1;
  v_count := coalesce(v_count, 0) + 1;

  v_days := case p_reason
    when 'not_relevant' then least(180, 30 * (2 ^ least(v_count - 1, 3))::int)
    when 'timing'       then 3
    else 7
  end;
  if v_kind = 'med_followup' then
    v_days := least(v_days, 3);
  end if;
  v_until := now() + make_interval(days => v_days);

  v_note := case p_reason
    when 'not_relevant' then format('العميل مش مهتم بتنبيهات «%s» — رفضها %s مرة. متبعتهاش ومتقترحهاش قبل %s.',
                                    v_label, v_count, to_char(v_until at time zone 'utc', 'YYYY-MM-DD'))
    when 'timing'       then format('العميل كان عارف بتنبيه «%s» وقت ما اتبعت — متكرروش قبل %s.',
                                    v_label, to_char(v_until at time zone 'utc', 'YYYY-MM-DD'))
    else                     format('العميل قال إن أرقام تنبيه «%s» غلط — البيانات المصدر محتاجة مراجعة قبل ما تتبني عليها.',
                                    v_label)
  end;
  v_conf := case p_reason when 'not_relevant' then 0.6 when 'timing' then 0.4 else 0.7 end;

  if v_existing_id is not null then
    update public.zad_memory
      set note = v_note, confidence = v_conf, evidence_count = v_count,
          last_seen = now(), suppress_until = v_until, embedding = null
      where id = v_existing_id;
  else
    insert into public.zad_memory (user_id, scope, note, confidence, evidence_count, last_seen, subject_kind, suppress_until)
    values (p_user, 'proactive_dismissal', v_note, v_conf, v_count, now(), v_kind, v_until);
  end if;

  return jsonb_build_object('ok', true, 'kind', v_kind, 'label', v_label, 'reason', p_reason,
                            'days', v_days, 'count', v_count, 'suppress_until', v_until);
end;
$$;

revoke execute on function public._agent_proactive_suppressed(uuid, text) from public, anon, authenticated;
revoke execute on function public.zad_memory_record_proactive_dismissal(uuid, uuid, text) from public, anon, authenticated;
grant execute on function public._agent_proactive_suppressed(uuid, text) to service_role;
grant execute on function public.zad_memory_record_proactive_dismissal(uuid, uuid, text) to service_role;

-- الماسح: نفس تعريف 20260913161000 بالظبط + فحص الكتم قبل كل مرحلة + عدّاد `suppressed`.
-- نفس نوع الإرجاع (jsonb)، فـcreate or replace كفاية والصلاحيات بتفضل.
create or replace function public.agent_proactive_scan()
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $fn$
declare
  v_user uuid;
  v_has_limit boolean;
  v_cost_pct int := public.agent_brain_cost_guard();
  v_stages text[];
  v_stage text;
  v_scanned int := 0;
  v_failed int := 0;             -- عدد (مستخدم × دالة) اللي فشلت
  v_failed_users uuid[] := '{}';
  v_errors jsonb := '[]'::jsonb; -- أول ٥ أخطاء بس، عشان الرد والجدول مايتضخّموش
  v_orphans int;
  v_suppressed int := 0;         -- مراحل اتخطّت لأن العميل رفض النوع ده (zad_memory)
  v_result jsonb;
  v_first_today boolean;
  v_admin record;
  v_body text;
begin
  -- حسابات حقيقية بس. `zad_users` مالوش FK على `auth.users`، وفيه صفوف يتيمة (٢ من ٦
  -- يوم 2026-09-13 — مش من `delete-account` اللي بيمسح zad_users الأول، غالبًا حسابات
  -- اتمسحت من الداشبورد). `agent_tasks.user_id` عليه FK على auth.users، فأي دالة مساعدة
  -- بتحاول تكتب مهمة ليتيم بتقع بـ23503. التجربة الجافة للميجريشن دي هي اللي كشفت ده:
  -- `home_weekly_digest` كان بيقع كل ساعة من زمان، والـ`when others then return;` كان
  -- بيبلعه. اليتامى بيتعدّوا في النتيجة (`skipped_orphans`) بدل ما يتفحصوا أو يتمسحوا —
  -- مسح بيانات مستخدم قرار مش شغل ماسح.
  select count(*) into v_orphans
  from public.zad_users u
  where not exists (select 1 from auth.users a where a.id = u.id);

  for v_user, v_has_limit in
    select u.id,
           (u.limit_confirmed_at is not null and coalesce(u.monthly_limit, 0) > 0)
    from public.zad_users u
    join auth.users a on a.id = u.id
  loop
    v_scanned := v_scanned + 1;

    v_stages := array['bill_reminder', 'warranty_reminder', 'listener_gap_alert', 'spend_forecast'];
    if v_has_limit then
      v_stages := array['spending_ahead'] || v_stages;
    end if;
    if v_cost_pct < 95 then
      v_stages := v_stages || array['home_weekly_digest'];
    end if;

    foreach v_stage in array v_stages loop
      -- العميل رفض النوع ده من تليجرام ولسه جوه مدة الكتم — مابنبعتهوش تاني.
      if public._agent_proactive_suppressed(v_user, v_stage) then
        v_suppressed := v_suppressed + 1;
        continue;
      end if;
      begin
        -- %I على قايمة ثابتة فوق، مش مدخل خارجي.
        execute format('select public.%I($1)', '_agent_' || v_stage || '_for_user') using v_user;
      exception when others then
        v_failed := v_failed + 1;
        if not v_user = any(v_failed_users) then
          v_failed_users := v_failed_users || v_user;
        end if;
        if jsonb_array_length(v_errors) < 5 then
          v_errors := v_errors || jsonb_build_object(
            'stage', v_stage, 'sqlstate', sqlstate, 'error', left(sqlerrm, 200));
        end if;
        raise warning 'agent_proactive_scan: % for user % failed: % (%)', v_stage, v_user, sqlerrm, sqlstate;
      end;
    end loop;
  end loop;

  if v_cost_pct < 95 then
    for v_user in
      select distinct p.user_id from public.zad_pharmacy_items p
      join auth.users a on a.id = p.user_id
      where coalesce(p.is_recurring, false) is true
    loop
      if public._agent_proactive_suppressed(v_user, 'med_followup') then
        v_suppressed := v_suppressed + 1;
        continue;
      end if;
      begin
        perform public._agent_med_followup_for_user(v_user);
      exception when others then
        v_failed := v_failed + 1;
        if not v_user = any(v_failed_users) then
          v_failed_users := v_failed_users || v_user;
        end if;
        if jsonb_array_length(v_errors) < 5 then
          v_errors := v_errors || jsonb_build_object(
            'stage', 'med_followup', 'sqlstate', sqlstate, 'error', left(sqlerrm, 200));
        end if;
        raise warning 'agent_proactive_scan: med_followup for user % failed: % (%)', v_user, sqlerrm, sqlstate;
      end;
    end loop;
  end if;

  v_result := jsonb_build_object(
    'scanned', v_scanned,
    'failed', v_failed,
    'failed_users', coalesce(array_length(v_failed_users, 1), 0),
    'cost_pct', v_cost_pct,
    'skipped_orphans', v_orphans,
    'suppressed', v_suppressed,
    'errors', v_errors
  );

  raise log 'agent_proactive_scan: %', v_result;

  if v_failed > 0 then
    -- صف واحد لكل يوم؛ الأرقام بتتحدّث بآخر فحص. `xmax = 0` = الصف اتدرج دلوقتي
    -- (مش اتحدّث)، فالتنبيه بيتبعت مرة واحدة في اليوم مش كل ساعة.
    insert into public.zad_brain_health_alerts (alert_date, kind, total_runs, bad_runs, bad_pct, top_error)
    values (
      current_date,
      'proactive_scan_failure',
      v_scanned,
      coalesce(array_length(v_failed_users, 1), 0),
      round(coalesce(array_length(v_failed_users, 1), 0)::numeric * 100 / greatest(v_scanned, 1)),
      left((v_errors -> 0 ->> 'stage') || ': ' || (v_errors -> 0 ->> 'error'), 200)
    )
    on conflict (alert_date, kind) do update
      set total_runs = excluded.total_runs,
          bad_runs   = excluded.bad_runs,
          bad_pct    = excluded.bad_pct,
          top_error  = excluded.top_error
    returning (xmax = 0) into v_first_today;

    if v_first_today then
      v_body := 'الماسح الاستباقي: ' || v_failed || ' فشل عند '
                || coalesce(array_length(v_failed_users, 1), 0) || ' من ' || v_scanned
                || ' حساب. ده معناه إن تنبيهات ماتبعتتش.'
                || coalesce(E'\n\nأول خطأ:\n' || (v_errors -> 0 ->> 'stage') || ' — '
                            || (v_errors -> 0 ->> 'error'), '');

      for v_admin in
        select b.user_id from public.dashboard_admins a
        join public.telegram_bindings b on b.user_id = a.user_id and b.bound_at is not null
      loop
        perform net.http_post(
          url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
          headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'X-Realtime-Push-Secret', public.zad_cron_secret('zad_realtime_push_secret')
          ),
          body := jsonb_build_object('user_id', v_admin.user_id,
                                     'title', '🚨 الماسح الاستباقي', 'body', v_body),
          timeout_milliseconds := 15000
        );
      end loop;
    end if;
  end if;

  return v_result;
end;
$fn$;

comment on function public.agent_proactive_scan() is
  'الماسح الاستباقي (حسابات auth.users الحقيقية بس، وبيحترم الكتم في zad_memory). بيرجّع {scanned, failed, failed_users, cost_pct, skipped_orphans, suppressed, errors}.';
