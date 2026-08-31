// verify-purchase — التحقق server-side من مشتريات Google Play.
//
// ليه دي موجودة؟ قبل كده العميل كان بيكتب tier في zad_users مباشرة بعد الشراء
// (client-side trust) — أي جهاز معدّل يقدر يفتح Pro ببلاش. هنا السيرفر هو مصدر
// الحقيقة: بنستدعي Google Play Developer API بالتوكن نفسه، ولو Google قالت
// إن الاشتراك فعلاً مدفوع ونشط، ساعتها وبس ساعتها بنرقّي المستخدم.
//
// المطلوب في البيئة (Supabase secrets):
//   GOOGLE_SERVICE_ACCOUNT_JSON — مفتاح service account كامل (JSON string)
//                                 مع صلاحية androidpublisher على Play Console
//   GOOGLE_PLAY_PACKAGE_NAME    — مثال: com.example.zad (package التطبيق الفعلي)
//
// التدفق: التطبيق يبعت { purchaseToken, orderId, productId }
// → هنا نتحقق من Google → zad_set_tier RPC بيكتب في zad_entitlements.
//
// ⚠️ إصلاح 2026-08-31 (بند 30.6): الملف ده كان مكسور من أربع جهات، وكل عملية شراء
// ناجحة كانت بترجع internal_error بعد ما جوجل تأكد الدفع فعلاً:
//   1. المتغير `pid` كان مستخدَم في أربع سطور ومعرَّفش خالص → ReferenceError.
//   2. كان بيكتب zad_users.{tier, subscription_status, subscription_expires_at}
//      والتلات أعمدة دي مش موجودة في الجدول (متأكَّد من information_schema الحي).
//   3. كان بيعمل upsert على جدول `subscriptions` وهو مش موجود في القاعدة أصلاً.
//   4. أخطاء 2 و3 كانت مش متفحوصة فبتتبلع بصمت.
// الكتابة الشغالة الوحيدة هي zad_set_tier RPC → zad_entitlements، وده مصدر الحقيقة
// اللي entitlement.ts بيقرا منه (وبيصفّر الكوتة للشهر الجديد كمان). الكتابتين
// الميتين اتشالوا — الصح إننا نشيل كود ميت مش نضيف أعمدة عشانه.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

// ملاحظة: `tier` و`isAnnual` كانوا في الواجهة دي ومحدش بيبعتهم —
// GooglePlayBillingManager.verifyWithServer بيبعت التلاتة دول بس. والـ tier
// بيتحدد سيرفر-سايد من productId (anti-spoof)، فقبوله من الجسم كان هيبقى ثغرة.
interface VerifyRequest {
  purchaseToken: string;
  orderId?: string;
  productId?: string;
}

