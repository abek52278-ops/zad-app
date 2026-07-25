// Task 16.1/16.2 — pure validation functions. No network, no DB, no throwing: every
// failure returns a reason string that becomes the tool_result the model reads back.
// Kept in their own module (no Deno.serve here) so they're importable by tests directly.

export type Validation = { ok: true } | { ok: false; reason: string };

export interface RunContext {
  userId: string;
  counts: Record<string, number>;
  mutationCount: number;
  insightCount: number;
  rejections: Array<{ tool: string; reason: string; input: unknown }>;
  mutations: Array<{ tool: string; old: unknown; new: unknown }>;
  abortedTools: Set<string>;
}

export type Validator = (input: any, snap: any, ctx: RunContext) => Promise<Validation> | Validation;

export function freshContext(userId: string): RunContext {
  return { userId, counts: {}, mutationCount: 0, insightCount: 0, rejections: [], mutations: [], abortedTools: new Set() };
}

const DEDUPE_KEY_RE = /^[a-z0-9_]{3,60}$/;
const DIGIT_RE = /[\d٠-٩]/;
const ACCUSATORY_RE = /(لم يأخذ|نسي|أهمل|didn't take|forgot)/i;

export const validateEmitInsight: Validator = (input, snap, ctx) => {
  if (ctx.insightCount >= 3) return { ok: false, reason: "وصلت ٣ رؤى — اختار الأهم وسيب الباقي" };
  if (!input.title || input.title.length > 40) return { ok: false, reason: "العنوان أطول من ٤٠ حرف" };
  if (!input.body || !DIGIT_RE.test(input.body)) return { ok: false, reason: "التنبيه من غير رقم محدد — قول الرقم" };
  if (!DEDUPE_KEY_RE.test(input.dedupe_key ?? "")) return { ok: false, reason: "dedupe_key لازم حروف صغيرة وأرقام و_ فقط" };
  if (ACCUSATORY_RE.test(input.body)) return { ok: false, reason: 'صيغة اتهام — قول "معندناش تسجيل إن..."' };
  if (snap.dismissed_keys?.includes(input.dedupe_key)) return { ok: false, reason: "العميل رفض ده قبل كده" };
  if (input.priority === "critical") {
    const overdueDose = (snap.upcoming ?? []).some((u: any) => u.type === "medication_low");
    const outOfStock = (snap.remaining ?? 0) <= 0;
    if (!overdueDose && !outOfStock) return { ok: false, reason: "مفيش في البيانات حاجة تبرر critical" };
  }
  if (input.surface === "voice" && input.priority !== "critical") {
    return { ok: false, reason: "الصوت للحرج بس" };
  }
  return { ok: true };
};

export const validateAskUser: Validator = (input, snap, ctx) => {
  if (ctx.insightCount >= 3) return { ok: false, reason: "وصلت ٣ رؤى — اختار الأهم وسيب الباقي" };
  if ((ctx.counts["ask_user"] ?? 0) >= 1) return { ok: false, reason: "سؤال واحد في المرة" };
  if (!["number", "yes_no", "camera"].includes(input.answer_type)) {
    return { ok: false, reason: "answer_type لازم يكون number أو yes_no أو camera" };
  }
  if (input.about_item && !snap.stock_unknown?.includes(input.about_item)) {
    return { ok: false, reason: "الصنف ده مش في المخزون أو معدله معروف أصلاً" };
  }
  return { ok: true };
};

export const validateUpdateInventoryQty: Validator = (input, snap) => {
  if (typeof input.new_qty !== "number" || input.new_qty < 0) {
    return { ok: false, reason: "الكمية ماينفعش تكون بالسالب" };
  }
  const item = (snap.stock ?? []).find((s: any) => s.name === input.item_name);
  if (!item) return { ok: false, reason: "المنتج مش موجود في المخزون" };
  if (input.new_qty > Math.max(item.qty * 20, 100)) {
    return { ok: false, reason: "الكمية غير منطقية مقارنة بالموجود" };
  }
  if (!input.reason || input.reason.length < 10) return { ok: false, reason: "اكتب سبب واضح للتعديل" };
  return { ok: true };
};

export const validateSetTransactionCategory: Validator = (input, snap) => {
  if (!input.category || !(snap.distinct_categories ?? []).includes(input.category)) {
    return { ok: false, reason: `التصنيف ده مش موجود عند العميل، استخدم واحد من: ${(snap.distinct_categories ?? []).join(", ")}` };
  }
  return { ok: true };
};

export const validateSuggestBudgetChange: Validator = (input, snap, ctx) => {
  if ((ctx.counts["suggest_budget_change"] ?? 0) >= 1) return { ok: false, reason: "اقتراح ميزانية واحد بس في المرة" };
  if (typeof input.new_budget !== "number" || input.new_budget <= 0) {
    return { ok: false, reason: "الميزانية الجديدة لازم رقم موجب" };
  }
  const budget = snap.budget ?? 0;
  if (budget > 0 && (input.new_budget < budget * 0.5 || input.new_budget > budget * 2)) {
    return { ok: false, reason: "الرقم بعيد جداً عن ميزانية العميل الحالية" };
  }
  return { ok: true };
};

export const validateAddShoppingItem: Validator = (input, snap, ctx) => {
  if ((ctx.counts["add_shopping_item"] ?? 0) >= 5) return { ok: false, reason: "وصلت لحد أقصى ٥ أصناف في المرة" };
  if (typeof input.quantity !== "number" || input.quantity <= 0 || input.quantity > 100) {
    return { ok: false, reason: "الكمية لازم تكون بين ١ و١٠٠" };
  }
  if ((snap.shopping_list_pending ?? []).includes(input.item_name)) {
    return { ok: false, reason: "الحاجة دي على القايمة أصلاً" };
  }
  return { ok: true };
};

export const validateRemember: Validator = (input, snap, ctx) => {
  if ((ctx.counts["remember"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ ملاحظات في المرة" };
  const len = (input.note ?? "").length;
  if (len < 10) return { ok: false, reason: "الملاحظة قصيرة أوي" };
  if (len > 200) return { ok: false, reason: "طويلة أوي، لخّصها في جملة" };
  return { ok: true };
};

export const VALIDATORS: Record<string, Validator> = {
  emit_insight: validateEmitInsight,
  ask_user: validateAskUser,
  update_inventory_qty: validateUpdateInventoryQty,
  set_transaction_category: validateSetTransactionCategory,
  suggest_budget_change: validateSuggestBudgetChange,
  add_shopping_item: validateAddShoppingItem,
  remember: validateRemember,
  merge_duplicate_expense: () => ({ ok: true }),
};

/** بوابة الفحص العامة — الحدود المشتركة (mutation cap, 3-strikes abort) قبل ما توصل للـ validator المتخصص */
export async function validateTool(name: string, input: any, snap: any, ctx: RunContext): Promise<Validation> {
  if (ctx.abortedTools.has(name)) {
    return { ok: false, reason: "الأداة دي اتوقفت الجلسة دي بعد ٣ محاولات فاشلة" };
  }
  if (ctx.mutationCount >= 5 && ["update_inventory_qty", "set_transaction_category", "merge_duplicate_expense"].includes(name)) {
    return { ok: false, reason: "وصلت الحد الأقصى للتعديلات في الجلسة دي" };
  }
  const validator = VALIDATORS[name];
  const v = validator ? await validator(input, snap, ctx) : { ok: true as const };
  if (!v.ok) {
    ctx.rejections.push({ tool: name, reason: v.reason, input });
    const failCount = ctx.rejections.filter((r) => r.tool === name).length;
    if (failCount >= 3) ctx.abortedTools.add(name);
  }
  return v;
}
