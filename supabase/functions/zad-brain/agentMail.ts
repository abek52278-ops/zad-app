// agentMailbox.ts — صندوق بريد الأيدجنتس (Phase 3: cross-agent communication).
//
// الفكرة: قبل كده كل "أيدجنت" كان مجرد توجيه برومبت — مكانش عمال حقيقيين، ومكانش
// فيه أي وسيلة إن وكيل يبعت نتيجة شغله للعقل أو للأيدجنتس التانية. ده بيخلي العقل
// أعمى عن أعمال أيدجنتته: وكيل الصيدلية سجّل جرعة، وكيل المخزون رصد صنف بيخلص —
// العقل مكانش واعي بيهم في الرد الجاي إلا لو صادفهم في الجداول الخام.
//
// الحل: صندوق بريد بسيط (zad_agent_messages):
//  - الأدوات بتكتب سطر "تقرير عمل" بعد كل تنفيذ ناجح (fire-and-forget — فشل التسجيل
//    مش بيكسر التنفيذ نفسه، زي recordSpecialistTrace).
//  - العقل بيقرا آخر تقريرات غير مقروءة قبل بناء برومبت المحادثة، وبعدها بيعلّمهم
//    مقروءين. فبقى واعي بحركة كل أيدجنت في آخر 24 ساعة من غير بحث يدوي.
//
// أمان: مفيش أي أثر على الصلاحيات — دي طبقة وعي مش طبقة صلاحيات (نفس قاعدة soul.ts).
// كل السطور مرتبطة بـ user_id وagent ثابت من allowlist.

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";
import { SpecialistId } from "./specialists.ts";

/** الأيدجنتس المسموح ليهم يكتبوا في الصندوق — نفس SpecialistId في specialists.ts، عدا
 *  "general" (شوف agentSenderFor تحت). zad_agent_messages.sender_check في القاعدة
 *  بيفرض نفس الستة دول بالحرف — لو اتغيّرت هنا لازم تتغيّر هناك كمان. */
export type AgentSender = "finance" | "pantry" | "pharmacy" | "family" | "home" | "brain";

/**
 * SpecialistId → AgentSender. الفرق الوحيد بينهم "general" (مفيش تخصص واضح اتوصّف
 * للرسالة) — مالوش قيمة مقابلة في AgentSender ولا في قيد القاعدة. قبل الدالة دي، نداء
 * sendAgentReport كان بيعمل `specialist as AgentSender` مباشرة: كاست بيسكت الخطأ وقت
 * الكومبايل بدل ما يحله، فأي تنفيذ أداة في محادثة "general" (الأغلبية لأي رسالة ملهاش
 * كلمات مفتاحية تخصص واضح) كان بيفشل يسجّل في الصندوق بصمت (sender_check بيرفض
 * "general"، والرفض بيتبلع جوه try/catch في sendAgentReport) — وده كان السبب الفعلي
 * إن zad_agent_messages فيه صف واحد بس من يوم ما الجدول اتعمل. "general" يترجم لـ
 * "brain" لأنه نفس المعنى: العقل بيتكلم بلا قبعة تخصص، زي المهام المجدولة اللي أصلاً
 * بتستخدم "brain" حرفياً.
 */
export function agentSenderFor(specialist: SpecialistId): AgentSender {
  return specialist === "general" ? "brain" : specialist;
}

export interface AgentMailEntry {
  sender: string;
  subject: string;
  detail: string | null;
  created_at: string;
}

/**
 * سطر تقرير من أيدجنت بعد تنفيذ ناجح. fire-and-forget: اللوج هنا، والفشل مابيرميش.
 * تُستدعى جوه runTool بعد validateTool ناجح — عشان التقرير ييجي من فعل حقيقي اتحقق منه.
 */
export async function sendAgentReport(
  sb: SupabaseClient,
  userId: string,
  sender: AgentSender,
  subject: string,
  detail?: string | null,
): Promise<void> {
  try {
    await sb.from("zad_agent_messages").insert({
      user_id: userId,
      sender,
      subject,
      detail: detail ?? null,
      read_by_brain: false,
    });
  } catch (e) {
    console.error("sendAgentMail failed:", e);
  }
}

/**
 * الرؤية اللي بيتحقن في برومبت المحادثة: آخر تقريرات الأيدجنتس غير المقروءة.
 * empty-safe: مفيش رسايل → "" (نفس نمط skillsBlock/soulBlock).
 */
export function agentMailBlock(
  messages: Array<{ sender: string; subject: string; detail: string | null }> | null,
): string {
  if (!messages || !messages.length) return "";
  const lines = messages.slice(0, 8).map((m) => {
    const who = m.sender;
    const det = m.detail ? ` — ${m.detail}` : "";
    return `- [${who}] ${m.subject}${det}`;
  });
  return "\n=== تقارير الأيدجنتس (شغل اتنفذ من غير ما تسأل) ===\n"
    + lines.join("\n")
    + "\nاستخدم دي في ردك لو ليها علاقة — العميل مش شايف التقارير دي مباشرة، انت صوته.\n"
    + "=== نهاية التقارير ===\n";
}

/**
 * قراءة غير المقروء + تعليمهم مقروءين (سطرين بس — فشلهم مابيكسّرش المحادثة).
 */
export async function fetchUnreadAgentMail(
  sb: SupabaseClient,
  userId: string,
): Promise<Array<{ sender: string; subject: string; detail: string | null }> | null> {
  try {
    const { data, error } = await sb
      .from("zad_agent_messages")
      .select("sender,subject,detail")
      .eq("user_id", userId)
      .eq("read_by_brain", false)
      .order("created_at", { ascending: false })
      .limit(8);
    if (error) {
      console.error("fetchUnreadAgentMail:", error.message);
      return null;
    }
    if (data?.length) {
      // تعليم كمقروء — العقل استلم التقرير. update by user فقط (RLS على المستخدم).
      await sb.from("zad_agent_messages")
        .update({ read_by_brain: true })
        .eq("user_id", userId)
        .eq("read_by_brain", false);
    }
    return data ?? null;
  } catch (e) {
    console.error("fetchUnreadAgentMail threw:", e);
    return null;
  }
}