interface PlaySubscriptionState {
  expiryTimeMillis: string;
  paymentState?: number; // 1 = paid, 0 = free trial, 2 = pending
  cancelReason?: number;
  acknowledged?: boolean;
  autoRenewing?: boolean;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), {
      status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    // 1) مصادقة المستخدم من توكن Supabase نفسه — مش من الجسم
    const authHeader = req.headers.get("Authorization") ?? "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const authClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: userData, error: userErr } = await authClient.auth.getUser();
    if (userErr || !userData?.user) {
      return new Response(JSON.stringify({ error: "unauthorized" }), {
        status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const userId = userData.user.id;

    const body: VerifyRequest = await req.json();
    if (!body.purchaseToken || !body.productId) {
      return new Response(JSON.stringify({ error: "missing_fields" }), {
        status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 2) التحقق من Google Play Developer API
    const saJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
    const packageName = Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME");
    if (!saJson || !packageName) {
      console.error("verify-purchase misconfigured: missing service account or package name");
      return new Response(JSON.stringify({ error: "server_misconfigured" }), {
        status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const jwtClient = new JWT({
      key: JSON.parse(saJson),
      scopes: ["https://www.googleapis.com/auth/androidpublisher"],
    });
    const accessToken = await jwtClient.getAccessToken();
    if (!accessToken.token) {
      return new Response(JSON.stringify({ error: "google_auth_failed" }), {
        status: 502, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const playUrl =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(packageName)}/purchases/subscriptions/` +
      `${encodeURIComponent(body.productId)}/tokens/${encodeURIComponent(body.purchaseToken)}`;

    const playRes = await fetch(playUrl, {
      headers: { Authorization: `Bearer ${accessToken.token}` },
    });
    if (!playRes.ok) {
      console.error("Play API rejected token:", playRes.status, await playRes.text());
      return new Response(JSON.stringify({ error: "purchase_verification_failed", valid: false }), {
        status: 402, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const sub = (await playRes.json()) as PlaySubscriptionState;

    // 3) فحوصات الحالة: مدفوع وغير ملغي ولم ينتهِ
    const expiryMs = parseInt(sub.expiryTimeMillis, 10);
    const now = Date.now();
    const paid = sub.paymentState === undefined || sub.paymentState === 1; // بعض الردود القديمة بتحذفه
    const notCancelled = sub.cancelReason === undefined;
    if (!paid || !notCancelled || expiryMs < now) {
      return new Response(JSON.stringify({ valid: false, reason: "inactive_subscription" }), {
        status: 402, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 4) tier من productId نفسه — مش من جسم الطلب (anti-spoof).
    // خريطة موحّدة مع GooglePlayBillingManager.ZadSubscriptionPlan وزاد_tiers في القاعدة:
    //   zad_sub_basic_monthly → starter (15 رؤية عقل/شهر)
    //   zad_sub_plus_monthly  → plus   (50 رؤية عقل/شهر)
    //   zad_sub_ultra_monthly → pro    (150 رؤية عقل + unlimited chat/شهر)
    const PID_TO_TIER: Record<string, string> = {
      "zad_sub_basic_monthly": "starter",
      "zad_sub_plus_monthly": "plus",
      "zad_sub_ultra_monthly": "pro",
    };
    const pid = body.productId.toLowerCase();
    const tier = PID_TO_TIER[pid]
      // fallback للمنتجات القديمة بالاسم (لو حصلت) — من الـ pid نفسه مش من الجسم
      ?? (pid.includes("pro") || pid.includes("ultra") ? "pro"
        : pid.includes("starter") || pid.includes("basic") ? "starter"
        : "plus");

    // 5) الكتابة في الداتابيز بمفتاح الخدمة (تجاوز RLS بشكل آمن ومقصود هنا).
    // zad_set_tier هي الكتابة الوحيدة المطلوبة: بتعمل refresh للصف، تكتب
    // tier + tier_expires_at في zad_entitlements، وتصفّر الكوتة للشهر الجديد
    // (من غير التصفير العميل بيدفع النهاردة وياخد الكوتة الجديدة لما دورته
    // القديمة تخلص بالصدفة).
    const sb = createClient(supabaseUrl, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const expiry = new Date(expiryMs).toISOString();

    // 4.5) التوكن ده محجوز لحد تاني؟ — الفحص ده **قبل** الترقية عن قصد.
    // جوجل بتأكد إن التوكن شراء حقيقي مدفوع، بس مش بتقول إنه بتاع الحساب ده.
    // من غير الفحص، حد ياخد توكن صحيح ويستخدمه على أكتر من حساب وكلهم يترقّوا.
    // الحارس النهائي هو zad_entitlements_purchase_token_uniq، وده بيخلي الرفض
    // يحصل قبل المنح مش بعده.
    const { data: holder } = await sb.from("zad_entitlements")
      .select("user_id").eq("purchase_token", body.purchaseToken).maybeSingle();
    if (holder && holder.user_id !== userId) {
      console.warn("verify-purchase: purchase token already claimed by another account", {
        userId, holder: holder.user_id,
      });
      return new Response(JSON.stringify({ valid: false, reason: "token_already_claimed" }), {
        status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: tierResult, error: tierErr } = await sb.rpc("zad_set_tier", {
      p_user: userId, p_tier: tier, p_expires: expiry,
    });

    // جوجل أكدت الدفع بس الترقية مكتبتش — ده فشل حقيقي ولازم يتقال. لو رجعنا
    // valid:true هنا، العميل يدفع وياخد رسالة نجاح ومفيش ترقية، ومفيش أي مسار
    // بيعيد المحاولة. العميل بيعيد النداء تلقائياً عند فتح التطبيق
    // (queryActivePurchases → verifyWithServer)، فالخطأ هنا مش نهاية الطريق.
    const rpcOk = tierResult && (tierResult as { ok?: boolean }).ok !== false;
    if (tierErr || !rpcOk) {
      console.error(
        "verify-purchase: Google approved the purchase but zad_set_tier failed",
        { userId, tier, rpcError: tierErr?.message, rpcResult: tierResult },
      );
      return new Response(JSON.stringify({ valid: false, reason: "entitlement_write_failed" }), {
        status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 6) تخزين التوكن — ده اللي بيخلي RTDN تعرف ترجع للعميل ده. من غيره
    // التجديد والإلغاء من جوجل مبيوصلوش (zad-billing-webhook بيدوّر بالتوكن).
    // fail-open مقصود: الترقية نجحت خلاص، وفشل تخزين التوكن يعني تتبع أضعف
    // مش شراء ضايع — والنداء الجاي من التطبيق هيحاول تاني.
    const { error: tokenErr } = await sb.from("zad_entitlements")
      .update({ purchase_token: body.purchaseToken })
      .eq("user_id", userId);
    if (tokenErr) {
      console.warn("verify-purchase: tier granted but purchase_token not stored —",
        "RTDN renewal/cancel tracking will not work for this user:", tokenErr.message);
    }

    return new Response(JSON.stringify({ valid: true, tier, expires: expiry }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("verify-purchase error:", e);
    return new Response(JSON.stringify({ error: "internal_error" }), {
      status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
