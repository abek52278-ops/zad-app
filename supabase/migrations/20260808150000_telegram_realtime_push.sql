-- Real-time Telegram push for the account owner's own transactions/budget warnings —
-- extends the existing notify_parents_on_child_spend() pattern (same threshold logic,
-- same zad_users.monthly_limit source of truth) instead of duplicating BudgetMath.kt's
-- fuller "available" calculation (committed obligations, cycle math) in SQL. This is a
-- deliberately simpler headline check: percentage of monthly_limit spent this calendar
-- month, same as the child-alert trigger already computes server-side.
--
-- Delivery goes through zad-telegram-bot's new ?job=realtime_push endpoint (net.http_post,
-- same fire-and-forget pattern as telegram_checkin_pipeline/telegram_subscription_alerts),
-- which only resolves chat_id and sends — no calculation happens in the edge function.
--
-- Scope note: pharmacy-alarm real-time push is NOT included here. Pharmacy low-stock
-- detection already runs server-side (findCheckInCandidates in zad-telegram-bot) but only
-- on the once-daily check-in cron, not per-event — there is no natural row-insert trigger
-- for "days of supply crossed a threshold" the way there is for a new transaction, and
-- pharmacy dosage/consumption-rate logic already lives in one place (zad_consumption +
-- ZadCentralBrain) that a new trigger would risk drifting from. Left for a follow-up that
-- can reuse this same realtime_push endpoint once a clear trigger event is chosen.

CREATE TABLE IF NOT EXISTS sent_telegram_budget_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    threshold INT NOT NULL,
    month TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sent_telegram_alerts_idempotency
    ON sent_telegram_budget_alerts(user_id, threshold, month);

-- Separate table from sent_budget_alerts on purpose: that one dedupes the child->parent
-- in-app notification channel. Reusing it here would silently suppress this channel's
-- alert whenever the other channel already fired the same threshold this month.
ALTER TABLE sent_telegram_budget_alerts ENABLE ROW LEVEL SECURITY;
CREATE POLICY "user_own_sent_telegram_budget_alerts" ON sent_telegram_budget_alerts
    FOR ALL USING (auth.uid() = user_id);

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
    -- Cheap early exit: most rows belong to a user who never linked Telegram at all —
    -- skip both checks below rather than firing an HTTP call that will just report
    -- "not linked" every time.
    SELECT EXISTS (
        SELECT 1 FROM telegram_bindings WHERE user_id = NEW.user_id AND bound_at IS NOT NULL
    ) INTO is_linked;
    IF NOT is_linked THEN
        RETURN NEW;
    END IF;

    -- ── New transaction push ── same "$0 EGP system transaction" rule as the Android
    -- UI-side filter (HomeScreen.kt visibleTransactions): a zero-amount row is noise,
    -- not a real transaction, so it doesn't get a push either.
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

    -- ── Budget warning push ── same threshold/dedup shape as notify_parents_on_child_spend(),
    -- for the account owner's own monthly_limit instead of a child's, own dedup table.
    IF NOT NEW.is_expense THEN
        RETURN NEW;
    END IF;

    SELECT monthly_limit INTO v_limit FROM zad_users WHERE id = NEW.user_id;
    IF v_limit IS NULL OR v_limit <= 0 THEN
        RETURN NEW;
    END IF;

    current_month := to_char(NEW.created_at, 'YYYY-MM');

    SELECT COALESCE(SUM(amount), 0) INTO month_spent
    FROM zad_transactions
    WHERE user_id = NEW.user_id
      AND is_expense = true
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

DROP TRIGGER IF EXISTS trigger_notify_telegram_on_transaction ON zad_transactions;
CREATE TRIGGER trigger_notify_telegram_on_transaction
    AFTER INSERT ON zad_transactions
    FOR EACH ROW EXECUTE FUNCTION public.notify_telegram_on_transaction();
