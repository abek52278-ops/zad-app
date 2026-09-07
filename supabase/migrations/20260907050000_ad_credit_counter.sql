-- ── رصيد الإعلانات: عدّاد رسائل صريح بدل جلسة زمنية مفتوحة ──────────────────
--
-- الوضع قبل كده: ٣ إعلانات = دفعة واحدة بتدي chat_left + 5 **و** تفتح
-- brain_session_expires_at لـ١٢ ساعة. والـ١٢ ساعة هي اللي كانت بتفتح العقل فعلاً
-- (handleAgentTurn بيقرا hasActiveAdSession)، بينما chat_left **ماكانش بيتقرا في
-- أي مكان** غير data class في أندرويد — رقم للعرض بس.
--
-- إعلان واحد بيفتح ١٢ ساعة استخدام غير محدود = تكلفة نداءات مفتوحة مقابل انطباع
-- واحد. العدّاد الصريح بيربط التكلفة بالمنفعة: رسالة مقابل رصيد.
--
-- المكافأة تصاعدية داخل اليوم (٣ ثم ٤ ثم ٥…) عشان تحفّز المشاهدة المتكررة،
-- بسقف لكل إعلان عشان ما تجريش بلا حد.

create or replace function public.zad_ad_reward_grant(p_user uuid default null)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  uid uuid;
  today_count int;
  reward int;
  new_left int;
  -- ١٠/يوم بدل ٣٠: السقف القديم كان عالي لدرجة إنه بيقرّب من حدود AdMob
  -- للحركة غير الصالحة. و٦٠ ثانية بدل ١٠: عشر ثواني بين إعلان وإعلان نمط
  -- بيتقري آلي، ودي أشيع علامة على الـspam.
  daily_cap constant int := 10;
  min_gap   constant interval := interval '60 seconds';
  base_reward constant int := 3;
  max_per_ad  constant int := 10;
begin
  if auth.uid() is null and coalesce(auth.role(), '') <> 'service_role' then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;
  if auth.uid() is not null and p_user is not null and p_user <> auth.uid() then
    return jsonb_build_object('granted', false, 'reason', 'forbidden');
  end if;

  uid := coalesce(auth.uid(), p_user);
  if uid is null then
    return jsonb_build_object('granted', false, 'reason', 'no user');
  end if;

  if exists (
    select 1 from public.zad_ad_grants
    where user_id = uid and granted_at > now() - min_gap
  ) then
    return jsonb_build_object('granted', false, 'reason', 'too_fast');
  end if;

  select count(*) into today_count from public.zad_ad_grants
  where user_id = uid and granted_at >= (now() at time zone 'utc')::date;

  if today_count >= daily_cap then
    return jsonb_build_object('granted', false, 'reason', 'daily_ad_cap',
                              'daily_cap', daily_cap, 'ads_today', today_count);
  end if;

  -- تصاعدي: الإعلان الأول ٣، التاني ٤، وهكذا لحد السقف لكل إعلان.
  reward := least(base_reward + today_count, max_per_ad);

  perform public.zad_entitlement_refresh(uid);
  insert into public.zad_ad_grants (user_id) values (uid);

  update public.zad_entitlements set
    chat_left = chat_left + reward,
    ad_watch_count = today_count + 1,
    -- الجلسة الزمنية اتلغت: الرصيد بقى بالعداد. بنصفّرها عشان أي كود قديم
    -- لسه بيقراها ما يفتحش الباب على الفاضي.
    brain_session_expires_at = null,
    updated_at = now()
  where user_id = uid
  returning chat_left into new_left;

  return jsonb_build_object(
    'granted', true,
    'reward', reward,
    'chat_left', new_left,
    'ads_today', today_count + 1,
    'daily_cap', daily_cap,
    -- الرقم اللي الزر بيعرضه للمشاهدة الجاية. null = خلص السقف.
    'next_reward', case when today_count + 1 >= daily_cap then null
                        else least(base_reward + today_count + 1, max_per_ad) end
  );
end;
$function$;

-- ── الاستهلاك ────────────────────────────────────────────────────────────────
-- بينقص رصيد واحد **ذرّيًا** ويرجع هل اتسمح ولا لأ. القراءة ثم الكتابة من
-- الكود كانت هتسمح بنداءين متوازيين ياخدوا نفس الرصيد الأخير.
create or replace function public.zad_chat_credit_consume(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  left_after int;
begin
  if coalesce(auth.role(), '') <> 'service_role' then
    return jsonb_build_object('allowed', false, 'reason', 'forbidden');
  end if;

  update public.zad_entitlements
     set chat_left = chat_left - 1, updated_at = now()
   where user_id = p_user and chat_left > 0
  returning chat_left into left_after;

  if left_after is null then
    return jsonb_build_object('allowed', false, 'reason', 'no_credit', 'chat_left', 0);
  end if;
  return jsonb_build_object('allowed', true, 'chat_left', left_after);
end;
$function$;

revoke all on function public.zad_chat_credit_consume(uuid) from public, anon, authenticated;
