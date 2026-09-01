import { assertEquals, assertMatch } from "jsr:@std/assert@1";
import { buildVoiceSystemInstruction, conversationProfile } from "./persona.ts";

Deno.test("persona follows the account country instead of forcing Egyptian Arabic", () => {
  assertEquals(conversationProfile("EG").locale, "ar-EG");
  assertEquals(conversationProfile("SA").locale, "ar-SA");
  assertEquals(conversationProfile("تركيا").locale, "tr-TR");
  assertMatch(conversationProfile("unknown").instruction, /طابق لغة المستخدم/);
});

Deno.test("voice system instruction never drops the honest-AI-disclosure rule", () => {
  const text = buildVoiceSystemInstruction("EG");
  assertMatch(text, /مساعدة ذكاء اصطناعي/);
  assertMatch(text, /من غير خداع/);
});

Deno.test("voice system instruction asks for speech-sized turns and follows dialect", () => {
  const eg = buildVoiceSystemInstruction("EG");
  assertMatch(eg, /جملك قصيرة/);
  assertMatch(eg, /اللهجة المصرية/);
  const sa = buildVoiceSystemInstruction("SA");
  assertMatch(sa, /سعودية\/خليجية/);
});
