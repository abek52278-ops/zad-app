// push.ts — إشعار FCM فوري للعميل (الوعي اللحظي للأيدجنت).
//
// الفكرة: قبل كده التنبيهات الاستباقية بتوصل عن طريق تليجرام (لو مربوط) أو إشعارات
// محلية للتطبيق فتح — يعني لو العميل قافل التطبيق ومش مربوط تليجرام، زاد بيبقى صامت.
// ده بيكمّل الحلقة: السيرفر يبعت FCM مباشر لكل جهاز مسجل في zad_fcm_tokens.
//
// الأمان: FCM_SERVER_KEY سيرفر-سايد فقط (secret) — التوكنات بتتقرا بـ service-role.
// الفشل fire-and-forget: الإشعار ده تحسين، مش مسار حياة — لو وقع مفيش حاجة تتكسر.

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";

const FCM_ENDPOINT = "https://fcm.googleapis.com/fcm/send";

export type PushDelivery = "sent" | "no_tokens" | "no_key" | "failed";

/**
 * بيبعت إشعار FCM لكل أجهزة العميل. fire-and-forget — بيرجع الحالة بس للتشخيص.
 * نص الرسالة بيتاخد زي ما هو (نفس النص اللي بيتكتب في الرؤى/تليجرام).
 */
export async function pushToDevice(
  sb: SupabaseClient,
  userId: string,
  title: string,
  body: string,
): Promise<PushDelivery> {
  try {
    const serverKey = Deno.env.get("FCM_SERVER_KEY") ?? "";
    if (!serverKey) return "no_key";

    const { data: rows } = await sb.from("zad_fcm_tokens")
      .select("token")
      .eq("user_id", userId);
    const tokens = (rows ?? []).map((r: { token: string }) => r.token).filter(Boolean);
    if (!tokens.length) return "no_tokens";

    // إرسال لكل توكن منفصلاً — رسايل multi-token عبر fcm/send القديم بتترفض جماعياً.
    let anySent = false;
    await Promise.all(tokens.map(async (token: string) => {
      try {
        const res = await fetch(FCM_ENDPOINT, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `key=${serverKey}`,
          },
          body: JSON.stringify({
            to: token,
            notification: { title, body },
            data: { title, body },
            priority: "high",
            android: { priority: "high" },
          }),
        });
        if (res.ok) anySent = true;
        // توكن مات (410/404) → نمسحه عشان المرة الجاية ميفشلش
        else if (res.status === 404 || res.status === 410) {
          await sb.from("zad_fcm_tokens").delete().eq("token", token);
        }
      } catch { /* توكن واحد فاشل مابوظش الباقي */ }
    }));
    return anySent ? "sent" : "failed";
  } catch (e) {
    console.error("pushToDevice failed:", e);
    return "failed";
  }
}
