-- Extends 20260808220000's realtime fix to the other two personal tables the app now
-- subscribes to live (see RealtimePersonalRepo.kt): zad_inventory and zad_pharmacy_items.
-- Same root cause — neither was ever added to supabase_realtime, so a subscription to
-- either would silently never fire.
ALTER PUBLICATION supabase_realtime ADD TABLE zad_inventory;
ALTER PUBLICATION supabase_realtime ADD TABLE zad_pharmacy_items;

-- Consolidated from the former duplicate timestamp migration
-- 20260809000000_fix_parent_alert_budget_ceiling.sql. This preserves both
-- operations for a clean bootstrap with one migration identity.
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
    IF NOT NEW.is_expense THEN RETURN NEW; END IF;
    SELECT role INTO child_role FROM family_members WHERE user_id = NEW.user_id;
    IF child_role <> 'child' THEN RETURN NEW; END IF;
    SELECT monthly_limit INTO budget_ceiling FROM zad_users WHERE id = NEW.user_id;
    IF budget_ceiling IS NULL OR budget_ceiling <= 0 THEN RETURN NEW; END IF;
    current_month := to_char(NEW.created_at, 'YYYY-MM');
    SELECT COALESCE(SUM(amount), 0) INTO month_spent FROM zad_transactions
      WHERE user_id = NEW.user_id AND is_expense = true AND to_char(created_at, 'YYYY-MM') = current_month;
    spent_pct := (month_spent::NUMERIC / budget_ceiling * 100)::INT;
    threshold_to_fire := CASE WHEN spent_pct >= 100 AND NOT EXISTS (SELECT 1 FROM sent_budget_alerts WHERE user_id = NEW.user_id AND threshold = 100 AND month = current_month) THEN 100
                              WHEN spent_pct >= 90 AND NOT EXISTS (SELECT 1 FROM sent_budget_alerts WHERE user_id = NEW.user_id AND threshold = 90 AND month = current_month) THEN 90
                              WHEN spent_pct >= 75 AND NOT EXISTS (SELECT 1 FROM sent_budget_alerts WHERE user_id = NEW.user_id AND threshold = 75 AND month = current_month) THEN 75 END;
    IF threshold_to_fire IS NOT NULL THEN
      INSERT INTO sent_budget_alerts (user_id, threshold, month) VALUES (NEW.user_id, threshold_to_fire, current_month) ON CONFLICT (user_id, threshold, month) DO NOTHING;
      FOR parent_id IN SELECT DISTINCT fm_admin.id FROM family_members fm_admin JOIN family_members fm_child ON fm_child.family_id = fm_admin.family_id WHERE fm_child.user_id = NEW.user_id AND fm_child.role = 'child' AND fm_admin.role = 'admin' LOOP
        SELECT user_id INTO parent_user_id FROM family_members WHERE id = parent_id;
        IF parent_user_id IS NOT NULL THEN
          INSERT INTO app_notifications (user_id, title, message, created_at) VALUES (parent_user_id,
            CASE threshold_to_fire WHEN 100 THEN '⛔ ميزانية ' || (SELECT alias FROM family_members WHERE user_id = NEW.user_id) || ' امتلأت' WHEN 90 THEN '⚠️ ميزانية ' || (SELECT alias FROM family_members WHERE user_id = NEW.user_id) || ' قاربت النفاد' WHEN 75 THEN '💡 ميزانية ' || (SELECT alias FROM family_members WHERE user_id = NEW.user_id) || ' استخدام 75%' END,
            'مصروف شهري: ' || month_spent::TEXT || ' من ' || budget_ceiling::TEXT || ' (' || spent_pct::TEXT || '%)', now());
        END IF;
      END LOOP;
    END IF;
    RETURN NEW;
END;
$function$;
