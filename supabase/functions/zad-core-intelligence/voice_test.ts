import { assertEquals, assertExists } from "jsr:@std/assert@1";
import { bearerToken, requestElevenLabsVoice, validateVoicePayload } from "./voice.ts";

Deno.test("voice synthesis requires an exact known persona and bounded text", () => {
  assertEquals(validateVoicePayload({ text: "", persona: "sarah_warm" }), null);
  assertEquals(validateVoicePayload({ text: "hello", persona: "invented" }), null);
  assertEquals(validateVoicePayload({ text: "x".repeat(1201), persona: "karim_pro" }), null);
  assertExists(validateVoicePayload({ text: "  أهلاً  ", persona: "sarah_warm" }));
});

Deno.test("bearer parsing never accepts a non-bearer authorization scheme", () => {
  assertEquals(bearerToken(new Request("https://example.test")), "");
  assertEquals(
    bearerToken(new Request("https://example.test", { headers: { Authorization: "Basic abc" } })),
    "",
  );
  assertEquals(
    bearerToken(new Request("https://example.test", { headers: { Authorization: "Bearer user-jwt" } })),
    "user-jwt",
  );
});

Deno.test("ElevenLabs request keeps the key in its header and asks for PCM", async () => {
  let capturedUrl = "";
  let capturedInit: RequestInit | undefined;
  const response = await requestElevenLabsVoice(
    { text: "اختبار", voiceId: "voice-1" },
    "server-secret",
    ((url: string | URL | Request, init?: RequestInit) => {
      capturedUrl = String(url);
      capturedInit = init;
      return Promise.resolve(new Response(new Uint8Array([1, 2, 3]), { status: 200 }));
    }) as typeof fetch,
  );
  assertEquals(response.status, 200);
  assertEquals(capturedUrl.includes("output_format=pcm_24000"), true);
  assertEquals((capturedInit?.headers as Record<string, string>)["xi-api-key"], "server-secret");
  assertEquals(String(capturedInit?.body).includes("server-secret"), false);
});
