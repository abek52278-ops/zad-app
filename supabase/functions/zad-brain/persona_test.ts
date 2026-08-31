import { assertEquals, assertMatch } from "jsr:@std/assert@1";
import { conversationProfile, voiceModeInstruction } from "./persona.ts";

Deno.test("persona follows the account country instead of forcing Egyptian Arabic", () => {
  assertEquals(conversationProfile("EG").locale, "ar-EG");
  assertEquals(conversationProfile("SA").locale, "ar-SA");
  assertEquals(conversationProfile("تركيا").locale, "tr-TR");
  assertMatch(conversationProfile("unknown").instruction, /طابق لغة المستخدم/);
});

Deno.test("voice mode asks for speech-sized turns", () => {
  assertMatch(voiceModeInstruction(true), /جملك قصيرة/);
  assertMatch(voiceModeInstruction(true), /بنت حرة/);
  assertMatch(voiceModeInstruction(false), /محادثة مكتوبة/);
});
