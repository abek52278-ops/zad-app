// audit.ts — الطبقة اللي بتخلي كل كتابة الوكيل بيعملها **مثبتة ومرجوع عنها**.
//
// حاجتين منفصلتين بيتعملوا مع بعض هنا عن قصد:
//
//  1) تثبيت الكتابة (writeRows) — أي INSERT/UPDATE بيرجع الصفوف اللي استقرت فعلاً في
//     الداتابيز، مش مجرد "مفيش error". supabase-js بيرجع {error: null} على UPDATE ما
//     غيّرش ولا صف (معرّف مش موجود، أو RLS منعت الصف) — ولو اعتبرنا ده نجاح، الموديل
//     بيقول للعميل "اتعدلت" وهي ما اتعدلتش. صفر صفوف = فشل صريح.
//
//  2) سجل التدقيق (recordAction) — new_state بيتاخد من **نفس** الصف المقروء في (1)،
//     مش من input بتاع الموديل. ده اللي بيخلي agent_actions سجل لللي حصل فعلاً بدل
//     سجل لللي الموديل نواه، وهو نفس الشرط اللي بيخلي zad_agent_undo() ينفع يرجّع
//     الحالة بدقة.
//
// الاتنين مربوطين لأن الواحد من غير التاني بينهار: تدقيق من غير قراءة-بعد-الكتابة
// بيوثّق نية، وقراءة-بعد-الكتابة من غير تدقيق بتضيع بعد ما الرد يترد.

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";

/**
 * من فين جه الطلب. لازم يطابق CHECK constraint على agent_actions.source بالظبط.
 * القيم دي هي القنوات الحقيقية اللي بتنادي runTool/executeTool دلوقتي — مش قايمة
 * نظرية: app_chat/telegram/confirm من agent_turn/agent_confirm (المحادثة)،
 * daily/event نفس قيم zad_brain_runs.trigger بتاعة الحلقة الخلفية بالظبط، voice من
 * zad-voice-live (بند 33.2 — أدوات مباشرة/مؤكَّدة نادية من جلسة صوتية حية).
 */
export type AgentSource = "app_chat" | "telegram" | "confirm" | "daily" | "event" | "voice";

export interface AuditScope {
  source: AgentSource;
  /** الـ run في zad_brain_runs اللي الفعل ده حصل جواه، لو موجود. */
  runId?: string | null;
}

export interface ActionRecord {
  tool: string;
  input: unknown;
  /** الجدول والصف المستهدف. الاتنين مطلوبين عشان الفعل يبقى قابل للتراجع. */
  table?: string | null;
  targetId?: string | null;
  /** null = العملية كانت INSERT (التراجع = حذف). */
  previous?: unknown;
  /** null = العملية كانت DELETE (التراجع = إعادة إدراج). */
  next?: unknown;
  summary?: string;
}

export type WriteResult<T> = { ok: true; rows: T[] } | { ok: false; reason: string };

type PostgrestLike<T> = PromiseLike<{ data: T[] | null; error: { message: string } | null }>;

/**
 * بيشغّل استعلام كتابة و**بيتحقق إنه غيّر صف فعلاً**.
 *
 * لازم الاستعلام يبقى منتهي بـ `.select(...)` — من غيرها supabase-js بيرجع data=null
 * حتى وهو ناجح، وساعتها مفيش طريقة نفرّق بيها بين "اتكتب" و"ما لقاش الصف".
 */
export async function writeRows<T>(q: PostgrestLike<T>, what: string): Promise<WriteResult<T>> {
  const { data, error } = await q;
  if (error) return { ok: false, reason: `فشل ${what}: ${error.message}` };
  const rows = data ?? [];
  if (rows.length === 0) {
    // مش رسالة تقنية للعميل — دي بترجع للموديل كـ tool_result عشان يعرف إن الكتابة
    // ما حصلتش ويقول كده بدل ما يأكد تنفيذ وهمي.
    return { ok: false, reason: `${what} ما اتنفذش — مفيش صف اتغير (المعرّف مش موجود أو مش بتاع العميل ده)` };
  }
  return { ok: true, rows };
}

/**
 * بيسجّل فعل في agent_actions. **مابيرميش أبداً**: فشل التسجيل ما يصحش يلغي كتابة
 * حصلت خلاص — بيتسجل في اللوج ويكمّل، والنتيجة إن الفعل يبقى غير قابل للتراجع بدل
 * ما العملية كلها تفشل بعد ما البيانات اتغيرت.
 */
export async function recordAction(
  sb: SupabaseClient,
  userId: string,
  scope: AuditScope,
  rec: ActionRecord,
): Promise<void> {
  const { error } = await sb.from("agent_actions").insert({
    user_id: userId,
    run_id: scope.runId ?? null,
    source: scope.source,
    tool_name: rec.tool,
    input: rec.input ?? {},
    target_table: rec.table ?? null,
    target_id: rec.targetId ?? null,
    previous_state: rec.previous ?? null,
    new_state: rec.next ?? null,
    status: "applied",
    result_summary: rec.summary ?? null,
  });
  if (error) console.error("agent_actions insert failed:", error.message, "tool:", rec.tool);
}
