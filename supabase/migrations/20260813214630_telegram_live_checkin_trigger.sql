-- كان فحص "قرب يخلص" لتيليجرام بيحصل مرة واحدة باليوم بس (runDailyCheckins cron)،
-- سقفه سؤالين. لو صنف نزل تحت الحد بعد الفحص اليومي، المستخدم ميعرفش غير بكرة. دلوقتي
-- تريجر فوري على أي تحديث كمية بيبعت تنبيه لحظي — بس أول مرة الصنف يعدي للحالة دي
-- (مش كل تنقيص كمية وهو أصلاً تحت الحد، عشان ميبقاش إزعاج متكرر لكل جرعة استهلاك).
--
-- نفس معيار findCheckInCandidates (zad-telegram-bot) بالظبط: تحت threshold، أو معدل
-- استهلاك متعلّم (rate_known) وباقي يومين أو أقل بيه — مكرر هنا عمداً مش مستدعى من
-- TS، لأن التريجر لازم يقرر لحظة الكتابة نفسها من غير نداء شبكة لسيرفر تاني.

CREATE OR REPLACE FUNCTION public._inventory_needs_checkin(p_quantity NUMERIC, p_threshold NUMERIC, p_avg_daily_qty NUMERIC, p_rate_known BOOLEAN)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
AS $function$
    SELECT p_quantity <= COALESCE(p_threshold, 2)
        OR (COALESCE(p_rate_known, false) AND COALESCE(p_avg_daily_qty, 0) > 0 AND p_quantity / p_avg_daily_qty <= 2);
$function$;

CREATE OR REPLACE FUNCTION public.notify_telegram_on_low_stock()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
DECLARE
    is_linked BOOLEAN;
    v_avg_daily_qty NUMERIC;
    v_rate_known BOOLEAN;
    was_needing BOOLEAN;
    now_needing BOOLEAN;
BEGIN
    -- كمية زادت أو ما اتغيرتش — مش الاتجاه اللي بيهمنا هنا (اللي بيهمنا النقصان).
    IF NEW.quantity >= OLD.quantity THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM telegram_bindings WHERE user_id = NEW.user_id AND bound_at IS NOT NULL
    ) INTO is_linked;
    IF NOT is_linked THEN
        RETURN NEW;
    END IF;

    SELECT avg_daily_qty, rate_known INTO v_avg_daily_qty, v_rate_known
        FROM zad_consumption WHERE user_id = NEW.user_id AND item_name = NEW.item_name;

    was_needing := public._inventory_needs_checkin(OLD.quantity, OLD.low_stock_threshold, v_avg_daily_qty, v_rate_known);
    now_needing := public._inventory_needs_checkin(NEW.quantity, NEW.low_stock_threshold, v_avg_daily_qty, v_rate_known);

    -- بس لحظة العبور (لسه ما كانش محتاج → بقى محتاج) — مش كل تحديث وهو أصلاً محتاج.
    IF was_needing OR NOT now_needing THEN
        RETURN NEW;
    END IF;

    PERFORM net.http_post(
        url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=live_checkin',
        headers:='{"Content-Type":"application/json","X-Live-Checkin-Secret":"58dda37fa693d2351ab038f07303fb9b621ce983c597046de7c7edc2b6d283a6"}'::jsonb,
        body:=jsonb_build_object('user_id', NEW.user_id, 'item_name', NEW.item_name, 'quantity', NEW.quantity),
        timeout_milliseconds:=15000
    );

    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trigger_notify_telegram_on_low_stock ON zad_inventory;
CREATE TRIGGER trigger_notify_telegram_on_low_stock
    AFTER UPDATE ON zad_inventory
    FOR EACH ROW EXECUTE FUNCTION public.notify_telegram_on_low_stock();

REVOKE EXECUTE ON FUNCTION public._inventory_needs_checkin(NUMERIC, NUMERIC, NUMERIC, BOOLEAN) FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public._inventory_needs_checkin(NUMERIC, NUMERIC, NUMERIC, BOOLEAN) TO service_role;
REVOKE EXECUTE ON FUNCTION public.notify_telegram_on_low_stock() FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.notify_telegram_on_low_stock() TO service_role;
