// voice.ts — توليد الصوت البشري لزاد عبر Gemini TTS (بديل ElevenLabs).
//
// ليه Gemini؟
// - مفتاح GEMINI_API_KEY موجود بالفعل في المشروع (بيستخدم في zad-brain).
// - تحكم بالمشاعر عبر style prompt — ElevenLabs مش بيدعم ده بنفس المرونة.
// - عربي مدعوم رسمياً مع كشف لغة تلقائي.
//
// النموذج: gemini-2.5-flash-preview-tts — الأسرع (منخفض التأخير) ومناسب
// للمحادثة الحية. لو عايز جودة أقصى لمقاطع أطول: gemini-2.5-pro-preview-tts.
//
// الصوت الافتراضي: Aoede — ناعم إيقاعي، الأقرب لصوت أنثوي بشري جميل بالعربي.
// الشخصيات (personas) بتترجم لأصوات + style prompts مختلفة:
// - sarah_warm  → Aoede   (دافئ هادئ)
// - karim_pro   → Charon  (رجالي واضح وواثق)
// - pet_mascot  → Leda    (شبابي مرح)
//
// الإخراج: PCM 24kHz mono 16-bit (نفس فورمات مسار التشغيل في الأندرويد بالظبط).
// المشاعر: النص بيتغلف بتعليمات أسلوب حسب سياق الرسالة (styleForText).

export const GEMINI_TTS_MODEL = Deno.env.get("GEMINI_TTS_MODEL") ?? "gemini-2.5-flash-preview-tts";

export const VOICE_IDS: Record<string, string> = {
  sarah_warm: "Aoede",
  karim_pro: "Charon",
  pet_mascot: "Leda",
};

export interface ValidVoiceRequest {
  text: string;
  voiceId: string;
}

/** تعليمات لهجة اختيارية قادمة من جهاز العميل (مصري/سعودي/...) */
export interface VoiceDialectHint {
  text: string;
  voiceId: string;
  dialect?: string;
}

export function bearerToken(req: Request): string {
  const header = req.headers.get("Authorization") ?? "";
  return header.toLowerCase().startsWith("bearer ")
    ? header.slice(7).trim()
    : "";
}

export function validateVoicePayload(payload: unknown): ValidVoiceRequest | null {
  if (!payload || typeof payload !== "object") return null;
  const row = payload as Record<string, unknown>;
  const text = typeof row.text === "string" ? row.text.trim() : "";
  const persona = typeof row.persona === "string" ? row.persona : "";
  const voiceId = VOICE_IDS[persona];
  if (!text || text.length > 1200 || !voiceId) return null;
  return { text, voiceId };
}

/** استخراج تعليمات اللهجة من الـ payload (اختياري — للتوافق مع الإصدارات القديمة). */
export function extractDialectHint(payload: unknown): string {
  if (!payload || typeof payload !== "object") return "";
  const d = (payload as Record<string, unknown>).dialect_instruction;
  return typeof d === "string" && d.length < 120 ? d.trim() : "";
}

/**
 * تعليمات الأسلوب حسب محتوى النص — دي ميزة Gemini الفعلية (style prompting):
 * بدل نبرة واحدة ثابتة، الصوت بيحس بالمعلومة/التحذير/التهنئة.
 */
function stylePrompt(text: string): string {
  if (/[🚨⚠️]|خطر|انتبه|تجاوزت|فاقد/.test(text)) {
    return "اقرأ بنبرة هادئة لكن جادة ومقلقة قليلاً، بإيقاع أبطأ شوية.";
  }
  if (/[🎉✅⭐🌸]|مبروك|أحسنت|ممتاز|تم/.test(text)) {
    return "اقرأ بنبرة دافئة مبتهجة وواثقة، وكأنك تشاركه فرحة حقيقية.";
  }
  if (/دوا|جرعة|دكتور|صيدلية|حرارة|ضغط|سكري/.test(text)) {
    return "اقرأ بعناية ولطف، بوضوح تام في أسماء الأدوية والمواعيد، دون استعجال.";
  }
  return "اقرأ بصوت أنثوي دافئ طبيعي، هادئ وودود، بمستوى حديث شخصي بين صديقين مقربين.";
}

/**
 * نداء Gemini TTS — يرجع PCM base64 داخل inlineData.
 * الأخطاء ترمي exception والـ caller (index.ts) بيرد 502 بشكل آمن.
 */
export async function requestGeminiVoice(
  input: ValidVoiceRequest,
  apiKey: string,
  fetcher: typeof fetch = fetch,
  dialectInstruction = "",
): Promise<Response> {
  if (!apiKey) throw new Error("GEMINI_API_KEY is not configured");
  const dialectLine = dialectInstruction ? `\n${dialectInstruction}، مع الحفاظ على الطبيعية التامة.` : "";
  // Gemini TTS quirk (googleapis/js-genai#1058): the internal prompt classifier رفض
  // أي نص شكله "طلب نص" وردّ 400 "Model tried to generate text". الحل المعتمد:
  // تعليمة صريحة قبل النص + سطر أمر توليد الصوت بعد النص — كلهم في part واحد.
  const ttsDirective = "اقرأ النص التالي بصوت واضح وطبيعي — ولّد الصوت فقط من دون أي نص مكتوب.";
  const closingDirective = "\n\nالآن ولّد الصوت لهذا النص.";
  const res = await fetcher(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_TTS_MODEL}:generateContent`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [{
          parts: [{ text: `${ttsDirective}${dialectLine}\n\n${stylePrompt(input.text)}\n\n${input.text}${closingDirective}` }],
        }],
        generationConfig: {
          responseModalities: ["AUDIO"],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: { voiceName: input.voiceId },
            },
          },
        },
      }),
    },
  );
  if (!res.ok) return res;

  // استخراج الصوت من الرد وتحويله لـ PCM خام بنفس content-type القديم —
  // الكلاينت (ZadNaturalVoiceEngine) متعود audio/pcm فمايحتاجش أي تغيير.
  const data = await res.json();
  const parts = data?.candidates?.[0]?.content?.parts ?? [];
  const inline = parts.find((p: { inlineData?: { data?: string } }) => p.inlineData?.data);
  if (!inline?.inlineData?.data) {
    console.error("[CoreIntel] Gemini TTS returned no audio:", JSON.stringify(data).slice(0, 400));
    return new Response(JSON.stringify({ error: "no_audio_in_response" }), {
      status: 502,
      headers: { "Content-Type": "application/json" },
    });
  }
  const pcmBytes = Uint8Array.from(atob(inline.inlineData.data), (c) => c.charCodeAt(0));
  return new Response(pcmBytes, {
    status: 200,
    headers: { "Content-Type": "audio/pcm" },
  });
}
