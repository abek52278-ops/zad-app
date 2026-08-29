// اختبارات مسبح مفاتيح TTS: 429 على مفتاح → ينط للمفتاح التالي،
// و404 على موديل → ينط للموديل الاحتياطي بنفس المفتاح. كل النداءات mock fetcher.
import { assertEquals } from "jsr:@std/assert@1";
import { requestGeminiVoiceWithPool } from "./voice.ts";

function audioResponse(): Response {
  return new Response(
    JSON.stringify({
      candidates: [{ content: { parts: [{ inlineData: { data: btoa("pcm-bytes") } }] } }],
    }),
    { status: 200 },
  );
}

Deno.test("TTS pool: 429 على المفتاح الأول ينط للتاني ويرجّع صوت", async () => {
  const calls: Array<{ key: string; model: string }> = [];
  const mockFetcher: typeof fetch = (input, init) => {
    const url = String(input);
    const key = String(((init as RequestInit)?.headers as Record<string, string> | undefined)?.["x-goog-api-key"] ?? "");
    const model = url.split("/models/")[1]?.split(":")[0] ?? "";
    calls.push({ key, model });
    if (key === "KEY1") return Promise.resolve(new Response("rate limited", { status: 429 }));
    return Promise.resolve(audioResponse());
  };
  const res = await requestGeminiVoiceWithPool(
    { text: "مرحبا", voiceId: "Kore" } as any,
    ["KEY1", "KEY2"],
    mockFetcher,
  );
  assertEquals(res.status, 200);
  assertEquals(calls.length, 2);
  assertEquals(calls[0].key, "KEY1");
  assertEquals(calls[1].key, "KEY2");
  // نص المفتاح مايبانش في أي رد
  const bodyText = await res.clone().text();
  assertEquals(bodyText.includes("KEY1") || bodyText.includes("KEY2"), false);
});

Deno.test("TTS pool: 404 على الموديل الأساسي ينط للاحتياطي بنفس المفتاح", async () => {
  const calls: Array<{ key: string; model: string; status: number }> = [];
  const mockFetcher: typeof fetch = (input, init) => {
    const url = String(input);
    const key = String(((init as RequestInit)?.headers as Record<string, string> | undefined)?.["x-goog-api-key"] ?? "");
    const model = url.split("/models/")[1]?.split(":")[0] ?? "";
    calls.push({ key, model, status: 0 });
    if (model === "primary-model") return Promise.resolve(new Response("not found", { status: 404 }));
    return Promise.resolve(audioResponse());
  };
  const res = await requestGeminiVoiceWithPool(
    { text: "مرحبا", voiceId: "Kore" } as any,
    ["KEY1"],
    mockFetcher,
    "",
    ["primary-model", "fallback-model"],
  );
  assertEquals(res.status, 200);
  assertEquals(calls.length, 2);
  assertEquals(calls[0].model, "primary-model");
  assertEquals(calls[1].model, "fallback-model");
  assertEquals(calls[1].key, "KEY1");
});

Deno.test("TTS pool: استنفاد المسبح كله يرجّع 502 بمحاولات مبرهنة بدون مفاتيح", async () => {
  const mockFetcher: typeof fetch = () => Promise.resolve(new Response("down", { status: 500 }));
  const res = await requestGeminiVoiceWithPool(
    { text: "مرحبا", voiceId: "Kore" } as any,
    ["KEY1", "KEY2"],
    mockFetcher,
    "",
    ["m1", "m2"],
  );
  assertEquals(res.status, 502);
  const body = await res.json();
  assertEquals(Array.isArray(body.attempts), true);
  assertEquals(body.attempts.length, 2); // 5xx = مشكلة مفتاح → محاولة واحدة لكل مفتاح
  const serialized = JSON.stringify(body);
  assertEquals(serialized.includes("KEY1") || serialized.includes("KEY2"), false);
});

Deno.test("TTS pool: مفيش مفاتيح أصلاً يرجّع 503 صريح", async () => {
  const res = await requestGeminiVoiceWithPool(
    { text: "مرحبا", voiceId: "Kore" } as any,
    [],
    () => Promise.resolve(audioResponse()),
  );
  assertEquals(res.status, 503);
});
