// voice-selftest — فحص صحة مزود الصوت بدون JWT.
//
// مسار تشخيص فقط: بيستدعي Gemini TTS بكلمة واحدة ويرجّع الحالة (ok / سبب الفشل).
// مفيش بيانات مستخدم هنا ولا صوت بيرجع للـ caller — عشان كده verify_jwt=false آمن.
// الفايدة: نقدر نتأكد إن المفتاح شغال من الـ curl مباشرة بدل ما نستنى مستخدم يشتكي.

const GEMINI_TTS_MODEL = Deno.env.get("GEMINI_TTS_MODEL") ?? "gemini-2.5-flash-preview-tts";
// نفس مسبح مفاتيح zad-core-intelligence: ZAD_API_KEY_1..5 ثم GEMINI_API_KEY
const GEMINI_API_KEY =
  Deno.env.get("ZAD_API_KEY_1") ?? Deno.env.get("GEMINI_API_KEY") ?? "";

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Content-Type": "application/json",
    },
  });
}

Deno.serve(async () => {
  if (!GEMINI_API_KEY) return jsonResponse({ ok: false, reason: "no_api_key" }, 503);
  try {
    const res = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_TTS_MODEL}:generateContent`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY },
        body: JSON.stringify({
          contents: [{ parts: [{ text: "اقرأ بصوت واضح: مرحباً بك" }] }],
          generationConfig: {
            responseModalities: ["AUDIO"],
            speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: "Aoede" } } },
          },
        }),
      },
    );
    if (!res.ok) {
      const errText = await res.text();
      console.error(`[voice-selftest] HTTP ${res.status}: ${errText.slice(0, 200)}`);
      return jsonResponse({ ok: false, status: res.status, detail: errText.slice(0, 300) });
    }
    const data = await res.json();
    const audioB64 = data?.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;
    return jsonResponse({
      ok: !!audioB64,
      model: GEMINI_TTS_MODEL,
      audio_bytes: audioB64 ? audioB64.length : 0,
    });
  } catch (e) {
    console.error("[voice-selftest] exception:", e);
    return jsonResponse({ ok: false, reason: "exception" }, 500);
  }
});
