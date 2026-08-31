-- Task 30.4 — قفل الثغرات اللي فتحها التوسّع بعد كنسة أغسطس.
--
-- المسح الشامل (2026-08-31) لقى تمن دوال SECURITY DEFINER قابلة للنداء من anon.
-- كلها اتضافت **بعد** ميجريشنز التأمين التلاتة (20260813190358، 20260816230000،
-- 20260817003500)، فمحدش قفلها. دي مشكلة تنظيمية متكررة مش خطأ تصميم: Postgres
-- بيدي EXECUTE لـ PUBLIC على كل دالة جديدة تلقائياً، فأي دالة SECURITY DEFINER
-- جديدة بتفتح لوحدها من غير ما حد يقصد.
--
-- ⚠️ الفخ الموثّق في 20260817003500: المنحة **لـ PUBLIC مش لـ anon**. الميجريشن
-- 20260816230000 عملت `revoke ... from anon` وكانت بلا أثر تماماً، والثغرة فضلت
-- مفتوحة شهر كامل والملاحظات مسجّلة إنها "اتقفلت". كل revoke هنا بتسمي PUBLIC
-- صراحةً، وفي آخر الملف تأكيد بيفشل الميجريشن لو فضلت مفتوحة.
--
-- الأخطر في القايمة: zad_media_pass_grant — بتدي اشتراك وسائط 24 ساعة، وجواها
-- coalesce(auth.uid(), p_user). الحارس اللي جواها بيشتغل للمسجَّلين بس، فأي حد
-- من غير تسجيل دخول كان يقدر يبعت uuid أي عميل و: يدي نفسه اشتراك، أو يحرق
-- حد الاثنعشر منحة اليومي بتاع الضحية فيمنعه هو من استخدامها.

-- ───────────────────────────────────────────────────────────────────────────
-- 1) دوال الحلقة الاستباقية الداخلية
-- ───────────────────────────────────────────────────────────────────────────
-- دي بتتنده بـ `perform` من جوه agent_proactive_scan() بس (20260824152000،
-- 20260824170000). الدالة الخارجية SECURITY DEFINER فبتشتغل بصلاحيات مالكها ومش
-- بتستشير منح EXECUTE أصلاً — فسحبها من PUBLIC مش بيكسر الحلقة، بيقفل الباب
-- بتاع /rest/v1/rpc/<name> اللي كان مفتوح بالغلط ومحدش محتاجه.
revoke execute on function public._agent_bill_reminder_for_user(uuid) from public;
revoke execute on function public._agent_warranty_reminder_for_user(uuid) from public;
revoke execute on function public._agent_home_weekly_digest_for_user(uuid) from public;
revoke execute on function public._agent_listener_gap_alert_for_user(uuid) from public;
grant execute on function public._agent_bill_reminder_for_user(uuid) to service_role;
grant execute on function public._agent_warranty_reminder_for_user(uuid) to service_role;
grant execute on function public._agent_home_weekly_digest_for_user(uuid) to service_role;
grant execute on function public._agent_listener_gap_alert_for_user(uuid) to service_role;

-- ───────────────────────────────────────────────────────────────────────────
-- 2) دالة تريجر
-- ───────────────────────────────────────────────────────────────────────────
-- agent_goal_touch_progress() متربطة بـ trg_agent_goal_progress. التريجر بيشتغل
-- بصلاحيات مالك الجدول ومش بيبص لمنح EXECUTE، فمحدش محتاج ينديها عبر PostgREST
-- ومفيش حاجة هتقع. نفس منطق التلات دوال في 20260817003500.
revoke execute on function public.agent_goal_touch_progress() from public;

-- ───────────────────────────────────────────────────────────────────────────
-- 3) دوال المهارات — سيرفر-سايد بس
-- ───────────────────────────────────────────────────────────────────────────
-- zad_skill_upsert بيتنده من zad-brain/index.ts:2131 بمفتاح الخدمة. zad_skill_touch
-- مفيهوش نداء من TypeScript ولا Kotlin خالص. مفيش سبب إن العميل يوصلهم: اللي
-- بيقرر إن العقل "اتعلم" مهارة هو العقل، مش الجهاز.
revoke execute on function public.zad_skill_upsert(uuid, text, text, real) from public;
revoke execute on function public.zad_skill_touch(uuid, text) from public;
grant execute on function public.zad_skill_upsert(uuid, text, text, real) to service_role;
grant execute on function public.zad_skill_touch(uuid, text) to service_role;

-- ───────────────────────────────────────────────────────────────────────────
-- 4) zad_media_pass_grant — سحب + تقوية الجسم نفسه
-- ───────────────────────────────────────────────────────────────────────────
-- التطبيق بينديها بتوكن العميل (SupabaseRepo.kt:2140 بعد مشاهدة إعلان)، فـ
-- authenticated لازم تفضل. السحب من PUBLIC بيشيل anon.
revoke execute on function public.zad_media_pass_grant(uuid) from public;
grant execute on function public.zad_media_pass_grant(uuid) to authenticated, service_role;

-- والجسم نفسه بيتقوّى كمان (دفاع في العمق): من غير التعديل ده، الدالة تفضل تثق
-- في p_user لما auth.uid() تبقى null — وأي منحة PUBLIC ترجع بالغلط في المستقبل
-- تفتح الثغرة تاني. دلوقتي: p_user مقبول بس لو النداء بمفتاح الخدمة.
create or replace function public.zad_media_pass_grant(p_user uuid default null)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  uid uuid;
  last_at timestamptz;
  today_count int;
  min_gap constant interval := interval '10 seconds';
  daily_cap constant int := 12; -- 12 يوم-بوابة من الإعلانات كحد أقصى يومياً
