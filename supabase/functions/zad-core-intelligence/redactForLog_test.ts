import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { redactForLog } from "./redact.ts";

/**
 * `logged()` بتكتب الـinput بتاع كل نداء AI في `agent_logs`. أكشنين الرؤية
 * (`analyze_receipt_image` و`analyze_inventory_image`) بيبعتوا الصورة كـbase64 جوّه
 * الوسائط — فمن غير حجب، صور المستخدم بالكامل بتتكتب في جدول لوجات. صورة واحدة ممكن
 * تعدّي ميجابايت كنص.
 *
 * الحارس الأساسي هو **الطول** مش اسم الحقل، لأن نقط النداء بتبعت الوسائط كـarray
 * (`{ args: [systemPrompt, userPrompt, image_base64, mimeType] }`) فالمفتاح بيبقى رقم.
 */

const bigImage = "A".repeat(120_000);

Deno.test("صورة base64 جوّه args بتتحجب بالطول", () => {
  const out = redactForLog({
    args: ["أنت مساعد", "حلّل الفاتورة دي", bigImage, "image/jpeg"],
  }) as { args: string[] };

  assertEquals(out.args[0], "أنت مساعد");
  assertEquals(out.args[1], "حلّل الفاتورة دي");
  assertEquals(out.args[2], "[redacted 120000 chars]");
  assertEquals(out.args[3], "image/jpeg");
});

Deno.test("حقل اسمه image_base64 بيتحجب حتى لو قصير", () => {
  const out = redactForLog({ image_base64: "abc", mime_type: "image/png" });
  assertEquals(out, { image_base64: "[redacted]", mime_type: "image/png" });
});

Deno.test("البرومبتات والنتايج العادية بتعدّي زي ما هي", () => {
  const payload = {
    action: "analyze_receipt_image",
    items: [{ name: "لبن", qty: 2, price: 30.5 }],
    ok: true,
    total: null,
  };
  assertEquals(redactForLog(payload), payload);
});

Deno.test("الحجب بينزل جوّه الأوبچكتات والمصفوفات المتداخلة", () => {
  const out = redactForLog({
    batch: [{ payload: { image_base64: bigImage } }, { payload: { note: "تمام" } }],
  }) as { batch: Array<{ payload: Record<string, unknown> }> };

  assertEquals(out.batch[0].payload.image_base64, "[redacted]");
  assertEquals(out.batch[1].payload.note, "تمام");
});

Deno.test("التداخل العميق جدًا بيتقطع بدل ما يلف للأبد", () => {
  // deno-lint-ignore no-explicit-any
  const deep: any = {};
  let cur = deep;
  for (let i = 0; i < 12; i++) {
    cur.next = {};
    cur = cur.next;
  }
  cur.value = "الآخر";

  const out = JSON.stringify(redactForLog(deep));
  assertEquals(out.includes("too deep"), true);
  assertEquals(out.includes("الآخر"), false);
});

Deno.test("القيم البدائية بتعدّي من غير تغيير", () => {
  assertEquals(redactForLog(42), 42);
  assertEquals(redactForLog(null), null);
  assertEquals(redactForLog(undefined), undefined);
  assertEquals(redactForLog(false), false);
});
