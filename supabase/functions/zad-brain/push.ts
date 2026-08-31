// push.ts — إشعار FCM فوري للعميل (الوعي اللحظي للأيدجنت).
//
// الفكرة: قبل كده التنبيهات الاستباقية بتوصل عن طريق تليجرام (لو مربوط) أو إشعارات
// محلية للتطبيق فتح — يعني لو العميل قافل التطبيق ومش مربوط تليجرام، زاد بيبقى صامت.
// ده بيكمّل الحلقة: السيرفر يبعت FCM مباشر لكل جهاز مسجل في zad_fcm_tokens.
//
// المصادقة: FCM v1 API عبر OAuth2 — FIREBASE_SERVICE_ACCOUNT (service account JSON
// كامل في Supabase secrets) بيتصدّر توكن قصير العمر. مفيش legacy server key —
// جوجل لغتها، وv1 هو المسار المعتمد.
// الأمان: الـ service account سيرفر-سايد فقط. التوكنات بتتقرا بـ service-role.
// الفشل fire-and-forget: الإشعار ده تحسين، مش مسار حياة — لو وقع مفيش حاجة تتكسر.

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";

const FCM_V1_ENDPOINT = "https://fcm.googleapis.com/v1/projects/{project_id}/messages:send";
const TOKEN_CACHE_TTL_MS = 50 * 60 * 1000; // توكن OAuth2 صالح ساعة — نجدده قبلها بـ10 دقايق

let cachedToken: { value: string; expiresAt: number } | null = null;
let cachedProjectId = "";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

function parseServiceAccount(raw: string): ServiceAccount | null {
  try {
    const parsed = JSON.parse(raw);
    if (parsed?.project_id && parsed?.client_email && parsed?.private_key) {
      return parsed as ServiceAccount;
    }
    return null;
  } catch {
    return null;
  }
}

function base64UrlEncode(data: Uint8Array): string {
  let bin = "";
  for (const b of data) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Date.now();
  if (cachedToken && cachedProjectId === sa.project_id && cachedToken.expiresAt > now) {
    return cachedToken.value;
  }
  const header = base64UrlEncode(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = base64UrlEncode(new TextEncoder().encode(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: Math.floor(now / 1000),
    exp: Math.floor(now / 1000) + 3600,
  })));
  const unsigned = `${header}.${claim}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key) as unknown as ArrayBuffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const jwt = `${unsigned}.${base64UrlEncode(new Uint8Array(sig))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`oauth token failed: ${res.status} ${await res.text().catch(() => "")}`);
  const data = await res.json() as { access_token: string };
  cachedToken = { value: data.access_token, expiresAt: now + TOKEN_CACHE_TTL_MS };
  cachedProjectId = sa.project_id;
  return data.access_token;
}

function pemToDer(pem: string): Uint8Array {
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const bin = atob(b64);
  const der = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) der[i] = bin.charCodeAt(i);
  return der;
}

export type PushDelivery = "sent" | "no_tokens" | "no_credentials" | "failed";

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
    const saRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "";
    if (!saRaw) return "no_credentials";
    const sa = parseServiceAccount(saRaw);
    if (!sa) {
      console.error("FIREBASE_SERVICE_ACCOUNT set but not a valid service-account JSON");
      return "no_credentials";
    }

    const { data: rows } = await sb.from("zad_fcm_tokens")
      .select("token")
      .eq("user_id", userId);
    const tokens = (rows ?? []).map((r: { token: string }) => r.token).filter(Boolean);
    if (!tokens.length) return "no_tokens";

    const accessToken = await getAccessToken(sa);
    const endpoint = FCM_V1_ENDPOINT.replace("{project_id}", sa.project_id);
    let anySent = false;

    await Promise.all(tokens.map(async (token: string) => {
      try {
        const res = await fetch(endpoint, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${accessToken}`,
          },
          body: JSON.stringify({
            message: {
              token,
              notification: { title, body },
              data: { title, body },
              android: { priority: "HIGH" },
            },
          }),
        });
        if (res.ok) {
          anySent = true;
          return;
        }
        // توكن مات (UNREGISTERED/INVALID_ARGUMENT على التوكن) → نمسحه عشان المرة الجاية ميفشلش
        if (res.status === 404) {
          await sb.from("zad_fcm_tokens").delete().eq("token", token);
        } else if (res.status === 400) {
          const errText = await res.text().catch(() => "");
          if (errText.includes("UNREGISTERED") || errText.includes("not a valid FCM registration token")) {
            await sb.from("zad_fcm_tokens").delete().eq("token", token);
          } else {
            console.error("FCM v1 400:", errText.slice(0, 300));
          }
        } else {
          console.error(`FCM v1 send failed ${res.status}:`, await res.text().catch(() => "").then?.(() => "") ?? "");
        }
      } catch (e) {
        console.error("FCM token send threw:", e);
      }
    }));
    return anySent ? "sent" : "failed";
  } catch (e) {
    console.error("pushToDevice failed:", e);
    return "failed";
  }
}
