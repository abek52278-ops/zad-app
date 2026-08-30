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

    // ── 2. Direct App Purchase Verification Flow — RETIRED 2026-08-30 ──
    // كان المسار بيكتب اشتراك "active" لأي purchase_token من غير أي اتصال بـ Google
    // Play Developer API — أي حد بيتبعتله توكن (أو بيكتبه يدوي) بياخد tier كامل 30 يوم.
    // التوثيق الحقيقي الوحيد: Edge Function verify-purchase (androidpublisher + service
    // account) — التطبيق بيكلمه من GooglePlayBillingManager.verifyWithServer. RTDN فوق
    // لسه شغال لأنه بس بيحدّث صفوف موجودة بالتوكن، مش بيخترع اشتراكات.
    if (body?.action === "verify_google_play_purchase") {
      console.warn("[ZadBillingWebhook] legacy verify path rejected — use verify-purchase function");
      return new Response(
        JSON.stringify({
          error: "verification_retired",
          message: "هذا المسار اتقفل. التوثيق الحقيقي بيحصل عبر verify-purchase مع Google Play Developer API.",
        }),
        { status: 410, headers: corsHeaders },
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
