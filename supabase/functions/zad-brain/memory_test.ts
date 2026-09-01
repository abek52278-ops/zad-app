import { assertEquals } from "jsr:@std/assert@1";
import { rankMemoryForMessage } from "./index.ts";

const mem = [
  { id: "1", scope: "food", note: "العميل مش بيكل تونة نهائياً", confidence: 0.5, evidence_count: 1 },
  { id: "2", scope: "finance", note: "الراتب بيدخل أول الشهر", confidence: 0.9, evidence_count: 4 },
  { id: "3", scope: "pharmacy", note: "بياخد كونكور مرتين يومياً", confidence: 0.7, evidence_count: 2 },
];

Deno.test("rankMemoryForMessage يقدّم الملاحظة المرتبطة بالسؤال على أعلى ثقة", () => {
  const ranked = rankMemoryForMessage(mem, "اقترحلي غدا من غير تونة");
  assertEquals(ranked[0].id, "1");
});

Deno.test("rankMemoryForMessage بلا علاقة يرتّب بالثقة كما هي", () => {
  const ranked = rankMemoryForMessage(mem, "عامل ايه عاملة ايه");
  assertEquals(ranked[0].id, "2"); // أعلى confidence + evidence
});

Deno.test("rankMemoryForMessage يحدّ العدد ويحافظ على كل الحقول", () => {
  const many = Array.from({ length: 30 }, (_, i) => ({
    id: String(i), scope: "general", note: `ملاحظة رقم ${i}`, confidence: 0.5, evidence_count: 1,
  }));
  const ranked = rankMemoryForMessage(many, "ملاحظة", 12);
  assertEquals(ranked.length, 12);
  for (const r of ranked) {
    assertEquals(typeof r.note, "string");
    assertEquals(typeof r.confidence, "number");
  }
});

Deno.test("rankMemoryForMessage يتعامل مع ذاكرة فاضية ورسالة كلمات توقف", () => {
  assertEquals(rankMemoryForMessage([], "اي حاجة"), []);
  const out = rankMemoryForMessage(mem, "من في على عن");
  assertEquals(out.length, 3); // مفيش كلمات دالة — يرجع بالترتيب الأصلي/الثقة
});

Deno.test("rankMemoryForMessage (31.4) بيفضّل الملاحظة الأحدث لما كل حاجة تانية متساوية", () => {
  const now = Date.now();
  const tied = [
    { id: "old", scope: "general", note: "نفس الملاحظة", confidence: 0.5, evidence_count: 1,
      last_seen: new Date(now - 60 * 86400000).toISOString() }, // خارج نافذة الـ30 يوم
    { id: "new", scope: "general", note: "نفس الملاحظة", confidence: 0.5, evidence_count: 1,
      last_seen: new Date(now).toISOString() },
  ];
  const ranked = rankMemoryForMessage(tied, "أي رسالة عامة مالهاش عالقة بالكلام");
  assertEquals(ranked[0].id, "new");
});

Deno.test("rankMemoryForMessage (31.4) last_seen غايبة = صفر مكافأة مش استبعاد", () => {
  const noTimestamp = [{ id: "1", scope: "general", note: "ملاحظة", confidence: 0.5, evidence_count: 1 }];
  const ranked = rankMemoryForMessage(noTimestamp, "ملاحظة");
  assertEquals(ranked.length, 1);
  assertEquals(ranked[0].id, "1");
});
