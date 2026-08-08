-- Fix false Telegram budget alerts (e.g. "700 من 500 (140%)" while the app showed a
-- much larger available figure). Root cause, found live in this project's own data —
-- not a hardcoded fallback, the two real bugs are:
--
-- 1. notify_telegram_on_transaction() (20260808150000) gated on NEW.is_expense and
--    summed WHERE is_expense = true. That's the exact bug 20260726070000 already fixed
--    for notify_parents_on_child_spend(): is_expense still marks an ATM
--    withdrawal (txn_kind='transfer') as an expense, so month_spent double-counts
--    transfers as spend. 20260808150000 was written after that fix but didn't reuse it
--    — this migration brings it in line with the txn_kind='expense' convention
--    BudgetMath.kt/zad-brain/notify_parents_on_child_spend all already use.
--
-- 2. It read zad_users.monthly_limit without checking limit_confirmed_at. Per
--    SupabaseRepo.getMonthlyLimit()'s own contract ("null معناها لسه متسجلش. مفيش حاجة
--    تعرض أو تحسب على ده قبل التأكيد"), a captured-but-unconfirmed monthly_limit must
--    never be displayed or reasoned on — every Kotlin consumer already honors this, the
--    trigger didn't. Verified live: the bound test user's monthly_limit=500 has
--    limit_confirmed_at=NULL (auto-captured, never confirmed via BudgetEditDialog's
--    setMonthlyLimit), which is exactly why the push used a number the app itself
--    would never show.
--
-- Monthly reset is already correct and needs no change: current_month is recomputed
-- per-transaction from NEW.created_at (not a stored counter), and
-- sent_telegram_budget_alerts is deduped on (user_id, threshold, month) — a new
-- calendar month naturally clears every threshold.

CREATE OR REPLACE FUNCTION public.notify_telegram_on_transaction()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
DECLARE
    is_linked BOOLEAN;
    v_limit DOUBLE PRECISION;
    current_month TEXT;
    month_spent DOUBLE PRECISION;
    spent_pct INT;
    threshold_to_fire INT;
    push_title TEXT;
    push_body TEXT;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM telegram_bindings WHERE user_id = NEW.user_id AND bound_at IS NOT NULL
    ) INTO is_linked;
    IF NOT is_linked THEN
        RETURN NEW;
    END IF;

    IF NEW.amount != 0 THEN
        push_title := 'زاد | معاملة جديدة';
        push_body := (CASE WHEN NEW.is_expense THEN 'مصروف: ' ELSE 'دخل: ' END)
            || NEW.title || ' — ' || NEW.amount::TEXT;
        PERFORM net.http_post(
            url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
            headers:='{"Content-Type":"application/json","X-Realtime-Push-Secret":"7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094"}'::jsonb,
            body:=jsonb_build_object('user_id', NEW.user_id, 'title', push_title, 'body', push_body),
            timeout_milliseconds:=15000
        );
    END IF;

    -- Fix 1: txn_kind='expense', not is_expense — a transfer/ATM withdrawal is not spend.
    IF NEW.txn_kind <> 'expense' THEN
        RETURN NEW;
    END IF;

    -- Fix 2: only reason on a confirmed limit — same invariant as every Kotlin reader.
    SELECT monthly_limit INTO v_limit
    FROM zad_users
    WHERE id = NEW.user_id AND limit_confirmed_at IS NOT NULL;
    IF v_limit IS NULL OR v_limit <= 0 THEN
        RETURN NEW;
    END IF;

    current_month := to_char(NEW.created_at, 'YYYY-MM');

    SELECT COALESCE(SUM(amount), 0) INTO month_spent
    FROM zad_transactions
    WHERE user_id = NEW.user_id
      AND txn_kind = 'expense'
      AND to_char(created_at, 'YYYY-MM') = current_month;

    spent_pct := (month_spent::NUMERIC / v_limit * 100)::INT;

    threshold_to_fire := NULL;
    IF spent_pct >= 100 AND NOT EXISTS (
        SELECT 1 FROM sent_telegram_budget_alerts WHERE user_id = NEW.user_id AND threshold = 100 AND month = current_month
    ) THEN
        threshold_to_fire := 100;
    ELSIF spent_pct >= 90 AND NOT EXISTS (
        SELECT 1 FROM sent_telegram_budget_alerts WHERE user_id = NEW.user_id AND threshold = 90 AND month = current_month
    ) THEN
        threshold_to_fire := 90;
    ELSIF spent_pct >= 75 AND NOT EXISTS (
        SELECT 1 FROM sent_telegram_budget_alerts WHERE user_id = NEW.user_id AND threshold = 75 AND month = current_month
    ) THEN
        threshold_to_fire := 75;
    END IF;

    IF threshold_to_fire IS NOT NULL THEN
        INSERT INTO sent_telegram_budget_alerts (user_id, threshold, month)
            VALUES (NEW.user_id, threshold_to_fire, current_month)
            ON CONFLICT (user_id, threshold, month) DO NOTHING;

        push_title := CASE threshold_to_fire
            WHEN 100 THEN '⛔ وصلت لحد ميزانيتك'
            WHEN 90 THEN '⚠️ ميزانيتك قاربت النفاد'
            WHEN 75 THEN '💡 استخدمت 75% من ميزانيتك'
        END;
        push_body := 'مصروف الشهر: ' || month_spent::TEXT || ' من ' || v_limit::TEXT || ' (' || spent_pct::TEXT || '%)';

        PERFORM net.http_post(
            url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
            headers:='{"Content-Type":"application/json","X-Realtime-Push-Secret":"7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094"}'::jsonb,
            body:=jsonb_build_object('user_id', NEW.user_id, 'title', push_title, 'body', push_body),
            timeout_milliseconds:=15000
        );
    END IF;

    RETURN NEW;
END;
$function$;
