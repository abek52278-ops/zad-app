import { assertEquals } from "jsr:@std/assert@1";
import { toBrainHistory } from "./index.ts";

/**
 * سياق محادثة تليجرام.
 *
 * تليجرام مالوش تخزين محلي — التطبيق بيقرا من Room، والبوت ماكانش عنده حاجة
 * يقرا منها، فكان بيبعت الرسالة الحالية لوحدها. zad-brain بيدعم body.history من
 * زمان (بيتحقق من الأدوار وبيقص على ٨)، فالناقص كان التخزين والتمرير بس.
 *
 * محادثة حقيقية 2026-09-06 هي اللي كشفت المشكلة:
 *   "هلا اخصم 50 جنيه" → البوت سأل عن الوصف → "مصروف" → البوت سأل عن المبلغ تاني.
 * المبلغ ما ضاعش من ذاكرة العقل — الرسالة اللي فيها عمرها ما اتبعتت.
 */

Deno.test("toBrainHistory بيقلب الترتيب التنازلي لتصاعدي", () => {
  // زي ما بينزل من الاستعلام: الأحدث الأول
  const rows = [
    { role: "assistant", text: "تحب أسجلها بوصف إيه؟" },
    { role: "user", text: "هلا اخصم 50 جنيه" },
  ];
  const history = toBrainHistory(rows);
  assertEquals(history.map((h) => h.text), [
    "هلا اخصم 50 جنيه",
    "تحب أسجلها بوصف إيه؟",
  ]);
});

Deno.test("toBrainHistory بيحافظ على الأدوار", () => {
  const history = toBrainHistory([
    { role: "assistant", text: "رد" },
    { role: "user", text: "سؤال" },
  ]);
  assertEquals(history[0].role, "user");
  assertEquals(history[1].role, "assistant");
});

Deno.test("أي دور غير معروف بيتحول user بدل ما اللفة تختفي", () => {
  // handleAgentTurn بيرمي أي دور مش user/assistant بصمت — التحويل هنا بيمنع
  // إن لفة تتخزن وبعدين تتبخر في الطريق للعقل.
  const history = toBrainHistory([{ role: "system", text: "لفة بدور غريب" }]);
  assertEquals(history.length, 1);
  assertEquals(history[0].role, "user");
});

Deno.test("قايمة فاضية بترجع فاضية — فشل القراءة مايكسرش اللفة", () => {
  assertEquals(toBrainHistory([]), []);
});

/**
 * السيناريو الحقيقي: لما اللفة السابقة توصل، "مصروف" بتبقى إجابة على سؤال
 * ومعاها المبلغ في السياق — مش رسالة يتيمة.
 */
Deno.test("سيناريو «اخصم ٥٠» ← «مصروف»: المبلغ موجود في السياق", () => {
  const rows = [
    { role: "assistant", text: "جاهزة أخصم 50 جنيه. تحب أسجلها بوصف إيه؟" },
    { role: "user", text: "هلا اخصم 50 جنيه" },
  ];
  const history = toBrainHistory(rows);
  const joined = history.map((h) => h.text).join(" ");
  assertEquals(joined.includes("50"), true);
  // والترتيب صح: السؤال بيجي بعد الطلب مش قبله
  assertEquals(history[0].role, "user");
});

/** ما بيعدّلش المصفوفة الأصلية — reverse() في المكان فخ كلاسيكي. */
Deno.test("toBrainHistory مابيغيّرش المدخل", () => {
  const rows = [{ role: "user", text: "أ" }, { role: "assistant", text: "ب" }];
  toBrainHistory(rows);
  assertEquals(rows[0].text, "أ");
});
