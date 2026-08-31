// zad-billing-webhook — مستقبِل إشعارات Google Play اللحظية (RTDN عبر Pub/Sub).
//
// ⚠️ إعادة كتابة 2026-08-31 (بند 30.6ب). المسار ده كان **ميت بالكامل** من تلات
// طبقات فوق بعض، وكل طبقة كانت كفيلة لوحدها إنها توقّفه:
//
//   1. البوابة: الدالة مكانتش معرَّفة في supabase/config.toml، والـ CLI بيفترض
//      verify_jwt = true لأي دالة مش لاقيها. Pub/Sub بتاعة جوجل **مبتبعتش توكن
//      سوبابيز خالص**، فكل إشعار كان بيتصدّ عند البوابة قبل ما الكود ده يشتغل.
//      ده كان السبب الأول — والأعمدة الغلط تحت كانت العَرَض التاني بس.
//   2. الجدول: كان بيحدّث zad_subscriptions ويسيب فيه status / rtdn_last_event /
//      updated_at ويفلتر بـ purchase_token — **ولا عمود من الأربعة موجود**.
//      وأصلاً zad_subscriptions ده جدول اشتراكات العميل الشخصية (نتفليكس، شاهد)
//      مش جدول فوترة التطبيق. الجدول الصح zad_entitlements.
//   3. الهوية: RTDN بتعرّف نفسها بـ purchase_token وبس، ومفيش أي جدول كان
//      بيخزّن التوكن — فحتى بعد إصلاح 1 و2، مكانش فيه طريقة نترجم بيها
//      إشعار لعميل. اتحل بـ zad_entitlements.purchase_token (ميجريشن
//      20260831130000) اللي verify-purchase بيكتبه بعد تأكيد جوجل.
//
// التوثيق دلوقتي: سيكريت في الـ query string بتاع push endpoint
// (?secret=<ZAD_RTDN_SECRET>) — الـ endpoint متظبّط في Google Cloud console مش
// في git، فالسر مبيتسربش. نفس نمط X-Checkin-Cron-Secret الموجود في المشروع.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(supabaseUrl, serviceKey);

const RTDN_SECRET = Deno.env.get("ZAD_RTDN_SECRET") ?? "";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, x-client-info, Content-Type, Authorization",
  "Content-Type": "application/json",
};

// نفس الخريطة اللي في verify-purchase بالظبط — المستوى بيتحدد من productId
// بتاع الاشتراك الفعلي، مش من أي حاجة في جسم الإشعار.
// (الـ PLAN_MAP القديمة هنا اتشالت: كانت بتستخدم مستويات basic/plus/ultra
//  و zad_tiers الحقيقية free/starter/plus/pro — يعني basic و ultra مش موجودين
//  أصلاً — وكانت كود ميت مش متندي من أي حتة بعد تقاعد مسار الشراء المباشر.)
const PID_TO_TIER: Record<string, string> = {
  "zad_sub_basic_monthly": "starter",
  "zad_sub_plus_monthly": "plus",
  "zad_sub_ultra_monthly": "pro",
};

function tierFromProductId(productId: string | undefined): string | null {
  if (!productId) return null;
  const pid = productId.toLowerCase();
  return PID_TO_TIER[pid]
    ?? (pid.includes("pro") || pid.includes("ultra") ? "pro"
      : pid.includes("starter") || pid.includes("basic") ? "starter"
      : "plus");
}

/**
 * بيسأل Google Play عن حالة الاشتراك الحالية.
 *
 * ليه بنسأل تاني وإحنا خدنا إشعار؟ لأن RTDN بتقول "حصل تجديد" وبس — مبتقولش
 * تاريخ الانتهاء الجديد. من غير السؤال ده هنعرف إن فيه تجديد ومش هنعرف نمدّد
 * لحد إمتى، فالعميل يدفع ويفضل tier_expires_at بتاعه على التاريخ القديم.
 *
 * (نفس منطق verify-purchase. متكرر عن قصد: الاتنين بيتنشروا كـ bundles منفصلة
 *  ومفيش _shared في المشروع، ومشاركة الكود بينهم كانت هتحتاج إعادة هيكلة نشر.)
 */
