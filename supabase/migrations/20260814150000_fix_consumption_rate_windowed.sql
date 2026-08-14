-- =====================================================
-- zad_recompute_consumption — الحساب كان بيرمي كل البيانات الحقيقية.
--
-- التشخيص من البيانات الحيّة (2026-08-14): `zad_inventory_observations` فيه ٢٧ ملاحظة،
-- منها ٨ انخفاضات حقيقية لصنف واحد على ٦ أيام — و`zad_consumption` **فاضي تمامًا**.
--
-- السبب: الدالة كانت بتحسب معدل لكل **زوج ملاحظات متتالي**:
--     rate = (الكمية القديمة - الجديدة) / (الفرق بين وقتي التسجيل بالأيام)
-- وبترفض أي زوج الفرق بينه أقل من ساعة. الشرط ده اتحط لسبب صح — تعديلين ورا بعض في
-- نفس الجلسة مش استهلاك — بس هو مبني على افتراض غلط: **إن وقت التسجيل هو وقت الاستهلاك**.
--
-- الناس مابتسجّلش كده. المستخدم بيفتح التطبيق ويظبّط كذا صنف في قعدة واحدة، فالفرق بين
-- التسجيلات بيبقى ثواني حتى لو الاستهلاك نفسه خد يومين. النتيجة: كل زوج إما بيدّي معدل
-- خيالي (كمية اتشالت في ٣٠ ثانية)، أو بيترفض بالشرط. عمليًا الاتنين حصلوا: ٨ انخفاضات،
-- صفر عيّنة صالحة.
--
-- الحل: بدل معدل لكل زوج، **مجموع الانخفاضات على المدى الزمني كله**:
--     avg_daily = (مجموع كل الانخفاضات) / (عدد الأيام بين أول وآخر ملاحظة)
--
-- ده بيستخدم الـ٨ انخفاضات كلهم والمدى الحقيقي (٦ أيام)، ومابيهتمّش إن التسجيل حصل على
-- دفعات — لأن الدفعات بتأثر على توزيع التسجيل، مش على إجمالي اللي اتستهلك ولا على طول
-- الفترة. الزيادات (شراء/تعبئة) بتتجاهل زي ما كانت.
-- =====================================================

CREATE OR REPLACE FUNCTION zad_recompute_consumption(p_user UUID, p_item TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
DECLARE
  v_total_drop DOUBLE PRECISION;
  v_drops INT;
  v_span_days DOUBLE PRECISION;
  v_distinct_days INT;
  v_rate DOUBLE PRECISION;
BEGIN
  IF auth.uid() IS NOT NULL AND auth.uid() <> p_user THEN
    RAISE EXCEPTION 'not_authorized';
  END IF;

  WITH ordered AS (
    SELECT qty, observed_at,
           lag(qty) OVER (ORDER BY observed_at) AS prev_qty
      FROM public.zad_inventory_observations
     WHERE user_id = p_user AND item_name = p_item
  ),
  drops AS (
    SELECT (prev_qty - qty) AS amount, observed_at
      FROM ordered
     WHERE prev_qty IS NOT NULL AND qty < prev_qty
  ),
  span AS (
    SELECT extract(epoch FROM (max(observed_at) - min(observed_at))) / 86400.0 AS days
      FROM public.zad_inventory_observations
     WHERE user_id = p_user AND item_name = p_item
  )
  SELECT coalesce(sum(d.amount), 0), count(*), count(DISTINCT d.observed_at::date),
         (SELECT days FROM span)
    INTO v_total_drop, v_drops, v_distinct_days, v_span_days
    FROM drops d;

  v_drops := coalesce(v_drops, 0);

  -- المدى لازم يكون يوم على الأقل: كل الاستهلاك في يوم واحد مايقولش معدل يومي، يقول
  -- إن حصل استهلاك مرة. ومحتاجين انخفاضين على الأقل عشان نتكلم عن "معدل" أصلاً.
  IF v_drops < 2 OR coalesce(v_span_days, 0) < 1.0 OR v_total_drop <= 0 THEN
    RETURN jsonb_build_object('samples', v_drops, 'avg_daily_qty', NULL, 'rate_known', false);
  END IF;

  v_rate := v_total_drop / v_span_days;

  INSERT INTO public.zad_consumption
    (user_id, item_name, avg_daily_qty, sample_count, rate_known, last_computed_at)
  VALUES
    -- rate_known بيفضل محتاج ٣ انخفاضات على يومين مختلفين على الأقل: معدل من انخفاضين
    -- في نفس اليوم رقم، مش نمط، والفرق ده بيوصل للموديل ويأثر على قد إيه يثق فيه.
    (p_user, p_item, v_rate, v_drops, (v_drops >= 3 AND v_distinct_days >= 2), now())
  ON CONFLICT (user_id, item_name) DO UPDATE
    SET avg_daily_qty    = excluded.avg_daily_qty,
        sample_count     = excluded.sample_count,
        rate_known       = excluded.rate_known,
        last_computed_at = excluded.last_computed_at;

  RETURN jsonb_build_object(
    'samples', v_drops,
    'avg_daily_qty', v_rate,
    'rate_known', (v_drops >= 3 AND v_distinct_days >= 2)
  );
END $function$;

-- إعادة حساب كل الأصناف اللي عندها ملاحظات — الملاحظات اتجمعت من ١١ أغسطس ومحصلش
-- ليها حساب ناجح ولا مرة، فمن غير الخطوة دي الإصلاح مايظهرش غير مع أول تعديل جديد.
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT DISTINCT user_id, item_name FROM zad_inventory_observations LOOP
    PERFORM zad_recompute_consumption(r.user_id, r.item_name);
  END LOOP;
END $$;
