-- =====================================================
-- zad_family_digest — "عقول العائلة": الأب يعرف أولاده عاملين إيه، من غير ما يشوف
-- كل معاملة ليهم.
--
-- الطلب كان "يعرف الأب إنجازاتهم ومصروفهم". الفرق بين ده وبين أداة مراقبة هو **مستوى
-- التجميع**: الدالة بترجّع "أحمد صرف ٨٠٪ من سقفه هو" — مش قايمة مشترياته. البيانات
-- الخام مابتخرجش من حدود صاحبها أبداً.
--
-- ليه مش `get_my_family_ids()`: الدالة دي مبنية على `auth.uid()`، والعقل بينادي من
-- إيدج فانكشن بـservice_role حيث `auth.uid()` بيبقى NULL — يعني كانت هترجّع فاضي دايماً.
-- العضوية والدور بيتقرأوا من `family_members` بـ`p_user` مباشرة.
--
-- بوابة الدور موجودة من دلوقتي رغم إنها **مالهاش أثر النهارده**: كل الـ١٢ عضو الموجودين
-- دورهم 'admin'، فالكل بيشوف الكل. لما تتضاف أدوار أولاد، الحد الأمني يبقى مكتوب أصلاً
-- بدل ما يتفتكر وقتها — غير الأدوار دي بيشوف نفسه بس.
-- =====================================================

CREATE OR REPLACE FUNCTION zad_family_digest(p_user UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE
  v_family UUID;
  v_role TEXT;
  v_can_see_all BOOLEAN;
  v_members JSONB;
  v_goal JSONB;
BEGIN
  IF auth.uid() IS NOT NULL AND auth.uid() <> p_user THEN
    RAISE EXCEPTION 'not_authorized';
  END IF;

  SELECT family_id, role INTO v_family, v_role
    FROM family_members WHERE user_id = p_user LIMIT 1;

  IF v_family IS NULL THEN
    RETURN jsonb_build_object('in_family', false);
  END IF;

  v_can_see_all := coalesce(v_role, '') IN ('admin', 'parent', 'owner');

  SELECT jsonb_agg(jsonb_build_object(
      'alias', m.alias,
      'role', m.role,
      'is_self', m.user_id = p_user,
      'spent_30d', round(coalesce(t.spent, 0)::numeric, 2),
      'monthly_limit', u.monthly_limit,
      'limit_used_pct', CASE WHEN coalesce(u.monthly_limit, 0) > 0
                             THEN round((coalesce(t.spent, 0) / u.monthly_limit * 100)::numeric, 0)
                             ELSE NULL END,
      'chores_done_30d', coalesce(ch.done, 0),
      'tasbiha_streak', coalesce(tb.streak_days, 0)
    ) ORDER BY m.user_id = p_user DESC, m.alias)
    INTO v_members
    FROM family_members m
    JOIN zad_users u ON u.id = m.user_id
    LEFT JOIN LATERAL (
      SELECT sum(amount) AS spent FROM zad_transactions
       WHERE user_id = m.user_id AND txn_kind = 'expense'
         AND created_at >= now() - interval '30 days'
    ) t ON true
    LEFT JOIN LATERAL (
      SELECT count(*) AS done FROM family_chores
       WHERE assigned_to = m.user_id AND is_completed
         AND created_at >= now() - interval '30 days'
    ) ch ON true
    LEFT JOIN LATERAL (
      SELECT max(streak_days) AS streak_days FROM family_tasbiha
       WHERE user_id = m.user_id AND family_id = v_family
    ) tb ON true
   WHERE m.family_id = v_family
     AND (v_can_see_all OR m.user_id = p_user);

  SELECT jsonb_build_object('target', target_amount, 'current', current_amount, 'month', month_year)
    INTO v_goal
    FROM family_goals WHERE family_id = v_family
    ORDER BY created_at DESC LIMIT 1;

  RETURN jsonb_build_object(
    'in_family', true,
    'can_see_all_members', v_can_see_all,
    'members', coalesce(v_members, '[]'::jsonb),
    'goal', v_goal
  );
END $$;

REVOKE ALL ON FUNCTION zad_family_digest(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION zad_family_digest(UUID) TO service_role;

COMMENT ON FUNCTION zad_family_digest(UUID) IS
  'Aggregated family view for the brain: per-member 30-day spend, share of their own limit, chores completed and tasbiha streak — never raw transactions. Membership and role are resolved from family_members rather than auth.uid(), because the brain calls this as service_role where auth.uid() is null.';
