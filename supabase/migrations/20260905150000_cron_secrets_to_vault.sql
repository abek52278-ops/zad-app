-- =====================================================
-- بند BE-03 (النص التاني) — الحوامل في الداتابيز تقرا الأسرار من Supabase Vault
-- بدل ما تكون مكتوبة نص صريح.
--
-- النص الأول (كوميت 5a1c094) خلّى الإيدج فانكشنز تقرا من project secrets. الملف ده
-- بيكمّل الناحية التانية: الجهات اللي **بتبعت** السر.
--
-- ليه ملفات الميجريشن التسعة القديمة ماتعدّلتش: هي **اتطبقت خلاص**، و`db push` مش
-- بيعيد تشغيل ميجريشن مطبَّقة. الحوامل الحقيقية عايشة في الداتابيز نفسها:
--
--   cron.job 4   telegram-checkin-daily            X-Checkin-Cron-Secret
--   cron.job 5   telegram-subscription-alerts      X-Subscription-Cron-Secret
--   cron.job 12  parent-digest-weekly              X-Parent-Digest-Cron-Secret
--   notify_telegram_on_insight()                   X-Realtime-Push-Secret
--   notify_telegram_on_transaction()               X-Realtime-Push-Secret
--   zad_brain_health_check()                       X-Realtime-Push-Secret
--   notify_telegram_on_low_stock()                 X-Live-Checkin-Secret
--
-- القيم نفسها **مش هنا ومش في git**: اتحطّت في Vault عن طريق `execute_sql` (مش
-- ميجريشن) عشان مايبقاش ليها أي أثر في التاريخ. الملف ده بيشاور عليها **بالاسم بس**.
--
-- الأربع دوال بتتعدّل بـ DO block بيقرا `pg_get_functiondef` الحي ويعمل استبدال واحد
-- على تعبير الـheaders. مقصود إني مانقلش أجسام الدوال بإيدي — أكبرها ٣٧٦٤ حرف، ونقلها
-- يدوي فرصة تلف من غير أي مكسب. اتأكدت قبل كده إن نمط الـheaders مطابق في الأربعة.
-- =====================================================

-- ── قارئ الأسرار ──────────────────────────────────────────────────────────────
-- SECURITY DEFINER عشان الدوال والكرونات توصل لـ vault، و`search_path` مقفول على
-- سلسلة فاضية عشان مايتخطفش بـschema في الـpath.
--
-- ⚠️ الـrevoke تحت **جزء أساسي من الأمان مش تنضيف**: من غيره أي مستخدم مسجّل يقدر
-- ينده `/rest/v1/rpc/zad_cron_secret` ويقرا كل الأسرار — وده كان هيبقى أسوأ من
-- المشكلة اللي بنصلحها. الدالة دي للاستخدام الداخلي من الداتابيز بس.
create or replace function public.zad_cron_secret(p_name text)
returns text
language sql
stable
security definer
set search_path to ''
as $fn$
  select decrypted_secret from vault.decrypted_secrets where name = p_name
$fn$;

revoke all on function public.zad_cron_secret(text) from public;
revoke all on function public.zad_cron_secret(text) from anon;
revoke all on function public.zad_cron_secret(text) from authenticated;

-- ── الأربع دوال trigger ───────────────────────────────────────────────────────
do $mig$
declare
  r        record;
  new_def  text;
  n_done   int := 0;
begin
  for r in
    select oid, proname, pg_get_functiondef(oid) as def
    from pg_proc
    where pronamespace = 'public'::regnamespace
      and proname in (
        'notify_telegram_on_insight',
        'notify_telegram_on_transaction',
        'zad_brain_health_check',
        'notify_telegram_on_low_stock'
      )
  loop
    new_def := r.def;

    new_def := replace(
      new_def,
      '''{"Content-Type":"application/json","X-Realtime-Push-Secret":"7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094"}''::jsonb',
      'jsonb_build_object(''Content-Type'', ''application/json'', ''X-Realtime-Push-Secret'', public.zad_cron_secret(''zad_realtime_push_secret''))'
    );

    new_def := replace(
      new_def,
      '''{"Content-Type":"application/json","X-Live-Checkin-Secret":"58dda37fa693d2351ab038f07303fb9b621ce983c597046de7c7edc2b6d283a6"}''::jsonb',
      'jsonb_build_object(''Content-Type'', ''application/json'', ''X-Live-Checkin-Secret'', public.zad_cron_secret(''zad_live_checkin_secret''))'
    );

    if new_def <> r.def then
      execute new_def;
      n_done := n_done + 1;
    else
      raise warning 'BE-03: % لم يتغيّر — نمط الـheaders مش زي المتوقع', r.proname;
    end if;
  end loop;

  -- الأربعة لازم يتغيّروا. لو أقل، يبقى فيه دالة نمطها اتغيّر ولسه شايلة السر القديم،
  -- والفشل هنا أحسن بكتير من ميجريشن بتعدّي وهي سايبة سر مكشوف ورا.
  if n_done <> 4 then
    raise exception 'BE-03: اتعدّل % دالة من ٤ المتوقعة', n_done;
  end if;
end
$mig$;

-- ── التلات كرونات ─────────────────────────────────────────────────────────────
-- إعادة جدولة بنفس الاسم بتستبدل الجوب الموجود (نفس نمط 20260901160000).
select cron.schedule(
  'telegram-checkin-daily',
  '0 18 * * *',
  $job$
  select net.http_post(
      url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=daily_checkins',
      headers:=jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Checkin-Cron-Secret', public.zad_cron_secret('zad_checkin_cron_secret')
      ),
      body:='{}'::jsonb,
      timeout_milliseconds:=55000
    ) as request_id;
  $job$
);

select cron.schedule(
  'telegram-subscription-alerts-daily',
  '0 9 * * *',
  $job$
  select net.http_post(
      url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=subscription_alerts',
      headers:=jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Subscription-Cron-Secret', public.zad_cron_secret('zad_subscription_cron_secret')
      ),
      body:='{}'::jsonb,
      timeout_milliseconds:=55000
    ) as request_id;
  $job$
);

select cron.schedule(
  'parent-digest-weekly',
  '0 9 * * 5',
  $job$
  select net.http_post(
    url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/parent-digest',
    headers := jsonb_build_object(
      'X-Parent-Digest-Cron-Secret', public.zad_cron_secret('zad_parent_digest_cron_secret'),
      'Content-Type', 'application/json'
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 55000
  );
  $job$
);

-- ── حارس ──────────────────────────────────────────────────────────────────────
-- لو أي حامل لسه شايل قيمة نص صريح، الميجريشن تفشل بدل ما تعدّي وهي سايبة السر مكشوف.
do $check$
declare
  n_bad int;
begin
  select count(*) into n_bad
  from (
    select command as body from cron.job where jobid in (4, 5, 12)
    union all
    select pg_get_functiondef(oid) from pg_proc
    where pronamespace = 'public'::regnamespace
      and proname in (
        'notify_telegram_on_insight', 'notify_telegram_on_transaction',
        'zad_brain_health_check', 'notify_telegram_on_low_stock'
      )
  ) s
  where s.body ~ '(5bbebc0b|2ceb272a|7e78ce0a|58dda37f|3302c068)';

  if n_bad > 0 then
    raise exception 'BE-03: لسه % حامل شايل سر نص صريح', n_bad;
  end if;
end
$check$;