begin
  -- بوابة الهوية: من غير جلسة مسجّلة، p_user مقبول بس من مفتاح الخدمة.
  -- قبل كده كان coalesce(auth.uid(), p_user) على طول — ودي كانت الثغرة.
  if auth.uid() is null and coalesce(auth.role(), '') <> 'service_role' then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;
  if auth.uid() is not null and p_user is not null and p_user <> auth.uid() then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;

  uid := coalesce(auth.uid(), p_user);
  if uid is null then return jsonb_build_object('granted', false, 'reason', 'no user'); end if;

  -- المشتركون أصلاً عندهم وسائط — مش محتاجين بوابة
  if exists (select 1 from public.zad_entitlements e
             join public.zad_tiers t on t.tier = e.tier
             where e.user_id = uid and t.telegram_media = true) then
    return jsonb_build_object('granted', true, 'already_paid', true,
                              'media_pass_expires_at', null);
  end if;

  select max(granted_at) into last_at from public.zad_ad_grants where user_id = uid;
  if last_at is not null and last_at > now() - min_gap then
    return jsonb_build_object('granted', false, 'reason', 'too_fast');
  end if;

  select count(*) into today_count from public.zad_ad_grants
  where user_id = uid and granted_at >= (now() at time zone 'utc')::date;
  if today_count >= daily_cap then
    return jsonb_build_object('granted', false, 'reason', 'daily_ad_cap');
  end if;

  insert into public.zad_ad_grants (user_id) values (uid);
  update public.zad_entitlements set
    media_pass_expires_at = now() + interval '24 hours',
    updated_at = now()
  where user_id = uid;

  return jsonb_build_object('granted', true, 'media_pass_expires_at', now() + interval '24 hours');
end;
$function$;

-- create or replace بيرجّع منحة PUBLIC الافتراضية، فالسحب لازم يتكرر بعده.
revoke execute on function public.zad_media_pass_grant(uuid) from public;
grant execute on function public.zad_media_pass_grant(uuid) to authenticated, service_role;

-- ───────────────────────────────────────────────────────────────────────────
-- 5) search_path للدالتين الناقصتين
-- ───────────────────────────────────────────────────────────────────────────
-- اتضافوا في 20260829020000 وفاتوا من تقوية الـ search_path اللي كل الدوال
-- التانية واخداها. ALTER أنضف من إعادة الكتابة — الجسم ميتلمسش.
alter function public.zad_notif_fuzzy_key(text, text) set search_path to 'public';
alter function public.zad_notif_fuzzy_key_fill() set search_path to 'public';

-- ───────────────────────────────────────────────────────────────────────────
-- 6) التلات جداول اللي RLS مفعّل عليها من غير أي policy
-- ───────────────────────────────────────────────────────────────────────────
-- dashboard_admins / zad_ad_grants / zad_orphaned_rows: RLS شغال ومفيش policy،
-- يعني دلوقتي fail-closed ومحدش بيوصلهم — كويس. بس منح CRUD الكاملة لـ anon و
-- authenticated لسه قاعدة عليهم، فأول policy تتضاف أو أول `disable row level
-- security` وقت ديباج بتخليهم مكتوبين للعالم. والتلاتة مقروءين سيرفر-سايد بس:
--   dashboard_admins  → is_dashboard_admin() وهي SECURITY DEFINER بتتخطى RLS
--   zad_ad_grants     → zad_media_pass_grant() بتكتب فيه، نفس الحكاية
--   zad_orphaned_rows → أرشيف الميجريشنز الهدّامة
-- السحب بيخلي التصميم صريح بدل ما يكون معتمد على غياب policy.
revoke all on table public.dashboard_admins from anon, authenticated;
revoke all on table public.zad_ad_grants from anon, authenticated;
revoke all on table public.zad_orphaned_rows from anon, authenticated;

comment on table public.dashboard_admins is
  'سيرفر-سايد بس. بيتقرا عبر is_dashboard_admin() (security definer). RLS مفعّل بلا policy عمداً + المنح مسحوبة — مش سهو.';
comment on table public.zad_ad_grants is
  'سيرفر-سايد بس. بيتكتب من zad_media_pass_grant() (security definer). RLS مفعّل بلا policy عمداً + المنح مسحوبة.';
comment on table public.zad_orphaned_rows is
  'أرشيف سيرفر-سايد للصفوف قبل الميجريشنز الهدّامة. RLS مفعّل بلا policy عمداً + المنح مسحوبة.';

-- ───────────────────────────────────────────────────────────────────────────
-- 7) تأكيد — الميجريشن تفشل بدل ما تكدب
-- ───────────────────────────────────────────────────────────────────────────
-- السبب الوحيد لوجود ده: 20260816230000 "قفلت" الثغرة ومقفلتهاش، والملاحظات
-- سجّلتها كمقفولة. مفيش سحب هنا بيتصدَّق من غير ما يتقاس.
do $$
declare
  still_open text;
begin
  select string_agg(p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')', ', ')
    into still_open
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
  where n.nspname = 'public' and p.prosecdef
    and has_function_privilege('anon', p.oid, 'EXECUTE');

  if still_open is not null then
    raise exception 'ما زالت دوال SECURITY DEFINER قابلة للنداء من anon: %', still_open;
  end if;
end $$;

do $$
declare
  leftover text;
begin
  select string_agg(table_name || ':' || grantee || ':' || privilege_type, ', ')
    into leftover
  from information_schema.role_table_grants
  where table_schema = 'public'
    and table_name in ('dashboard_admins', 'zad_ad_grants', 'zad_orphaned_rows')
    and grantee in ('anon', 'authenticated');

  if leftover is not null then
    raise exception 'منح متبقية على الجداول بلا policy: %', leftover;
  end if;
end $$;
