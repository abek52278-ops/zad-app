// soul_skills_test.ts — اختبارات الهوية (SOUL) والمهارات المتعلمة.
import { assertEquals } from "jsr:@std/assert@1";
import { ZAD_SOUL, soulBlock } from "./soul.ts";
import { skillsBlock, type LearnedSkill } from "./skills.ts";

Deno.test("soulBlock بيرجع كل الفقرات كبلوك واحد", () => {
  const block = soulBlock();
  assertEquals(block.includes("=== SOUL"), true);
  assertEquals(block.includes("=== نهاية الهوية ==="), true);
  for (const para of ZAD_SOUL) {
    assertEquals(block.includes(para.slice(0, 20)), true);
  }
});

Deno.test("SOUL مفيهوش ادعاء إنسانية", () => {
  const joined = ZAD_SOUL.join(" ");
  assertEquals(joined.includes("مشاعر حقيقية") || !joined.includes("لي إنسان"), true);
  // الحدود بتتقال صراحةً في برومبت المحادثة، فالهوية هنا متضيفش عبارات تتعارض معاها.
  assertEquals(/أنا\s*إنسان/.test(joined), false);
});

Deno.test("skillsBlock فاضي لما مفيش مهارات", () => {
  assertEquals(skillsBlock([]), "");
});

Deno.test("skillsBlock بيضمّن المهارات وعدد التكرار", () => {
  const skills: LearnedSkill[] = [
    { id: "s1", note: "بيذكّر بالفاتورة يوم ٥ قبل الاستحقاق بيومين", evidence_count: 3, last_used_at: null },
    { id: "s2", note: "أسلوب الأسئلة القصيرة بيرد أسرع", evidence_count: 2, last_used_at: null },
  ];
  const block = skillsBlock(skills);
  assertEquals(block.includes("مهارات اتعلمتها"), true);
  assertEquals(block.includes("بيذكّر بالفاتورة يوم ٥ قبل الاستحقاق بيومين"), true);
  assertEquals(block.includes("اتأكدت 3 مرة"), true);
});
