// skills.ts — مهارات متعلمة (نمط Hermes skills) — جدول zad_skills المستقل.
//
// الفكرة: العقل لما ينجح في إجراء مع عميل مرتين+ يتسجل كمهارة، وتتحمّل في برومبت
// المحادثة الجاية. الكتابة عبر remember() بـ scope="skill" لسه مدعومة (ترحيل تلقائي
// في migration 20260824130000 نقل القديم)، والقراءة من هنا من الجدول الجديد مباشرة.
//
// fail-open: فشل الشبكة مش حرج — بيرجع فاضي والعقل يشتغل من غيرها.

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";

export interface LearnedSkill {
  id: string;
  skill_key: string;
  note: string;
  evidence_count: number;
  last_used_at: string | null;
}

/** الحد الأقصى للمهارات المحمّلة في البرومبت — السياق مش مجاني. */
const MAX_SKILLS_IN_PROMPT = 8;

/** حمّل مهارات العميل النشطة مرتبة بالقوة ثم حداثة الاستخدام. */
export async function loadSkills(
  sb: SupabaseClient,
  userId: string,
): Promise<LearnedSkill[]> {
  try {
    const { data, error } = await sb
      .from("zad_skills")
      .select("id, skill_key, note, evidence_count, last_used_at")
      .eq("user_id", userId)
      .is("retired_at", null)
      .order("evidence_count", { ascending: false })
      .order("last_used_at", { ascending: false, nullsFirst: false })
      .limit(MAX_SKILLS_IN_PROMPT);
    if (error) return [];
    return (data ?? []) as LearnedSkill[];
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

/** عدد المهارات النشطة — للتتبع فقط. */
export async function countActiveSkills(sb: SupabaseClient, userId: string): Promise<number> {
  try {
    const { count, error } = await sb
      .from("zad_skills")
      .select("id", { count: "exact", head: true })
      .eq("user_id", userId)
      .is("retired_at", null);
    if (error) return 0;
    return count ?? 0;
  } catch (_e) {
    return 0;
  }
}
