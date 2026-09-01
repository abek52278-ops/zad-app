// specialists_test.ts — اختبارات توجيه الوكلاء المتخصصين.
import { assertEquals } from "jsr:@std/assert@1";
import {
  routeSpecialist,
  routeSpecialists,
  scopeToolsForSpecialist,
  SPECIALISTS,
  specialistPromptBlock,
} from "./specialists.ts";

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

Deno.test("فاتورة/صيانة بتتوجه لوكيل المنزل والدفع", () => {
  assertEquals(routeSpecialist("التكييف محتاج صيانة والضمان قرب يخلص"), "home");
  assertEquals(routeSpecialist("سددت الفاتورة النهاردة"), "home");
  // "دفعت + اسم الخدمة" فيه كلمتين للمال (دفعت/كهربا) فيكسب المال — ده مقصود: سداد فعلي
  // هو شغلانة المال، والمنزل بياخد الصيانة والأعطال والتتبع.
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
  for (const id of ["finance", "pantry", "pharmacy", "family", "home"] as const) {
    const s = SPECIALISTS[id];
    assertEquals(typeof s.nameAr, "string");
    assertEquals(s.nameAr.length > 0, true);
    assertEquals(s.activeLineAr.length > 0, true);
  }
  assertEquals(SPECIALISTS.general.activeLineAr, "");
});

Deno.test("general ملوش بلوك برومبت، والمتخصصين عندهم", () => {
  assertEquals(specialistPromptBlock("general"), null);
  for (const id of ["finance", "pantry", "pharmacy", "family", "home"] as const) {
    const block = specialistPromptBlock(id) ?? "";
    assertEquals(block.includes("الوكيل المتخصص"), true);
    assertEquals(block.includes(SPECIALISTS[id].nameAr), true);
  }
});

// بند 31.6 — أساسي + استشاري تانٍ

Deno.test("رسالة بتمس مطبخ وفلوس مع بعض بترجّع أساسي مطبخ واستشاري مال", () => {
  const r = routeSpecialists("اعمللي عشا واطبخ حاجة، وكمان قوللي هل الميزانية هتسمح ولا لأ");
  assertEquals(r.primary, "pantry");
  assertEquals(r.secondary, "finance");
});

Deno.test("رسالة أحادية النطاق ملهاش استشاري", () => {
  const r = routeSpecialists("سجلي جرعة الدوا الساعة 8 الصبح");
  assertEquals(r.primary, "pharmacy");
  assertEquals(r.secondary, null);
});

Deno.test("general ملوش استشاري", () => {
  const r = routeSpecialists("ازيك عامل ايه");
  assertEquals(r.primary, "general");
  assertEquals(r.secondary, null);
});

Deno.test("بلوك البرومبت بيتضمن سطر الاستشاري لو موجود", () => {
  const block = specialistPromptBlock("pantry", "finance") ?? "";
  assertEquals(block.includes("استشارة إضافية"), true);
  assertEquals(block.includes(SPECIALISTS.finance.nameAr), true);
});

Deno.test("بلوك البرومبت من غير استشاري مفيهوش سطر الاستشارة", () => {
  const block = specialistPromptBlock("pantry") ?? "";
  assertEquals(block.includes("استشارة إضافية"), false);
});

Deno.test("أدوات الاستشاري بتتضاف لأدوات الأساسي (اتحاد مش استبدال)", () => {
  const tools = [
    { name: "add_inventory_item" }, // pantry
    { name: "log_transaction" }, // finance
    { name: "add_pharmacy_item" }, // pharmacy — مفيش في الاتنين
  ];
  const scoped = scopeToolsForSpecialist(tools, "pantry", "finance");
  const names = scoped.map((t) => t.name);
  assertEquals(names.includes("add_inventory_item"), true);
  assertEquals(names.includes("log_transaction"), true);
  assertEquals(names.includes("add_pharmacy_item"), false);
});
