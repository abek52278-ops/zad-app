-- =====================================================
-- إقفال آخر فجوتين في طبقة الملاحظات: الميزانية، وتتبّع سداد الالتزامات.
--
-- ١) الميزانية كانت المجال الوحيد اللي بيكتب **تنبيه للعميل** (عبر
--    `_agent_spending_ahead_for_user`) من غير ما يرجّع **ملاحظة للعقل**. فالعقل كان
--    بيشوف الأرقام النهائية ومايعرفش "انت قدام الجدول". دلوقتي بيتقارن المصروف الفعلي
--    بالمتوقّع خطّيًا من `zad_budget_state` (نفس مصدر الحقيقة الوحيد للفلوس).
--    العتبة ١٥٪ عشان ماتطلعش ملاحظة من تذبذب طبيعي؛ فوق ٤٠٪ بتبقى high.
--    وكمان ملاحظة "سقف متسجّل ومش مأكّد" — دي حالة حقيقية قايمة في البيانات دلوقتي
--    (`limit_confirmed: false` مع `monthly_limit: 20000`) ومكانش ليها أي صوت.
--
-- ٢) تتبّع السداد ("هل الإيجار اتدفع الشهر ده؟") كان مكتوب في
--    SESSION_2026_08_13_unified_brain.md كـ"مشروع منفصل". طلع مش محتاج جدول ولا عمود
--    جديد — هو **ملاحظة محسوبة**: استحقاق عدّى، ومفيش معاملة مصروف بمبلغ قريب منه في
--    نافذة حواليه.
--
--    التسامح ١٠٪ (بحد أدنى ٥) عشان الفواتير بتختلف شوية من شهر لشهر، والنافذة بتبدأ
--    قبل الاستحقاق بـ٣ أيام عشان الناس بتدفع بدري. النتيجة **احتمال** مش حكم، والنص
--    بيقول كده صراحةً: "يا إما مادفعش يا إما مااتسجّلش" — لأن غياب المعاملة ممكن يكون
--    معناه إن الدفع اتعمل كاش ومااتسجّلش، والعقل لازم يسأل مش يتهم.
--
--    مقصور على `recurrence = 'monthly'` عن قصد: `zad_obligations` فاضي دلوقتي فمفيش
--    بيانات حقيقية تأكّد سلوك الدوريات التانية، وتخمينها كان هيطلّع ملاحظات غلط.
--
-- اتفحص بثلاث حالات على البيانات الحقيقية من غير ما تتكتب أي صفوف: التزام ١٥٠٠ يوم ٩
-- (فيه معاملة ١٥٠٠ يوم ٢٠٢٦-٠٨-٠٩ → اتحسب مدفوع)، التزام ٣٠٠٠ يوم ٥ (مفيش → طلعت
-- ملاحظة)، والتزام استحقاقه لسه ماجاش (اتفلتر).
-- =====================================================

-- الدالة كاملة أدناه (نفس محتوى 20260814160000 + المجالين الجداد).

