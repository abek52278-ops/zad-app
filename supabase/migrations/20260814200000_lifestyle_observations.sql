-- =====================================================
-- محفّزات نمط الحياة — شيف زاد، التسبيحة، والفايض.
--
-- التلاتة دول كان عندهم ذكاء بالفعل ومش موصولين بالعقل: شيف زاد بينادي
-- `meal_suggestions` في zad-core-intelligence مباشرة، والتسبيحة بتوصل في سياق الشات
-- وفي family_digest — بس **العقل مايعرفش إنه المفروض يتصرف**. الأداة مش الناقص،
-- المحفّز هو الناقص.
--
-- منفصلة عن `zad_domain_observations` عن قصد: دي **فرصة** (اقترح وجبة، اخرج) مش
-- **حالة محتاجة تصرّف** (الدوا هيخلص، الالتزام استحق). خلطهم كان هيخلّي الاتنين
-- بنفس الإلحاح في عين الموديل.
--
-- الفايض محسوب كـ (المتاح − اللي باقي الدورة محتاجاه بالمعدل)، مش المتاح نفسه.
-- الفرق ده هو اللي بيمنع "معاك ١٨ ألف اخرج" وهو محتاجهم لباقي الشهر.
-- والبرومبت بيقول للموديل صراحةً: لو فيه التزام قريب مادفعش، ماتقترحش صرف زيادة.
-- =====================================================

CREATE OR REPLACE FUNCTION zad_lifestyle_observations(p_user UUID)
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
  v_available NUMERIC;
  v_days_left INT;
  v_pace_left NUMERIC;
  v_surplus NUMERIC;
  v_items TEXT;
  v_streak INT;
  v_last DATE;
BEGIN
  IF auth.uid() IS NOT NULL AND auth.uid() <> p_user THEN
    RAISE EXCEPTION 'not_authorized';
  END IF;

  SELECT string_agg(item_name, '، ' ORDER BY item_name)
    INTO v_items
    FROM (
      SELECT DISTINCT i.item_name
        FROM zad_inventory i
        LEFT JOIN zad_consumption c ON c.user_id = i.user_id AND c.item_name = i.item_name
       WHERE i.user_id = p_user AND i.quantity > 0
         AND (
           (c.avg_daily_qty > 0 AND i.quantity / c.avg_daily_qty <= 5)
           OR (zad_try_date(i.expiry_date) IS NOT NULL
               AND zad_try_date(i.expiry_date) - v_today BETWEEN 0 AND 5)
         )
       LIMIT 6
    ) x;

  IF v_items IS NOT NULL THEN
    v_out := v_out || jsonb_build_array(jsonb_build_object(
      'domain', 'chef', 'kind', 'use_before_gone',
      'text', 'أصناف قربت تخلص أو تنتهي صلاحيتها وتنفع تتاكل قريب: ' || v_items,
      'severity', 'normal',
      'data', jsonb_build_object('items', v_items)
    ));
  END IF;

  SELECT max(streak_days), max(last_streak_date)
    INTO v_streak, v_last
    FROM family_tasbiha WHERE user_id = p_user;

  IF coalesce(v_streak, 0) >= 3 AND v_last IS NOT NULL
     AND v_today - v_last BETWEEN 1 AND 7 THEN
    v_out := v_out || jsonb_build_array(jsonb_build_object(
      'domain', 'tasbiha', 'kind', 'streak_at_risk',
      'text', 'سلسلة التسبيح كانت ' || v_streak || ' يوم وآخر مرة من ' ||
              (v_today - v_last) || ' يوم',
      'severity', CASE WHEN v_today - v_last = 1 THEN 'normal' ELSE 'low' END,
      'data', jsonb_build_object('streak_days', v_streak, 'days_since', v_today - v_last)
    ));
  END IF;

  v_bs := zad_budget_state(p_user);
  v_available := coalesce((v_bs->>'available')::numeric, 0);
  v_days_left := coalesce((v_bs->>'days_left')::int, 0);

  IF v_available > 0 AND v_days_left > 0
     AND coalesce((v_bs->>'monthly_limit')::numeric, 0) > 0 THEN
    v_pace_left := (v_bs->>'monthly_limit')::numeric
                   * v_days_left::numeric / greatest((v_bs->>'cycle_length_days')::numeric, 1);
    v_surplus := v_available - v_pace_left;
    IF v_surplus >= greatest((v_bs->>'monthly_limit')::numeric * 0.02, 50) THEN
      v_out := v_out || jsonb_build_array(jsonb_build_object(
        'domain', 'budget', 'kind', 'surplus',
        'text', 'عنده فايض حوالي ' || round(v_surplus, 0) || ' فوق اللي محتاجه لباقي الدورة ('
                || v_days_left || ' يوم)',
        'severity', 'low',
        'data', jsonb_build_object('surplus', round(v_surplus, 2),
                                   'available', v_available, 'days_left', v_days_left)
      ));
    END IF;
  END IF;

  RETURN v_out;
END $$;

REVOKE ALL ON FUNCTION zad_lifestyle_observations(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION zad_lifestyle_observations(UUID) TO service_role;
