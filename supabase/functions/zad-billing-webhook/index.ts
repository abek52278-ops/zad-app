// deno-lint-ignore-file
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(supabaseUrl, serviceKey);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, x-client-info, Content-Type, Authorization",
  "Content-Type": "application/json",
};

interface PlanConfig {
  tier: "basic" | "plus" | "ultra";
  title: string;
  monthlyAiQuota: number; // -1 for unlimited
  adFree: boolean;
  familyLimit: number;
  priceUsd: number;
}

const PLAN_MAP: Record<string, PlanConfig> = {
  "zad_sub_basic_monthly": {
    tier: "basic",
    title: "الأساسية (Basic)",
    monthlyAiQuota: 50,
    adFree: true,
    familyLimit: 1,
    priceUsd: 9.99,
  },
  "zad_sub_plus_monthly": {
    tier: "plus",
    title: "المتقدمة (Plus)",
    monthlyAiQuota: 250,
    adFree: true,
    familyLimit: 2,
    priceUsd: 19.99,
  },
  "zad_sub_ultra_monthly": {
    tier: "ultra",
    title: "الفائقة (Ultra)",
    monthlyAiQuota: -1,
    adFree: true,
    familyLimit: 5,
    priceUsd: 49.99,
  },
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();
    console.log(`[ZadBillingWebhook] Received request:`, JSON.stringify(body));

    // ── 1. Google Play Real-Time Developer Notification (Pub/Sub RTDN) ──
    if (body.message && body.message.data) {
      const decodedString = atob(body.message.data);
      const rtdnData = JSON.parse(decodedString);
      console.log(`[ZadBillingWebhook] Decoded RTDN notification:`, rtdnData);

      const subEvent = rtdnData.subscriptionNotification;
      if (subEvent) {
        const purchaseToken = subEvent.purchaseToken;
        const notificationType = subEvent.notificationType;

        // 1=RECOVERED, 2=RENEWED, 3=CANCELED, 4=PURCHASED, 12=REVOKED, 13=EXPIRED
        const isActive = [1, 2, 4].includes(notificationType);

        if (purchaseToken) {
          const { error: updateErr } = await admin
            .from("zad_subscriptions")
            .update({
              status: isActive ? "active" : "canceled",
              rtdn_last_event: notificationType,
              updated_at: new Date().toISOString(),
            })
            .eq("purchase_token", purchaseToken);

          if (updateErr) {
            console.error(`[ZadBillingWebhook] Failed to update RTDN sub: ${updateErr.message}`);
          }
        }
      }

      return new Response(JSON.stringify({ status: "rtdn_processed" }), {
        status: 200,
        headers: corsHeaders,
      });
    }

    // ── 2. Direct App Purchase Verification Flow ──
    const { action, user_id, order_id, purchase_token, package_name, products, purchase_time } = body;

    if (action === "verify_google_play_purchase") {
      if (!purchase_token || !products || products.length === 0) {
        return new Response(JSON.stringify({ error: "missing_purchase_details" }), {
          status: 400,
          headers: corsHeaders,
        });
      }

      const productId = products[0];
      const plan = PLAN_MAP[productId] || PLAN_MAP["zad_sub_plus_monthly"];

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 30); // 30 days monthly cycle

      // Record / Upsert subscription in database
      const subscriptionRecord = {
        user_id: user_id || "anonymous",
        order_id: order_id || `GPA.${Date.now()}`,
        purchase_token: purchase_token,
        product_id: productId,
        package_name: package_name || "com.aistudio.zad.wrtqvx",
        tier: plan.tier,
        status: "active",
        ad_free: plan.adFree,
        monthly_ai_quota: plan.monthlyAiQuota,
        family_limit: plan.familyLimit,
        price_usd: plan.priceUsd,
        expires_at: expiresAt.toISOString(),
        updated_at: new Date().toISOString(),
      };

      const { data: subData, error: subErr } = await admin
        .from("zad_subscriptions")
        .upsert(subscriptionRecord, { onConflict: "purchase_token" })
        .select()
        .single();

      if (subErr) {
        console.warn(`[ZadBillingWebhook] Note on subscriptions upsert: ${subErr.message}`);
      }

      // Also update user profile if user_id is provided
      if (user_id && user_id !== "anonymous") {
        await admin
          .from("user_behavior_profile")
          .upsert({
            user_id: user_id,
            subscription_tier: plan.tier,
            ad_free: plan.adFree,
            ai_quota_monthly: plan.monthlyAiQuota,
            family_sharing_limit: plan.familyLimit,
            subscription_expires_at: expiresAt.toISOString(),
            updated_at: new Date().toISOString(),
          }, { onConflict: "user_id" });
      }

      return new Response(
        JSON.stringify({
          success: true,
          message: `تم توثيق اشتراك ${plan.title} بنجاح`,
          entitlement: {
            tier: plan.tier,
            title: plan.title,
            ad_free: plan.adFree,
            monthly_ai_quota: plan.monthlyAiQuota,
            family_limit: plan.familyLimit,
            expires_at: expiresAt.toISOString(),
          },
        }),
        { status: 200, headers: corsHeaders }
      );
    }

    return new Response(JSON.stringify({ error: "unknown_action" }), {
      status: 400,
      headers: corsHeaders,
    });
  } catch (error: any) {
    console.error(`[ZadBillingWebhook] Server error: ${error.message}`);
    return new Response(
      JSON.stringify({ error: error.message || "internal_server_error" }),
      { status: 500, headers: corsHeaders }
    );
  }
});
