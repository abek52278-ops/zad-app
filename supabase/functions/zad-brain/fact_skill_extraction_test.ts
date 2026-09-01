import { assertEquals } from "jsr:@std/assert@1";
import { parseFactSkillExtraction } from "./index.ts";

Deno.test("parseFactSkillExtraction بيقرا حقيقة ومهارة سليمتين مع بعض", () => {
  const out = parseFactSkillExtraction(
    "FACT: العميل بقى ياكل نباتي من شهر رمضان\nSKILL: reminder_style|تذكير قصير بالصبح بيرد عليه أسرع من الطويل بالليل",
  );
  assertEquals(out.fact, "العميل بقى ياكل نباتي من شهر رمضان");
  assertEquals(out.skillKey, "reminder_style");
  assertEquals(out.skillNote, "تذكير قصير بالصبح بيرد عليه أسرع من الطويل بالليل");
});

Deno.test("parseFactSkillExtraction بيرفض NONE في السطرين", () => {
  const out = parseFactSkillExtraction("FACT: NONE\nSKILL: NONE");
  assertEquals(out.fact, null);
  assertEquals(out.skillKey, null);
  assertEquals(out.skillNote, null);
});

Deno.test("parseFactSkillExtraction بيرفض مفتاح مهارة مخترع مش من القايمة الثابتة", () => {
  const out = parseFactSkillExtraction("FACT: NONE\nSKILL: made_up_key|وصف طويل بما يكفي عشان يعدي الحد الأدنى");
  assertEquals(out.skillKey, null);
  assertEquals(out.skillNote, null);
});

Deno.test("parseFactSkillExtraction بيرفض حقيقة قصيرة جداً (أقل من 10 حروف)", () => {
  const out = parseFactSkillExtraction("FACT: قصيرة\nSKILL: NONE");
  assertEquals(out.fact, null);
});

Deno.test("parseFactSkillExtraction بيرفض مهارة وصفها أقصر من الحد الأدنى (db check constraint)", () => {
  const out = parseFactSkillExtraction("FACT: NONE\nSKILL: budget_talk|قصير");
  assertEquals(out.skillKey, null);
  assertEquals(out.skillNote, null);
});

Deno.test("parseFactSkillExtraction بيقص الحقيقة عند 200 حرف", () => {
  const longFact = "ح".repeat(250);
  const out = parseFactSkillExtraction(`FACT: ${longFact}\nSKILL: NONE`);
  assertEquals(out.fact?.length, 200);
});

Deno.test("parseFactSkillExtraction بيتعامل مع رد فاضي أو ناقص من غير ما يرمي استثناء", () => {
  assertEquals(parseFactSkillExtraction(""), { fact: null, skillKey: null, skillNote: null });
  assertEquals(parseFactSkillExtraction("كلام عشوائي من غير السطرين المتوقعين"), { fact: null, skillKey: null, skillNote: null });
});
