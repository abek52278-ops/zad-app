-- notify_parents_on_child_spend() (20260726100000) still read the dead, decrementing
-- `budget` column as the ceiling, same double-subtraction bug 20260726032509_monthly_limit.sql
-- already fixed everywhere else by splitting off the fixed `monthly_limit` column. A child's
-- spent_pct here was silently computed against a shrinking number instead of the real ceiling.

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
    -- Only fire on new expenses
    IF NOT NEW.is_expense THEN
        RETURN NEW;
    END IF;

    -- Check if the inserter is a child
    SELECT role INTO child_role FROM family_members WHERE user_id = NEW.user_id;
    IF child_role <> 'child' THEN
        RETURN NEW;
    END IF;

    -- Fetch child's budget ceiling
    SELECT monthly_limit INTO budget_ceiling FROM zad_users WHERE id = NEW.user_id;
    IF budget_ceiling IS NULL OR budget_ceiling <= 0 THEN
        RETURN NEW;
    END IF;

    -- This month (YYYY-MM)
    current_month := to_char(NEW.created_at, 'YYYY-MM');

    -- Sum all expenses for this child this month
    SELECT COALESCE(SUM(amount), 0) INTO month_spent
    FROM zad_transactions
    WHERE user_id = NEW.user_id
      AND is_expense = true
      AND to_char(created_at, 'YYYY-MM') = current_month;

    -- Compute percentage
    spent_pct := CASE WHEN budget_ceiling > 0 THEN (month_spent::NUMERIC / budget_ceiling * 100)::INT ELSE 0 END;

    -- Determine which threshold to fire (if any new one crossed)
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

    -- If a threshold fired, log it and notify all admins in the family
    IF threshold_to_fire IS NOT NULL THEN
        INSERT INTO sent_budget_alerts (user_id, threshold, month) VALUES (NEW.user_id, threshold_to_fire, current_month)
            ON CONFLICT (user_id, threshold, month) DO NOTHING;

        -- Notify all family admins
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
