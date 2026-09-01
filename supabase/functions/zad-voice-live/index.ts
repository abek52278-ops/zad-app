// zad-voice-live — بند 33.1: relay صوتي ريل-تايم بين تطبيق زاد وGemini Live API.
//
// الفكرة: العميل بيفتح WebSocket واحد على الفانكشن دي (بتوكن Supabase حقيقي، نفس أي
// نداء موثّق تاني). الفانكشن بتتحقق من الهوية، بتخصم من رصيد "voice" في entitlement،
// وبعدين بتفتح WebSocket تاني — من عندها هي، بمفتاح سيرفر-سايد — لـGemini Live API
// الحقيقي، وترسل رسالة setup الأولى. من ساعتها، أي فريم بيجي من العميل بيتمرر لجيميناي
// زي ما هو، وأي فريم بيرجع من جيميناي بيتمرر للعميل زي ما هو. المفتاح عمره ما بيوصل
// للكلاينت — ده أهم سبب لوجود الفانكشن دي أصلاً بدل ما التطبيق يكلم Gemini مباشرة.
//
// نطاق 33.1 بالظبط: النقل ثنائي الاتجاه بس. الشخصية (33.3) وربط أدوات zad-brain
// بالجلسة الصوتية (33.2) ملاحم منفصلة تُبنى فوق الـrelay ده، مش جواه.
//
// بروتوكول Gemini Live الحقيقي (اتأكد حي 2026-09-01 ضد مفاتيح المشروع، مش من التوثيق):
// - العنوان: wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent
// - التوثيق: باراميتر ?key=<API_KEY> في الرابط نفسه (اتأكد إنه شغال — الهيدر المخصص
//   x-goog-api-key اللي الـSDK الرسمي بيستخدمه مش متاح أصلاً على WebSocket قياسي في
//   Deno/المتصفح، فباراميتر الرابط هو الطريقة الوحيدة الممكنة هنا، ولحسن الحظ شغالة).
// - أول رسالة لازم تتبعت من العميل (هنا: الفانكشن نفسها) هي setup:
//   {"setup":{"model":"models/<name>","generationConfig":{"responseModalities":["AUDIO"]}}}
// - الموديلات اللي بتقبل bidiGenerateContent فعلاً على مشروعنا (`client.models.list()`
//   مفلترة بـsupported_actions): gemini-3.1-flash-live-preview،
//   gemini-2.5-flash-native-audio-preview-{09,12}-2025، gemini-3.5-transcribe-live،
//   gemini-3.5-live-translate-preview. اتأكد حي إن responseModalities لازم تكون
//   ["AUDIO"] مش ["TEXT"] على موديلات الصوت — TEXT بيرجع 1007 "combination of response
//   modalities not supported". الافتراضي هنا gemini-3.1-flash-live-preview (جيل 3.x،
//   نفس تفضيل المشروع الموثّق في CLAUDE.md إن 2.5 كتير منها بيتقفل لعملاء جداد).

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const VOICE_LIVE_MODEL = Deno.env.get("ZAD_VOICE_LIVE_MODEL") ?? "gemini-3.1-flash-live-preview";
const GEMINI_LIVE_HOST = "generativelanguage.googleapis.com";
const GEMINI_LIVE_PATH =
  "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";

// نفس مسبح zad-brain/zad-core-intelligence بالظبط (ZAD_API_KEY_1..5 مع fallback على
// GEMINI_API_KEY المفرد) — سرّ مشترك عن قصد، مش تصادم أسماء. جلسة صوتية طويلة مالهاش
// نفس منطق إعادة المحاولة عبر المسبح كله بتاع النداءات القصيرة؛ محاولة واحدة بمفتاح
// من الدور، ولو فشلت العميل بيعيد المحاولة (فتبدأ بمفتاح تاني تلقائياً).
const GEMINI_KEY_POOL: string[] = [1, 2, 3, 4, 5]
  .map((n) => Deno.env.get(`ZAD_API_KEY_${n}`))
  .filter((k): k is string => !!k);
if (GEMINI_KEY_POOL.length === 0) {
  const legacy = Deno.env.get("ZAD_API_KEY") || Deno.env.get("GEMINI_API_KEY");
  if (legacy) GEMINI_KEY_POOL.push(legacy);
}
let keyCursor = 0;
function nextGeminiKey(): string | null {
  if (GEMINI_KEY_POOL.length === 0) return null;
  const key = GEMINI_KEY_POOL[keyCursor % GEMINI_KEY_POOL.length];
  keyCursor = (keyCursor + 1) % GEMINI_KEY_POOL.length;
  return key;
}

/** أقل نسخة كافية من resolveAuthedUserId (zad-brain/auth.ts) — بدون فرع service-role
 *  عن قصد: جلسة صوتية حية لازم تكون مستخدم حقيقي، مفيش سيناريو cron/بوت هنا. */
