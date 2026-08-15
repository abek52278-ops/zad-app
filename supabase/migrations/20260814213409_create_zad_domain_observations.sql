-- =====================================================
-- zad_domain_observations — طبقة "الموظفين": كل مجال بيطلّع **ملاحظات**، مش صفوف.
--
-- العقل دلوقتي بيبني `buildSnapshot` بيجيب صفوف خام من ~٢٠ جدول ويسيب الموديل يستنتج
-- منها. ده غالي في التوكن (والكوتة عندنا ٢٠ طلب/يوم للمشروع، فالتوكن مش رفاهية) وبيضيّع
-- التفاصيل الصغيرة: الموديل بيشوف "حليب: ٢" مش "الحليب هيخلص بعد يومين".
--
-- الدالة دي قراءة بس ومالهاش أي أثر جانبي — مقصود إنها تختلف عن
-- `_agent_spending_ahead_for_user` و`_agent_med_followup_for_user` اللي بيرجعوا void
-- وبيكتبوا رؤى. الملاحظة مش رؤية: الرؤية بتتعرض للعميل، الملاحظة بتتقال للعقل ويقرر
-- هو يعمل بيها إيه.
--
-- كل ملاحظة: { domain, kind, text, severity, data }
--   severity: high = محتاج تصرّف دلوقتي، normal = يستاهل ذكر، low = خلفية.
-- =====================================================

-- تلات أعمدة تواريخ في السكيما دي متخزّنة **نص** مش date: `zad_inventory.expiry_date`،
-- `zad_maintenance_items.last_service_date`، `zad_subscriptions.renewal_date`. تحويل
-- مباشر بـ::date بيرمي استثناء على أول قيمة بايظة أو فاضية ويوقّع الدالة كلها — يعني
-- صنف واحد بتاريخ غلط كان هيمنع كل الملاحظات في كل المجالات. التحويل الآمن بيرجّع NULL
-- والصف بيتفلتر بدل ما ياخد الدالة معاه.
CREATE OR REPLACE FUNCTION zad_try_date(p_text TEXT)
RETURNS DATE
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
  RETURN left(btrim(p_text), 10)::date;
EXCEPTION WHEN others THEN
  RETURN NULL;
END $$;

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
BEGIN
  IF auth.uid() IS NOT NULL AND auth.uid() <> p_user THEN
    RAISE EXCEPTION 'not_authorized';
  END IF;

  -- ── المخزون: نفاد متوقّع ────────────────────────────────────────────────────
  -- ده اللي كان مستحيل قبل إصلاح zad_recompute_consumption: المعدل كان دايماً فاضي،
  -- فالعقل كان بيشوف الكمية بس. `rate_known` بيوصل زي ما هو عشان الموديل يفرّق بين
  -- تقدير مؤكد وتقدير أولي بدل ما يتعامل مع الاتنين بنفس الثقة.
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

  -- ── المخزون: صلاحية قربت ───────────────────────────────────────────────────
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

  -- ── الصيدلية: دوا قارب يخلص ────────────────────────────────────────────────
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

  -- ── الاشتراكات: تجديد قريب ─────────────────────────────────────────────────
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

  -- ── الالتزامات: استحقاق قريب ───────────────────────────────────────────────
  -- زاد_obligation_next_due هي نفس الدالة اللي التذكيرات بتستخدمها، فالرقم اللي العقل
  -- بيشوفه هنا هو نفس الرقم اللي العميل بيتنبّه بيه — مش حساب تاني ممكن يختلف.
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

  -- ── الصيانة: خدمة فاتت ─────────────────────────────────────────────────────
  -- المجال ده مكانش ليه أي مصدر ملاحظات خالص قبل كده.
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

  -- ── التسوق: قايمة مستنية ───────────────────────────────────────────────────
  -- ملاحظة واحدة مجمّعة مش صف لكل صنف: قايمة فيها ١٢ حاجة مش ١٢ ملاحظة، هي حقيقة واحدة.
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

  RETURN v_out;
END $$;

REVOKE ALL ON FUNCTION zad_domain_observations(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION zad_domain_observations(UUID) TO service_role;

COMMENT ON FUNCTION zad_domain_observations(UUID) IS
  'Per-domain observations for the brain: short statements rather than raw rows. Read-only and side-effect free, unlike _agent_*_for_user which write insights. Cheaper in tokens than a full snapshot and preserves detail the model cannot infer from quantities alone.';
