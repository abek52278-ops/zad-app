-- =====================================================
-- ترشيح منتج مترشّح — بس لحاجة العميل محتاجها فعلاً.
--
-- الجداول (`affiliate_products`, `affiliate_clicks`) و`AffiliateHelper` موجودين من
-- زمان، والعقل مكانش يقدر يوصلهم. الفرق بين "مساعدة" و"إعلان" هو **إن الترشيح يبدأ
-- من حاجة ناقصة**: صنف في قايمة التسوق مش متشتري، أو مخزون هيخلص خلال أسبوع. مفيش
-- تصفّح كتالوج، ومفيش ترشيح من غير حاجة مقابلة.
--
-- الفلتر على `asin_verified` مش بس `is_active`: ASIN غير متحقّق ممكن يودّي لمنتج غلط
-- أو لينك ميت. تحويل عميل للينك ميت عشان عمولة أوحش من إننا ماناخدش عمولة أصلاً.
--
-- **الخمس منتجات الموجودين دلوقتي كلهم `asin_verified = false`**، فالدالة بترجّع فاضي
-- وده الصح. منطق المطابقة نفسه اتفحص لوحده على نفس البيانات: حليب ← المراعي،
-- زيت زيتون ← عافية، ارز ← أبو كاس. يعني اللي بيمنع الكسب دلوقتي هو **التحقّق من
-- الكتالوج**، مش الكود.
-- =====================================================

CREATE OR REPLACE FUNCTION zad_affiliate_matches(p_user UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE
  v_out JSONB;
BEGIN
  IF auth.uid() IS NOT NULL AND auth.uid() <> p_user THEN
    RAISE EXCEPTION 'not_authorized';
  END IF;

  WITH needs AS (
    SELECT item_name, 'shopping_list' AS why
      FROM zad_shopping_list
     WHERE user_id = p_user AND NOT is_purchased
    UNION
    SELECT i.item_name, 'running_out'
      FROM zad_inventory i
      JOIN zad_consumption c ON c.user_id = i.user_id AND c.item_name = i.item_name
     WHERE i.user_id = p_user AND c.avg_daily_qty > 0 AND i.quantity > 0
       AND i.quantity / c.avg_daily_qty <= 7
  )
  SELECT jsonb_agg(DISTINCT jsonb_build_object(
      'product_id', p.id,
      'product', p.product_name_ar,
      'category', p.category,
      'avg_price', p.average_price_sar,
      'matched_need', n.item_name,
      'why', n.why
    ))
    INTO v_out
    FROM needs n
    JOIN affiliate_products p
      ON p.is_active
     AND p.asin_verified
     AND EXISTS (
       SELECT 1 FROM unnest(p.product_name_search_keywords) k
        WHERE n.item_name ILIKE '%' || k || '%' OR k ILIKE '%' || n.item_name || '%'
     );

  RETURN coalesce(v_out, '[]'::jsonb);
END $$;

REVOKE ALL ON FUNCTION zad_affiliate_matches(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION zad_affiliate_matches(UUID) TO service_role;
