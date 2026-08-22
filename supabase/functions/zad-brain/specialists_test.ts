// specialists_test.ts — اختبارات توجيه الوكلاء المتخصصين.
import { assertEquals } from "jsr:@std/assert@1";
import { routeSpecialist, SPECIALISTS, specialistPromptBlock } from "./specialists.ts";

Deno.test("رسالة مصاريف بتروح لوكيل المال", () => {
  assertEquals(routeSpecialist("صرفت ٥٠ جنيه على البقالة"), "finance");
});

Deno.test("سؤال رصيد بيتوجّه للمال", () => {
  assertEquals(routeSpecialist("فاضل معايا كام النهاردة؟"), "finance");
});

Deno.test("طلب وجبة بيتوجّه للمخزون", () => {
  assertEquals(routeSpecialist("اعمللي اقتراح عشا من اللي في الخزنة"), "pantry");
});

Deno.test("جرعة دوا بيتوجّه للصيدلية", () => {
  assertEquals(routeSpecialist("سجلي جرعة الدوا الساعة 8 الصبح"), "pharmacy");
});

Deno.test("مهمة/موعد بيتوجّه للعائلة", () => {
  assertEquals(routeSpecialist("فكرني بموعد دكتور الأسنان بكرة"), "family");
});

Deno.test("كلام عام يفضل general", () => {
  assertEquals(routeSpecialist("ازيك عامل ايه"), "general");
  assertEquals(routeSpecialist("مين انت"), "general");
});

Deno.test("التطبيع: الهمزة والتاء المربوطة مبتغيرش التوجيه", () => {
  assertEquals(routeSpecialist("أنا صرفت فلوس كتير النهاردة"), "finance");
  assertEquals(routeSpecialist("محتاجة أضيف طماطة للقائمة"), "pantry");
});

Deno.test("كل وكيل متخصص له اسم وسطر حالة", () => {
  for (const id of ["finance", "pantry", "pharmacy", "family"] as const) {
    const s = SPECIALISTS[id];
    assertEquals(typeof s.nameAr, "string");
    assertEquals(s.nameAr.length > 0, true);
    assertEquals(s.activeLineAr.length > 0, true);
  }
  assertEquals(SPECIALISTS.general.activeLineAr, "");
});

Deno.test("general ملوش بلوك برومبت، والمتخصصين عندهم", () => {
  assertEquals(specialistPromptBlock("general"), null);
  for (const id of ["finance", "pantry", "pharmacy", "family"] as const) {
    const block = specialistPromptBlock(id) ?? "";
    assertEquals(block.includes("الوكيل المتخصص"), true);
    assertEquals(block.includes(SPECIALISTS[id].nameAr), true);
  }
});
