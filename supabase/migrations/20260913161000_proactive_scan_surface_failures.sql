-- 20260913161000 — الماسح الاستباقي بطّل يخبّي فشله.
--
-- الفشل كان بيتبلع على تلات طبقات، والتلاتة مع بعض خلّوا عطل حقيقي يبان "نجاح":
--
--   ١. أربع دوال مساعدة بتمسك أي خطأ جوّاها وترجع عادي:
--      `_agent_home_weekly_digest_for_user` بـ`when others then return;` **من غير حتى
--      warning**، والتلاتة التانيين (`spending_ahead`، `med_followup`، `spend_forecast`)
--      بـwarning في لوج Postgres محدش بيقراه. الفشل عمره مابيوصل للماسح.
--   ٢. `agent_proactive_scan()` بيرجّع `void`، وعدّاد الفشل بتاعه بيروح لـ`raise log` بس.
--   ٣. الإندبوينت (`run_proactive_scan` في zad-brain) مايقدرش يعرف حاجة، فبيرجّع
--      `200 {"ok":true}` دايمًا. والكرون بينادي بـ`net.http_post` async، فهو أخضر كمان.
--
-- القياس اللي كشفها (2026-09-13): من 09-06 لـ09-12 **صفر مهام استباقية لكل المستخدمين**،
-- و`cron.job_run_details` = 336/336 نجاح. عطل جدول ناقص (42P01) جوه دالة مساعدة كان
-- بيتبلع بالظبط بالطريقة دي.
--
-- الإصلاح:
--   أ. الدوال المساعدة الأربعة: فرع `when others` بقى `raise;`. فرع `unique_violation`
--      (منع التكرار في نفس اليوم — ده سلوك مقصود مش فشل) فضل `return` زي ما هو.
--   ب. الماسح بيلفّ **كل دالة مساعدة في subtransaction لوحدها**: فشل واحدة مايلغيش
--      شغل التانيين لنفس المستخدم (قبل كده ده كان مضمون بس لأنهم كانوا بيبلعوا).
--   ج. الماسح بيرجّع jsonb بالأرقام وعيّنة أخطاء، وبيسجّل صف
--      `proactive_scan_failure` في `zad_brain_health_alerts` + تنبيه تليجرام للأدمن مرة
--      في اليوم — نفس قناة `zad_brain_health_check` بالظبط.
--   د. المفتاح الأساسي لجدول التنبيهات بقى `(alert_date, kind)`.
--
-- (د) بتقفل عطل تاني من نفس العائلة: 20260913000000 ضاف عمود `kind` وتعليقه بيقول
-- "عشان نوعين التنبيه ما يخنقوش بعض في قفل مرة واحدة في اليوم" — بس المفتاح فضل
-- `(alert_date)` لوحده. يعني لو `failure_rate` اتسجّل الصبح، إدراج `proactive_silence`
-- في نفس اليوم كان بيقع على المفتاح، و`zad_brain_health_check` بيبلع الخطأ. الجدول
-- فيه صفر صفوف `proactive_silence` لحد النهاردة.

-- ── د. المفتاح الأساسي ───────────────────────────────────────────────────────
alter table public.zad_brain_health_alerts drop constraint zad_brain_health_alerts_pkey;
alter table public.zad_brain_health_alerts add primary key (alert_date, kind);

-- ── أ. الدوال المساعدة ترمي الفشل بدل ما تبلعه ─────────────────────────────
-- تعديل فرع الـhandler بس جوّه قاعدة البيانات، مش إعادة كتابة الأجسام يدويًا: الأجسام
-- فيها نصوص عربي وformat strings، ونسخها باليد مخاطرة تغيّر محتوى التنبيهات بصمت. لو
-- النمط مالقاش (يعني الجسم مختلف عن المتوقع)، الميجريشن بتقع في CI بدل ما تعدّي.
do $mig$
declare
  v_fn text;
  v_def text;
  v_new text;
begin
  foreach v_fn in array array[
    '_agent_home_weekly_digest_for_user',
    '_agent_spending_ahead_for_user',
    '_agent_med_followup_for_user',
    '_agent_spend_forecast_for_user'
  ] loop
    v_def := pg_get_functiondef(format('public.%I(uuid)', v_fn)::regprocedure);

    v_new := regexp_replace(
      v_def,
      'when others then\s*(raise warning [^;]*;\s*)?return;\s*end;\s*\$function\$\s*$',
      E'when others then\n    raise;\nend;\n$function$\n'
    );
    if v_new = v_def then
      raise exception 'proactive_scan_surface_failures: swallow handler not found in %', v_fn;
    end if;

    -- الـdigest مكانش عنده فرع unique_violation؛ من غيره تسابق نادر على قيد التكرار
    -- هيتحسب فشل. نفس الفرع اللي عند التلاتة التانيين.
    if v_new !~ 'when unique_violation then' then
      v_new := regexp_replace(v_new, 'when others then\n    raise;',
        E'when unique_violation then\n    return;\n  when others then\n    raise;');
    end if;

    execute v_new;
  end loop;
end;
$mig$;

-- ── ب + ج. الماسح ──────────────────────────────────────────────────────────
-- نوع الإرجاع اتغيّر (void → jsonb)، و`create or replace` مايقدرش يغيّره.
drop function if exists public.agent_proactive_scan();

create function public.agent_proactive_scan()
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

-- drop/create بيرجّع صلاحيات Supabase الافتراضية (anon/authenticated) — نفس قفل
-- 20260813190358: service_role بس.
revoke execute on function public.agent_proactive_scan() from public, anon, authenticated;
grant execute on function public.agent_proactive_scan() to service_role;

comment on function public.agent_proactive_scan() is
  'الماسح الاستباقي (حسابات auth.users الحقيقية بس). بيرجّع {scanned, failed, failed_users, cost_pct, skipped_orphans, errors}. أي فشل بيتسجّل '
  'في zad_brain_health_alerts (kind=proactive_scan_failure) مع تنبيه أدمن مرة في اليوم.';