CREATE OR REPLACE FUNCTION zad_domain_observations(p_user UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE
  v_out JSONB := '[]'::jsonb;
  v_today DATE := current_date;
  v_bs JSONB;
  v_expected NUMERIC;
  v_spent NUMERIC;
  v_ahead_pct NUMERIC;
BEGIN
  IF auth.uid() IS NOT NULL AND auth.uid() <> p_user THEN
    RAISE EXCEPTION 'not_authorized';
  END IF;

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'inventory', 'kind', 'depletion',
      'text', i.item_name || ' متوقّع يخلص خلال ' || ceil(i.quantity / c.avg_daily_qty)::int || ' يوم',
      'severity', CASE WHEN i.quantity / c.avg_daily_qty <= 3 THEN 'high' ELSE 'normal' END,
      'data', jsonb_build_object('item', i.item_name, 'quantity', i.quantity,
                                 'avg_daily_qty', round(c.avg_daily_qty::numeric, 3),
                                 'rate_known', c.rate_known)
    )), '[]'::jsonb)
    INTO v_out
    FROM zad_inventory i
    JOIN zad_consumption c ON c.user_id = i.user_id AND c.item_name = i.item_name
   WHERE i.user_id = p_user AND c.avg_daily_qty > 0 AND i.quantity > 0
     AND i.quantity / c.avg_daily_qty <= 14;

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'inventory', 'kind', 'expiry',
      'text', item_name || ' صلاحيته بتنتهي خلال ' || (ed - v_today) || ' يوم',
      'severity', CASE WHEN ed - v_today <= 3 THEN 'high' ELSE 'normal' END,
      'data', jsonb_build_object('item', item_name, 'expiry_date', ed)
    )), '[]'::jsonb)
    INTO v_out
    FROM (SELECT item_name, zad_try_date(expiry_date) AS ed
            FROM zad_inventory WHERE user_id = p_user AND expiry_date IS NOT NULL) x
   WHERE ed IS NOT NULL AND ed >= v_today AND ed - v_today <= 10;

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'pharmacy', 'kind', 'low_stock',
      'text', name || ' فاضل منه ' || remaining_quantity || ' ' || coalesce(unit, 'وحدة') ||
              ' — يكفي حوالي ' || floor(remaining_quantity / greatest(daily_dose_count, 1))::int || ' يوم',
      'severity', CASE WHEN remaining_quantity / greatest(daily_dose_count, 1) <= 3 THEN 'high' ELSE 'normal' END,
      'data', jsonb_build_object('name', name, 'remaining', remaining_quantity,
                                 'daily_dose_count', daily_dose_count)
    )), '[]'::jsonb)
    INTO v_out
    FROM zad_pharmacy_items
   WHERE user_id = p_user AND remaining_quantity > 0 AND coalesce(daily_dose_count, 0) > 0
     AND remaining_quantity / greatest(daily_dose_count, 1) <= 7;

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'subscriptions', 'kind', 'renewal',
      'text', title || ' هيتجدد خلال ' || (rd - v_today) || ' يوم بمبلغ ' || amount,
      'severity', 'normal',
      'data', jsonb_build_object('title', title, 'amount', amount, 'renewal_date', rd)
    )), '[]'::jsonb)
    INTO v_out
    FROM (SELECT title, amount, zad_try_date(renewal_date) AS rd
            FROM zad_subscriptions
           WHERE user_id = p_user AND is_active AND renewal_date IS NOT NULL) x
   WHERE rd IS NOT NULL AND rd >= v_today AND rd - v_today <= 7;

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'obligations', 'kind', 'due_soon',
      'text', title || ' استحقاقه خلال ' || (nd - v_today) || ' يوم بمبلغ ' || amount,
      'severity', CASE WHEN nd - v_today <= 3 THEN 'high' ELSE 'normal' END,
      'data', jsonb_build_object('title', title, 'amount', amount, 'due', nd)
    )), '[]'::jsonb)
    INTO v_out
    FROM (
      SELECT title, amount,
             zad_obligation_next_due(recurrence, due_day, due_date, v_today) AS nd
        FROM zad_obligations
       WHERE user_id = p_user AND active AND confirmed
    ) o
   WHERE nd IS NOT NULL AND nd >= v_today AND nd - v_today <= 7;

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'obligations', 'kind', 'possibly_unpaid',
      'text', title || ' كان استحقاقه ' || prev_due || ' (من ' || (v_today - prev_due) ||
              ' يوم) ومفيش معاملة بمبلغ قريب منه — يا إما مادفعش يا إما مااتسجّلش',
      'severity', CASE WHEN v_today - prev_due >= 5 THEN 'high' ELSE 'normal' END,
      'data', jsonb_build_object('title', title, 'amount', amount, 'due_was', prev_due)
    )), '[]'::jsonb)
    INTO v_out
    FROM (
      SELECT o.title, o.amount,
             (make_date(extract(year FROM v_today)::int, extract(month FROM v_today)::int,
                        least(o.due_day, extract(day FROM (date_trunc('month', v_today)
                             + interval '1 month - 1 day'))::int))) AS prev_due
        FROM zad_obligations o
       WHERE o.user_id = p_user AND o.active AND o.confirmed
         AND o.recurrence = 'monthly' AND o.due_day IS NOT NULL
    ) d
   WHERE prev_due <= v_today
     AND v_today - prev_due BETWEEN 2 AND 20
     AND NOT EXISTS (
       SELECT 1 FROM zad_transactions t
        WHERE t.user_id = p_user AND t.txn_kind = 'expense'
          AND abs(t.amount - d.amount) <= greatest(d.amount * 0.10, 5)
          AND t.created_at::date BETWEEN d.prev_due - 3 AND v_today
     );

  SELECT v_out || coalesce(jsonb_agg(jsonb_build_object(
      'domain', 'maintenance', 'kind', 'service_overdue',
      'text', name || ' فات على آخر صيانة ليه ' ||
              (v_today - lsd) || ' يوم (كل ' || service_interval_days || ' يوم)',
      'severity', 'normal',
      'data', jsonb_build_object('name', name, 'interval_days', service_interval_days)
    )), '[]'::jsonb)
    INTO v_out
    FROM (SELECT name, service_interval_days, zad_try_date(last_service_date) AS lsd
            FROM zad_maintenance_items
           WHERE user_id = p_user AND last_service_date IS NOT NULL
             AND coalesce(service_interval_days, 0) > 0) x
   WHERE lsd IS NOT NULL AND v_today - lsd > service_interval_days;

  SELECT v_out || CASE WHEN count(*) > 0 THEN jsonb_build_array(jsonb_build_object(
      'domain', 'shopping', 'kind', 'pending',
      'text', 'قايمة التسوق فيها ' || count(*) || ' صنف مستني' ||
              CASE WHEN sum(coalesce(estimated_price, 0)) > 0
                   THEN ' بتقدير ' || round(sum(coalesce(estimated_price, 0))::numeric, 2) ELSE '' END,
      'severity', 'low',
      'data', jsonb_build_object('count', count(*),
                                 'estimated_total', round(sum(coalesce(estimated_price, 0))::numeric, 2))
    )) ELSE '[]'::jsonb END
    INTO v_out
    FROM zad_shopping_list
   WHERE user_id = p_user AND NOT is_purchased;

  v_bs := zad_budget_state(p_user);

  IF coalesce((v_bs->>'monthly_limit')::numeric, 0) > 0
     AND coalesce((v_bs->>'cycle_length_days')::int, 0) > 0
     AND coalesce((v_bs->>'days_elapsed')::int, 0) >= 3 THEN
    v_spent := coalesce((v_bs->>'spent')::numeric, 0);
    v_expected := (v_bs->>'monthly_limit')::numeric
                  * (v_bs->>'days_elapsed')::numeric / (v_bs->>'cycle_length_days')::numeric;
    IF v_expected > 0 AND v_spent > v_expected THEN
      v_ahead_pct := round((v_spent - v_expected) / v_expected * 100, 0);
      IF v_ahead_pct >= 15 THEN
        v_out := v_out || jsonb_build_array(jsonb_build_object(
          'domain', 'budget', 'kind', 'ahead_of_pace',
          'text', 'صرفه أسرع من الجدول بـ' || v_ahead_pct || '٪ — صرف ' || round(v_spent, 0) ||
                  ' والمتوقّع لحد النهارده ' || round(v_expected, 0),
          'severity', CASE WHEN v_ahead_pct >= 40 THEN 'high' ELSE 'normal' END,
          'data', jsonb_build_object('spent', v_spent, 'expected', round(v_expected, 2),
                                     'ahead_pct', v_ahead_pct,
                                     'days_left', (v_bs->>'days_left')::int)
        ));
      END IF;
    END IF;
  END IF;

  IF coalesce((v_bs->>'monthly_limit')::numeric, 0) > 0
     AND coalesce((v_bs->>'limit_confirmed')::boolean, true) IS FALSE THEN
    v_out := v_out || jsonb_build_array(jsonb_build_object(
      'domain', 'budget', 'kind', 'limit_unconfirmed',
      'text', 'السقف الشهري (' || (v_bs->>'monthly_limit') || ') متسجّل بس العميل ماأكدهوش',
      'severity', 'low',
      'data', jsonb_build_object('monthly_limit', (v_bs->>'monthly_limit')::numeric)
    ));
  END IF;

  RETURN v_out;
END $$;

REVOKE ALL ON FUNCTION zad_domain_observations(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION zad_domain_observations(UUID) TO service_role;
