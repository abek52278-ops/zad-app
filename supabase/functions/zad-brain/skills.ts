// skills.ts — مهارات متعلمة (نمط Hermes skills، المرحلة C).
//
// الفكرة: العقل لما ينجح في إجراء مع عميل مرتين+ ("أسلوب تذكير الفواتير اللي ردّ
// على أسلوب X") يتسجل كمهارة، وتتحمّل في برومبت المحادثة الجاية. الاستخراج نفسه
// deterministic من zad_memory: ملاحظة scope="skill" + evidence_count >= 2.
//
// الكتابة مش أداة جديدة — بتستخدم remember() الموجود بـ scope="skill". الـ validator
// القديم بيقبله زي أي ملاحظة (10-200 حرف)، والفرق الوحيد إن evidence_count على
// skill بيزيد من التكرار بدل ما ينشئ صفوف مكررة (نفس منطق upsert في runTool).

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";

export interface LearnedSkill {
  id: string;
  note: string;
  evidence_count: number;
  last_used_at: string | null;
}

/** الحد الأقصى للمهارات المحمّلة في البرومبت — السياق مش مجاني. */
const MAX_SKILLS_IN_PROMPT = 8;

/**
 * حمّل مهارات العميل النشطة. فشل الشبكة هنا مش حرج — بيرجع فاضي والعقل يشتغل من غيرها
 * (نفس تحمّس fail-open بتاع buildDriftLessons).
 */
export async function loadSkills(
  sb: SupabaseClient,
  userId: string,
): Promise<LearnedSkill[]> {
  try {
    const { data, error } = await sb
      .from("zad_memory")
      .select("id, note, evidence_count, updated_at")
      .eq("user_id", userId)
      .eq("scope", "skill")
      .order("evidence_count", { ascending: false })
      .limit(MAX_SKILLS_IN_PROMPT);
    if (error) return [];
    return (data ?? []).map((row: any) => ({
      id: row.id,
      note: row.note,
      evidence_count: row.evidence_count ?? 1,
      last_used_at: row.updated_at ?? null,
    }));
  } catch (_e) {
    return [];
  }
}

/** بلوك المهارات للبرومبت. empty-safe زي lessonsBlock بالظبط. */
export function skillsBlock(skills: LearnedSkill[]): string {
  if (!skills.length) return "";
  return "\n=== مهارات اتعلمتها من تعاملك مع البيت ده ===\n"
    + skills.map((s) => `- ${s.note} (اتأكدت ${s.evidence_count} مرة)`).join("\n")
    + "\nاستخدم المهارة لو تناسب الموقف الحالي — متكررش نصاً لو المشكلة مختلفة.\n"
    + "=== نهاية المهارات ===\n";
}

/**
 * استخراج مهارة جديدة من ملاحظات الجري الحالي — deterministic:
 * ملاحظة remember() بـ scope="skill" اتكتبت في الجري ده = ترشيح للتثبيت.
 * التثبيت الفعلي (رفع evidence_count بدل التكرار) شغل runTool/remember الموجود.
 *
 * بترجع عدد المهارات النشطة عشان الـ audit.
 */
export async function countActiveSkills(sb: SupabaseClient, userId: string): Promise<number> {
  try {
    const { count, error } = await sb
      .from("zad_memory")
      .select("id", { count: "exact", head: true })
      .eq("user_id", userId)
      .eq("scope", "skill");
    if (error) return 0;
    return count ?? 0;
  } catch (_e) {
    return 0;
  }
}
