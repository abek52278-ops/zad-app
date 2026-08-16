-- ذاكرة السلوك — الحلقة الناقصة في "التعلّم المستمر".
--
-- الأنماط السلوكية كانت بتتحسب على التليفون (ZadViewModel.updateBehaviorPatterns) وبتعيش
-- في الذاكرة وبس. العقل على السيرفر والبوت على تليجرام ماكانوش يشوفوها خالص، فكل واحد
-- فيهم كان بيستنتج من الصفوف الخام من أول وجديد — أو مايستنتجش.
--
-- **دالة مشتقّة، مش جدول.** ده قرار مبني على درس اليوم نفسه: كل حقل احتاج حد يملاه فضل
-- فاضي — `due_day` في الاشتراكات، `currency` و`price` في المعاملات والأدوية،
-- `last_lat` في المستخدمين. جدول أنماط محتاج كاتب على الجهاز هيقع في نفس الحفرة أول ما
-- الكتابة تفشل مرة. الحساب من المعاملات مالوش كاتب ينساه، وبيبقى صحيح لحظة ما يتقرا.
--
-- كل رقم هنا بيتحسب من `zad_transactions` و`zad_consumption` اللي موجودين أصلاً — مفيش
-- جدول جديد يتزامن ومفيش مسار كتابة جديد يقع.

create or replace function public.zad_behavior_patterns(p_user uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  v_tz text;
  v_today date;
  v_out jsonb := '{}'::jsonb;
  v_categories jsonb;
  v_weekend jsonb;
  v_hours jsonb;
  v_merchants jsonb;
  v_depletion jsonb;
  v_tx_count int;
begin
  if auth.uid() is not null and auth.uid() <> p_user then
    raise exception 'not_authorized';
  end if;

  select public.zad_market_timezone(country) into v_tz from public.zad_users where id = p_user;
  v_tz := coalesce(v_tz, 'UTC');
  v_today := (now() at time zone v_tz)::date;

  select count(*) into v_tx_count
  from public.zad_transactions
  where user_id = p_user and txn_kind = 'expense'
    and (created_at at time zone v_tz)::date > v_today - 90;

  -- تحت ٨ معاملات في ٩٠ يوم مفيش نمط، فيه صدفة. الرقم بيترجع صريح عشان القارئ يفرّق
  -- بين "اتعلّمنا إنك مابتصرفش" و"لسه مامعناش كفاية نقول".
  if v_tx_count < 8 then
    return jsonb_build_object(
      'learning', true, 'transactions_seen', v_tx_count,
      'note', 'لسه مفيش معاملات كفاية لاستخراج نمط موثوق');
  end if;

  -- الفئة: المتوسط وكل كام يوم بتتكرر — ده اللي بيخلي "بقالك ١٢ يوم مانزلتش بقالة" ممكنة.
  select coalesce(jsonb_agg(x order by (x->>'total')::numeric desc), '[]'::jsonb) into v_categories
  from (
    select jsonb_build_object(
      'category', coalesce(nullif(category,''), 'أخرى'),
      'total', round(sum(amount)::numeric, 2),
      'avg', round(avg(amount)::numeric, 2),
      'count', count(*),
      'every_days', round((90.0 / greatest(count(*),1))::numeric, 1)) as x
    from public.zad_transactions
    where user_id = p_user and txn_kind = 'expense'
      and (created_at at time zone v_tz)::date > v_today - 90
    group by 1 having count(*) >= 2
    limit 8
  ) c;

  -- الويكند: الجمعة والسبت في أسواق زاد، مش السبت والأحد.
  select jsonb_build_object(
    'weekend_total', round(coalesce(sum(amount) filter (where extract(dow from (created_at at time zone v_tz)) in (5,6)),0)::numeric,2),
    'weekday_total', round(coalesce(sum(amount) filter (where extract(dow from (created_at at time zone v_tz)) not in (5,6)),0)::numeric,2))
    into v_weekend
  from public.zad_transactions
  where user_id = p_user and txn_kind = 'expense'
    and (created_at at time zone v_tz)::date > v_today - 90;

  -- ساعات النزول المعتادة — ده اللي بيخلي التنبيه يوصل في وقته مش في وقت عشوائي.
  select coalesce(jsonb_agg(x order by (x->>'count')::int desc), '[]'::jsonb) into v_hours
  from (
    select jsonb_build_object(
      'hour', extract(hour from (created_at at time zone v_tz))::int,
      'count', count(*)) as x
    from public.zad_transactions
    where user_id = p_user and txn_kind = 'expense'
      and (created_at at time zone v_tz)::date > v_today - 90
    group by 1 having count(*) >= 2
    order by count(*) desc limit 4
  ) h;

  -- متوسط ما بيتصرف في كل تاجر — الأساس اللي أي انحراف بيتقاس عليه.
  select coalesce(jsonb_agg(x order by (x->>'visits')::int desc), '[]'::jsonb) into v_merchants
  from (
    select jsonb_build_object(
      'merchant', merchant_name,
      'visits', count(*),
      'avg_spend', round(avg(amount)::numeric, 2)) as x
    from public.zad_transactions
    where user_id = p_user and txn_kind = 'expense'
      and merchant_name is not null and btrim(merchant_name) <> ''
      and (created_at at time zone v_tz)::date > v_today - 90
    group by merchant_name having count(*) >= 2
    order by count(*) desc limit 6
  ) m;

  -- النفاد المتوقّع من معدل الاستهلاك المرصود فعلاً — مش من تخمين.
  select coalesce(jsonb_agg(x order by (x->>'days_left')::numeric), '[]'::jsonb) into v_depletion
  from (
    select jsonb_build_object(
      'item', i.item_name,
      'quantity', i.quantity,
      'days_left', round((i.quantity / nullif(c.avg_daily_qty,0))::numeric, 1)) as x
    from public.zad_inventory i
    join public.zad_consumption c on c.user_id = i.user_id and c.item_name = i.item_name
    where i.user_id = p_user and i.quantity > 0 and coalesce(c.avg_daily_qty,0) > 0
    order by (i.quantity / nullif(c.avg_daily_qty,0)) limit 8
  ) d;

  return jsonb_build_object(
    'learning', false,
    'transactions_seen', v_tx_count,
    'window_days', 90,
    'by_category', coalesce(v_categories, '[]'::jsonb),
    'weekend_split', v_weekend,
    'active_hours', coalesce(v_hours, '[]'::jsonb),
    'merchants', coalesce(v_merchants, '[]'::jsonb),
    'depletion_forecast', coalesce(v_depletion, '[]'::jsonb),
    'computed_at', now());
end;
$$;

grant execute on function public.zad_behavior_patterns(uuid) to authenticated, service_role;