async function fetchPlaySubscription(productId: string, purchaseToken: string) {
  const saJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
  const packageName = Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME");
  if (!saJson || !packageName) return null;

  const jwtClient = new JWT({
    key: JSON.parse(saJson),
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  const accessToken = await jwtClient.getAccessToken();
  if (!accessToken.token) return null;

  const res = await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(packageName)}/purchases/subscriptions/` +
    `${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`,
    { headers: { Authorization: `Bearer ${accessToken.token}` } },
  );
  if (!res.ok) {
    console.error("[RTDN] Play API rejected lookup:", res.status, (await res.text()).slice(0, 300));
    return null;
  }
  return await res.json() as { expiryTimeMillis?: string };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  // الدالة شغالة بـ verify_jwt = false عشان Pub/Sub تعرف توصلها، فالتوثيق
  // مسؤوليتنا. لو السيكريت مش متظبّط، بنقفل بدل ما نفتح — endpoint شغال
  // بمفتاح الخدمة ومفتوح للإنترنت أسوأ من endpoint مقفول.
  if (!RTDN_SECRET) {
    console.error("[RTDN] ZAD_RTDN_SECRET غير مضبوط — رفض كل الطلبات");
    return new Response(JSON.stringify({ error: "server_misconfigured" }), { status: 500, headers: corsHeaders });
  }
  const provided = new URL(req.url).searchParams.get("secret")
    ?? req.headers.get("X-Rtdn-Secret") ?? "";
  if (provided !== RTDN_SECRET) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: corsHeaders });
  }

  try {
    const body = await req.json();

    // ── مسار الشراء المباشر — متقاعد 2026-08-30 ──
    // كان بيكتب اشتراك "active" لأي purchase_token من غير أي اتصال بجوجل: أي
    // حد بيتبعتله توكن (أو بيكتبه يدوي) كان بياخد tier كامل ٣٠ يوم. التوثيق
    // الحقيقي الوحيد هو verify-purchase (androidpublisher + service account).
    if (body?.action === "verify_google_play_purchase") {
      console.warn("[RTDN] legacy verify path rejected — use verify-purchase function");
      return new Response(
        JSON.stringify({
          error: "verification_retired",
          message: "هذا المسار اتقفل. التوثيق الحقيقي بيحصل عبر verify-purchase مع Google Play Developer API.",
        }),
        { status: 410, headers: corsHeaders },
      );
    }

    if (!body?.message?.data) {
      return new Response(JSON.stringify({ error: "unknown_action" }), { status: 400, headers: corsHeaders });
    }

    // ── Google Play RTDN عبر Pub/Sub ──
    // من هنا لآخر الدالة بنرجع 200 على طول، حتى مع الأخطاء. Pub/Sub بتعيد
    // المحاولة على أي رد مش-2xx، فرد خطأ على إشعار إحنا مش فاهمينه بيعمل
    // حلقة إعادة محاولة لا نهائية. اللوج هو مكان الشكوى، مش كود الحالة.
    const rtdn = JSON.parse(atob(body.message.data));
    const subEvent = rtdn?.subscriptionNotification;
    if (!subEvent?.purchaseToken) {
      console.log("[RTDN] إشعار من غير subscriptionNotification — اتتجاهل:", JSON.stringify(rtdn).slice(0, 300));
      return new Response(JSON.stringify({ status: "ignored_non_subscription" }), { status: 200, headers: corsHeaders });
    }

    const { purchaseToken, notificationType, subscriptionId } = subEvent;

    // التوكن → العميل. ده الربط اللي مكانش موجود خالص قبل ميجريشن 20260831130000.
    const { data: ent, error: lookupErr } = await admin
      .from("zad_entitlements")
      .select("user_id, tier, tier_expires_at")
      .eq("purchase_token", purchaseToken)
      .maybeSingle();

    if (lookupErr) {
      console.error("[RTDN] فشل البحث عن التوكن:", lookupErr.message);
      return new Response(JSON.stringify({ status: "lookup_failed" }), { status: 200, headers: corsHeaders });
    }
    if (!ent) {
      // طبيعي ومتوقع: جوجل بتبعت PURCHASED قبل ما التطبيق يخلص verify-purchase
      // أحياناً، فالتوكن لسه متسجّلش. verify-purchase هو اللي بيمنح الترقية
      // مش إحنا، فمفيش حاجة ضايعة — الإشعارات الجاية هتلاقي التوكن.
      console.log("[RTDN] توكن مش معروف (لسه متسجّلش من verify-purchase):", notificationType);
      return new Response(JSON.stringify({ status: "token_not_registered" }), { status: 200, headers: corsHeaders });
    }

    // أنواع الإشعارات من Google Play Developer API:
    //   1=RECOVERED 2=RENEWED 3=CANCELED 4=PURCHASED 5=ON_HOLD
    //   6=IN_GRACE_PERIOD 7=RESTARTED 10=PAUSED 12=REVOKED 13=EXPIRED
    const EXTENDS = [1, 2, 4, 7];   // فيه فلوس دخلت أو رجعت الخدمة → مدّد
    const REVOKES = [12, 13];        // انتهى أو اتسحب → نزّل دلوقتي

    if (EXTENDS.includes(notificationType)) {
      const tier = tierFromProductId(subscriptionId) ?? ent.tier;
      const play = await fetchPlaySubscription(subscriptionId, purchaseToken);
      const expiryMs = play?.expiryTimeMillis ? parseInt(play.expiryTimeMillis, 10) : NaN;
      if (!Number.isFinite(expiryMs)) {
        // عرفنا إن فيه تجديد بس مش عارفين لحد إمتى. **متمددش بتاريخ مخترع** —
        // الأصح إن العميل يفضل على تاريخه الحالي والتطبيق يصحّح عند أول فتح
        // (queryActivePurchases → verifyWithServer) من إننا نكتب رقم مش من جوجل.
        console.error("[RTDN] تجديد من غير تاريخ انتهاء من جوجل — متمددش:", { notificationType, subscriptionId });
        return new Response(JSON.stringify({ status: "renewal_without_expiry" }), { status: 200, headers: corsHeaders });
      }
      const expiry = new Date(expiryMs).toISOString();
      const { data: rpcResult, error: rpcErr } = await admin.rpc("zad_set_tier", {
        p_user: ent.user_id, p_tier: tier, p_expires: expiry,
      });
      if (rpcErr || (rpcResult && (rpcResult as { ok?: boolean }).ok === false)) {
        console.error("[RTDN] فشل تمديد المستوى:", { user: ent.user_id, tier, rpcErr: rpcErr?.message, rpcResult });
      } else {
        console.log("[RTDN] المستوى اتمدّد:", { user: ent.user_id, tier, expiry, notificationType });
      }
      return new Response(JSON.stringify({ status: "extended" }), { status: 200, headers: corsHeaders });
    }

    if (REVOKES.includes(notificationType)) {
      const { error: rpcErr } = await admin.rpc("zad_set_tier", {
        p_user: ent.user_id, p_tier: "free", p_expires: null,
      });
      if (rpcErr) console.error("[RTDN] فشل التنزيل للمجاني:", rpcErr.message);
      else console.log("[RTDN] اتنزّل للمجاني:", { user: ent.user_id, notificationType });
      return new Response(JSON.stringify({ status: "revoked" }), { status: 200, headers: corsHeaders });
    }

    // 3=CANCELED معناها **إيقاف التجديد التلقائي**، مش إلغاء فوري — العميل دفع
    // الفترة دي وليه حقه فيها لحد tier_expires_at. الكود القديم كان بيعامل 3
    // زي الإلغاء ويشيل الخدمة فوراً، وده كان هيسحب من عميل حاجة دفع تمنها.
    // 5=ON_HOLD و 6=IN_GRACE_PERIOD و 10=PAUSED كمان مش سحب: جوجل لسه بتحاول
    // تحصّل، والانتهاء هييجي كـ 13 لو فشلت.
    console.log("[RTDN] إشعار متسجّل من غير تغيير مستوى:", { user: ent.user_id, notificationType });
    return new Response(JSON.stringify({ status: "noted", notificationType }), { status: 200, headers: corsHeaders });
  } catch (error) {
    console.error("[RTDN] خطأ في السيرفر:", error instanceof Error ? error.message : String(error));
    // برضه 200 — استثناء عندنا مش سبب إن Pub/Sub تفضل تعيد للأبد.
    return new Response(JSON.stringify({ status: "error_logged" }), { status: 200, headers: corsHeaders });
  }
});