async function resolveUserId(req: Request): Promise<string | null> {
  const header = req.headers.get("Authorization") ?? "";
  const token = header.toLowerCase().startsWith("bearer ") ? header.slice(7).trim() : "";
  if (!token) return null;
  try {
    const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
    const { data, error } = await sb.auth.getUser(token);
    return error || !data?.user?.id ? null : data.user.id;
  } catch {
    return null;
  }
}

/** نفس entitlement.ts's consume() بالظبط (zad-brain) — نسخة مقصودة، مفيش استيراد بين
 *  فانكشنز في المشروع ده (كل فانكشن مستقل، نفس نمط zad-telegram-bot's نسخة entitlement.ts
 *  الخاصة بيها). فشل فتح — لو الـRPC نفسه واقع، عميل دافع ميتقفلش بسبب باج في الفوترة. */
async function consumeVoiceEntitlement(
  // deno-lint-ignore no-explicit-any
  sb: any,
  userId: string,
): Promise<{ allowed: boolean; reason: string }> {
  try {
    const { data, error } = await sb.rpc("zad_entitlement_consume", {
      p_user: userId, p_kind: "voice", p_tz: "UTC",
    });
    if (error) {
      console.error("zad_entitlement_consume (voice) failed, failing open:", error.message);
      return { allowed: true, reason: "gate_unavailable" };
    }
    return data as { allowed: boolean; reason: string };
  } catch (e) {
    console.error("zad_entitlement_consume (voice) threw, failing open:", e);
    return { allowed: true, reason: "gate_unavailable" };
  }
}

Deno.serve(async (req) => {
  if (req.headers.get("upgrade")?.toLowerCase() !== "websocket") {
    return new Response("expected a websocket upgrade request", { status: 400 });
  }

  const userId = await resolveUserId(req);
  if (!userId) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }

  const geminiKey = nextGeminiKey();
  if (!geminiKey) {
    return new Response(JSON.stringify({ error: "voice provider not configured" }), {
      status: 503,
      headers: { "Content-Type": "application/json" },
    });
  }

  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const entitlement = await consumeVoiceEntitlement(sb, userId);
  if (!entitlement.allowed) {
    return new Response(JSON.stringify({ error: "entitlement_denied", reason: entitlement.reason }), {
      status: 402,
      headers: { "Content-Type": "application/json" },
    });
  }

  const { socket: clientSocket, response } = Deno.upgradeWebSocket(req);

  // رسائل العميل اللي وصلت قبل ما اتصال جيميناي يخلص الهاندشيك وياخد setup — بتتراكم
  // هنا وتتبعت بالترتيب أول ما geminiSocket.onopen يشتغل، بدل ما تتفقد.
  let geminiSocket: WebSocket | null = null;
  const pendingFromClient: (string | ArrayBufferLike)[] = [];

  const closeBoth = (code: number, reason: string) => {
    try {
      if (clientSocket.readyState === WebSocket.OPEN || clientSocket.readyState === WebSocket.CONNECTING) {
        clientSocket.close(code, reason);
      }
    } catch (e) {
      console.error("zad-voice-live: closing client socket failed:", e);
    }
    try {
      if (geminiSocket && (geminiSocket.readyState === WebSocket.OPEN || geminiSocket.readyState === WebSocket.CONNECTING)) {
        geminiSocket.close();
      }
    } catch (e) {
      console.error("zad-voice-live: closing gemini socket failed:", e);
    }
  };

  clientSocket.onopen = () => {
    const geminiUrl =
      `wss://${GEMINI_LIVE_HOST}${GEMINI_LIVE_PATH}?key=${encodeURIComponent(geminiKey)}`;
    geminiSocket = new WebSocket(geminiUrl);

    geminiSocket.onopen = () => {
      const setup = {
        setup: {
          model: `models/${VOICE_LIVE_MODEL}`,
          generationConfig: { responseModalities: ["AUDIO"] },
        },
      };
      geminiSocket!.send(JSON.stringify(setup));
      for (const frame of pendingFromClient) geminiSocket!.send(frame);
      pendingFromClient.length = 0;
    };

    geminiSocket.onmessage = (event) => {
      if (clientSocket.readyState === WebSocket.OPEN) clientSocket.send(event.data);
    };
    geminiSocket.onerror = (event) => {
      console.error("zad-voice-live: gemini socket error for user", userId, event);
    };
    geminiSocket.onclose = (event) => {
      closeBoth(event.code === 1000 ? 1000 : 1011, "gemini session ended");
    };
  };

  clientSocket.onmessage = (event) => {
    if (geminiSocket && geminiSocket.readyState === WebSocket.OPEN) {
      geminiSocket.send(event.data);
    } else {
      pendingFromClient.push(event.data);
    }
  };
  clientSocket.onerror = (event) => {
    console.error("zad-voice-live: client socket error for user", userId, event);
  };
  clientSocket.onclose = () => {
    try {
      geminiSocket?.close();
    } catch (e) {
      console.error("zad-voice-live: closing gemini socket on client close failed:", e);
    }
  };

  return response;
});
