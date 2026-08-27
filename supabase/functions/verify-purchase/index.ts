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
// التدفق: التطبيق يبعت { tier, isAnnual, purchaseToken, orderId, productId }
// → هنا نتحقق من Google → نكتب tier في zad_users + zad_entitlements + subscriptions.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface VerifyRequest {
  tier: string;
  isAnnual?: boolean;
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

    // 4) tier من productId نفسه — مش من جسم الطلب (anti-spoof)
    const pid = body.productId.toLowerCase();
    const tier = pid.includes("pro") ? "pro" : pid.includes("starter") ? "starter" : "plus";
    const isAnnual = pid.includes("annual") || pid.includes("yearly");

    // 5) الكتابة في الداتابيز بمفتاح الخدمة (تجاوز RLS بشكل آمن ومقصود هنا)
    const sb = createClient(supabaseUrl, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const expiry = new Date(expiryMs).toISOString();

    const { error: tierErr } = await sb.rpc("zad_set_tier", {
      p_user: userId, p_tier: tier, p_expires: expiry,
    });
    if (tierErr) console.warn("zad_set_tier rpc:", tierErr.message);

    await sb.from("zad_users").update({
      tier, subscription_status: "active", subscription_expires_at: expiry,
    }).eq("id", userId);

    await sb.from("zad_entitlements").update({
      tier, tier_expires_at: expiry,
    }).eq("user_id", userId);

    await sb.from("subscriptions").upsert({
      user_id: userId, tier, provider: "google_play", status: "active",
      current_period_start: new Date().toISOString(),
      current_period_end: expiry,
      external_id: body.orderId ?? null,
      notes: body.purchaseToken.slice(0, 32),
    }, { onConflict: "user_id,provider" });

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
