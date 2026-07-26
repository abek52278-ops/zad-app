-- Task 19.0 خطوة ٦ — تصحيح آخر قارئين لعمود zad_users.budget الميت على السيرفر.
-- كلاهما دالة/trigger موجودين بالفعل (0001_zad_brain.sql، 20260726100000_parent_alerts_
-- child_spending.sql) — بما إننا مش بنعدّل ملفات migration قديمة، هنا CREATE OR REPLACE
-- بنفس التعريف الأصلي، الفرق الوحيد هو budget → monthly_limit.

-- ── zad_brain_self_review: كان بيقارن مصروف الشهر بعمود budget الميت لتقييم دقة
-- تحذيرات "سرعة الصرف" القديمة. لو الميزان غلط، دقة التقييم نفسها غلط.
create or replace function public.zad_brain_self_review(p_user uuid)
returns jsonb language sql stable as $$
  with velocity_checks as (
    select i.id, extract(year from i.created_at)::int as yr, extract(month from i.created_at)::int as mo
    from public.zad_insights i
    where i.user_id = p_user and i.dedupe_key like 'velocity_%'
      and extract(day from i.created_at) <= 10
      and i.created_at < date_trunc('month', now())
      and i.created_at >= now() - interval '75 days'
  ),
  velocity_outcomes as (
    select v.id,
      case when coalesce((
        select sum(t.amount) from public.zad_transactions t
        where t.user_id = p_user and t.is_expense
          and date_trunc('month', t.created_at) = make_date(v.yr, v.mo, 1)
      ), 0) <= coalesce((select monthly_limit from public.zad_users where id = p_user), 0)
      then 'incorrect' else 'correct' end as verdict
    from velocity_checks v
  ),
  low_stock_checks as (
    select i.id, i.about_item, i.created_at
    from public.zad_insights i
    where i.user_id = p_user and i.dedupe_key like 'low_stock_%'
      and i.created_at >= now() - interval '30 days'
  ),
  low_stock_outcomes as (
    select l.id,
      case when exists (
        select 1 from public.zad_transactions t
        where t.user_id = p_user and t.is_expense
          and t.created_at > l.created_at
          and t.title ilike '%' || l.about_item || '%'
      ) then 'correct' else 'incorrect' end as verdict
    from low_stock_checks l
  )
  select jsonb_build_object(
    'velocity_checked', (select count(*) from velocity_outcomes),
    'velocity_incorrect', (select count(*) from velocity_outcomes where verdict = 'incorrect'),
    'low_stock_checked', (select count(*) from low_stock_outcomes),
    'low_stock_incorrect', (select count(*) from low_stock_outcomes where verdict = 'incorrect')
  );
$$;

-- ── notify_parents_on_child_spend: عتبة ميزانية الطفل كانت بتتقرا من budget الميت.
-- ملاحظة: التنبيه لسه ممكن يتفعّل غلط لما سحب ATM يتصنف is_expense=true — دي مشكلة
-- تصنيف المعاملة (EPIC_1_4 Task 19.2/19.3، wallet/txn_kind)، مش عمود الميزانية —
-- مش بتتصلح هنا، متعمد.
CREATE OR REPLACE FUNCTION public.notify_parents_on_child_spend()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
DECLARE
    child_role TEXT;
    budget_ceiling DOUBLE PRECISION;
    current_month TEXT;
    month_spent DOUBLE PRECISION;
    spent_pct INT;
    parent_id UUID;
    parent_user_id UUID;
    threshold_to_fire INT;
BEGIN
    IF NOT NEW.is_expense THEN
        RETURN NEW;
    END IF;

    SELECT role INTO child_role FROM family_members WHERE user_id = NEW.user_id;
    IF child_role <> 'child' THEN
        RETURN NEW;
    END IF;

    -- Task 19.0: budget → monthly_limit (السقف الثابت اللي المستخدم بيأكده، مش الرصيد
    -- المتراكم اللي كان بيتنقّص بمعاملة معاملة والـ trigger بتاعه اتشال).
    SELECT monthly_limit INTO budget_ceiling FROM zad_users WHERE id = NEW.user_id;
    IF budget_ceiling IS NULL OR budget_ceiling <= 0 THEN
        RETURN NEW;
    END IF;

    current_month := to_char(NEW.created_at, 'YYYY-MM');

    SELECT COALESCE(SUM(amount), 0) INTO month_spent
    FROM zad_transactions
    WHERE user_id = NEW.user_id
      AND is_expense = true
      AND to_char(created_at, 'YYYY-MM') = current_month;

    spent_pct := CASE WHEN budget_ceiling > 0 THEN (month_spent::NUMERIC / budget_ceiling * 100)::INT ELSE 0 END;

    threshold_to_fire := NULL;
    IF spent_pct >= 100 AND NOT EXISTS (
        SELECT 1 FROM sent_budget_alerts WHERE user_id = NEW.user_id AND threshold = 100 AND month = current_month
    ) THEN
        threshold_to_fire := 100;
    ELSIF spent_pct >= 90 AND NOT EXISTS (
        SELECT 1 FROM sent_budget_alerts WHERE user_id = NEW.user_id AND threshold = 90 AND month = current_month
    ) THEN
        threshold_to_fire := 90;
    ELSIF spent_pct >= 75 AND NOT EXISTS (
        SELECT 1 FROM sent_budget_alerts WHERE user_id = NEW.user_id AND threshold = 75 AND month = current_month
    ) THEN
        threshold_to_fire := 75;
    END IF;

    IF threshold_to_fire IS NOT NULL THEN
        INSERT INTO sent_budget_alerts (user_id, threshold, month) VALUES (NEW.user_id, threshold_to_fire, current_month)
            ON CONFLICT (user_id, threshold, month) DO NOTHING;

        FOR parent_id IN
            SELECT DISTINCT fm_admin.id FROM family_members fm_admin
            JOIN family_members fm_child ON fm_child.family_id = fm_admin.family_id
            WHERE fm_child.user_id = NEW.user_id
              AND fm_child.role = 'child'
              AND fm_admin.role = 'admin'
        LOOP
            SELECT user_id INTO parent_user_id FROM family_members WHERE id = parent_id;
            IF parent_user_id IS NOT NULL THEN
                INSERT INTO app_notifications (user_id, title, message, created_at)
                VALUES (
                    parent_user_id,
                    CASE threshold_to_fire
                        WHEN 100 THEN '⛔ ميزانية ' || (SELECT alias FROM family_members WHERE user_id = NEW.user_id) || ' امتلأت'
                        WHEN 90 THEN '⚠️ ميزانية ' || (SELECT alias FROM family_members WHERE user_id = NEW.user_id) || ' قاربت النفاد'
                        WHEN 75 THEN '💡 ميزانية ' || (SELECT alias FROM family_members WHERE user_id = NEW.user_id) || ' استخدام 75%'
                    END,
                    'مصروف شهري: ' || month_spent::TEXT || ' من ' || budget_ceiling::TEXT || ' (' || spent_pct::TEXT || '%)',
                    now()
                );
            END IF;
        END LOOP;
    END IF;

    RETURN NEW;
END;
$function$;
