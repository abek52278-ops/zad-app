-- زاد-برين بيكتب أسئلة/رؤى حقيقية مبنية على تعلّمه (zad_insights: kind='question' من
-- ask_user، أو priority='critical' من emit_insight) — لكن دي كانت بتظهر بس في كارت
-- "رؤية زاد الذكي" على الرئيسية. لو المستخدم رابط تيليجرام ومش فاتح التطبيق، السؤال
-- يفضل مستني بلا داعي لحد ما يفتح التطبيق بنفسه، رغم إن نفس آلية الـ push
-- (realtime_push على zad-telegram-bot) موجودة بالفعل لتنبيهات الميزانية.
--
-- نطاق مقصود: kind='question' (محتاج رد فعلي) أو priority='critical' بس — مش كل
-- emit_insight عادي، عشان تيليجرام (إشعار فعلي على التليفون) ميبقاش مصدر إزعاج لكل
-- ملاحظة بسيطة العقل بيكتبها.
--
-- بيتفعّل بس على INSERT حقيقي (مش upsert بيرجع لصف موجود) — نفس (user_id, dedupe_key)
-- اللي emit_insight/ask_user بيعملوله upsert مش هيطلق التريجر تاني لو اتحدّث بس، وده
-- بالظبط اللي بيمنع تكرار نفس السؤال كإشعار كل مرة العقل يعيد تقييمه.

CREATE OR REPLACE FUNCTION public.notify_telegram_on_insight()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
DECLARE
    is_linked BOOLEAN;
    push_title TEXT;
    push_body TEXT;
BEGIN
    IF NEW.status != 'pending' OR NOT (NEW.kind = 'question' OR NEW.priority = 'critical') THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM telegram_bindings WHERE user_id = NEW.user_id AND bound_at IS NOT NULL
    ) INTO is_linked;
    IF NOT is_linked THEN
        RETURN NEW;
    END IF;

    push_title := CASE WHEN NEW.kind = 'question' THEN '❓ سؤال من زاد' ELSE '🔔 تنبيه من زاد' END;
    push_body := NEW.title || E'\n' || NEW.body
        || CASE WHEN NEW.kind = 'question' THEN E'\n\nرد هنا بإجابتك.' ELSE '' END;

    PERFORM net.http_post(
        url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
        headers:='{"Content-Type":"application/json","X-Realtime-Push-Secret":"7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094"}'::jsonb,
        body:=jsonb_build_object('user_id', NEW.user_id, 'title', push_title, 'body', push_body),
        timeout_milliseconds:=15000
    );

    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trigger_notify_telegram_on_insight ON zad_insights;
CREATE TRIGGER trigger_notify_telegram_on_insight
    AFTER INSERT ON zad_insights
    FOR EACH ROW EXECUTE FUNCTION public.notify_telegram_on_insight();

REVOKE EXECUTE ON FUNCTION public.notify_telegram_on_insight() FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.notify_telegram_on_insight() TO service_role;
