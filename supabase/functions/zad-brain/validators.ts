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
  /** Task 18: rate-learning artifacts produced this run (observation + recomputed rate). */
  observations: Array<{ item: string; qty: number; samples: number; rateKnown: boolean }>;
}

export type Validator = (input: any, snap: any, ctx: RunContext) => Promise<Validation> | Validation;

export function freshContext(userId: string): RunContext {
  return { userId, counts: {}, mutationCount: 0, insightCount: 0, rejections: [], mutations: [], abortedTools: new Set(), observations: [] };
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
    // Task 26 — available (بعد خصم الالتزامات الثابتة) مش remaining، عشان "المتاح صفر أو
    // سالب" هو التهديد الحقيقي حتى لو remaining لسه موجب. snap.available قد يبقى
    // undefined في سنابشوت قديم/اختبار — يرجع remaining كـ fallback فقط في الحالة دي.
    const outOfMoney = (snap.available ?? snap.remaining ?? 0) <= 0;
    if (!overdueDose && !outOfMoney) return { ok: false, reason: "مفيش في البيانات حاجة تبرر critical" };
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
  // Task 18 cooldown. Without this, Fault B survives the rate logic: an item needs four
  // observations before samples>=3, so it stays in stock_unknown for days, and the brain
  // would re-ask every single daily run in the meantime — which reads to the user exactly
  // like "the app asks and forgets". Checked BEFORE the stock_unknown test so the model gets
  // the specific reason (and can learn the rule) instead of a generic one.
  if (input.about_item && (snap.rate_known_items ?? []).includes(input.about_item)) {
    return { ok: false, reason: "المعدل معروف بالفعل" };
  }
  if (input.about_item && (snap.asked_recently ?? []).includes(input.about_item)) {
    return { ok: false, reason: "سألت عن ده قبل ٣ أيام — استنى" };
  }
  if (input.about_item && !snap.stock_unknown?.includes(input.about_item)) {
    return { ok: false, reason: "الصنف ده مش في المخزون أو معدله معروف أصلاً" };
  }
  // Task 19.5 — تسوية الكاش الأسبوعية. key محسوب في buildSnapshot (isoWeekKey)، مش من
  // الموديل، عشان مفيش مفتاح مخترع يفلت من عدّاد الرفض. رفضين اتنين = وقف نهائي.
  if ((input.dedupe_key ?? "").startsWith("cash_reconciliation_")) {
    if (input.dedupe_key !== snap.cash_reconciliation?.key) {
      return { ok: false, reason: "استخدم cash_reconciliation.key من الـ snapshot بالظبط، متخترعش مفتاح تاني" };
    }
    if ((snap.cash_reconciliation?.dismissed_count ?? 0) >= 2) {
      return { ok: false, reason: "العميل رفض سؤال تسوية الكاش مرتين قبل كده — متسألش تاني، اعتمد على السحب بس، وسجّلها بـ remember() لو لسه ما سجلتهاش" };
    }
    if (snap.cash_reconciliation?.needs_ask === false) {
      return { ok: false, reason: "سؤال الأسبوع ده اتسأل بالفعل" };
    }
  }
  // Task 25 — نفس مبدأ cash_reconciliation فوق: dedupe_key محسوب في buildSnapshot، مش
  // من الموديل، عشان مفيش مفتاح مخترع أو يوم مقترح مختلف عن اللي فعلاً في الـ snapshot.
  if ((input.dedupe_key ?? "").startsWith("cycle_start_confirm_")) {
    if (input.dedupe_key !== snap.cycle_detection?.dedupe_key) {
      return { ok: false, reason: "استخدم cycle_detection.dedupe_key من الـ snapshot بالظبط، متخترعش مفتاح تاني" };
    }
    if (snap.cycle_detection?.needs_ask === false) {
      return { ok: false, reason: "السؤال ده اتسأل بالفعل أو دورة الراتب متسجلة أصلاً" };
    }
  }
  // Task 26 — نفس المبدأ: dedupe_key محسوب في buildSnapshot (hashKey على تاجر+مبلغ)، مش
  // من الموديل.
  if ((input.dedupe_key ?? "").startsWith("obligation_confirm_")) {
    if (input.dedupe_key !== snap.obligation_detection?.dedupe_key) {
      return { ok: false, reason: "استخدم obligation_detection.dedupe_key من الـ snapshot بالظبط، متخترعش مفتاح تاني" };
    }
    if (snap.obligation_detection?.needs_ask === false) {
      return { ok: false, reason: "السؤال ده اتسأل بالفعل أو الالتزام ده متسجل أصلاً" };
    }
  }
  return { ok: true };
};

export const validateConfirmCycleStart: Validator = (input, snap, ctx) => {
  if ((ctx.counts["confirm_cycle_start"] ?? 0) >= 1) return { ok: false, reason: "تأكيد واحد بس في المرة" };
  if (typeof input.cycle_start_day !== "number" || input.cycle_start_day < 1 || input.cycle_start_day > 31) {
    return { ok: false, reason: "cycle_start_day لازم يكون بين ١ و٣١" };
  }
  if (input.cycle_start_day !== snap.cycle_detection?.suggested_day) {
    return { ok: false, reason: "استخدم cycle_detection.suggested_day من الـ snapshot بالظبط، متخترعش رقم تاني" };
  }
  return { ok: true };
};

const OBLIGATION_KINDS = ["rent", "installment", "debt", "tuition", "utility", "other"];

export const validateConfirmObligation: Validator = (input, snap, ctx) => {
  if ((ctx.counts["confirm_obligation"] ?? 0) >= 1) return { ok: false, reason: "تأكيد التزام واحد بس في المرة" };
  if (!OBLIGATION_KINDS.includes(input.kind)) {
    return { ok: false, reason: `kind لازم يكون واحد من: ${OBLIGATION_KINDS.join(", ")}` };
  }
  if (!snap.obligation_detection?.title || snap.obligation_detection?.needs_ask === false) {
    return { ok: false, reason: "مفيش التزام مكتشف محتاج تأكيد دلوقتي في الـ snapshot" };
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
  if (input.confidence !== undefined && (typeof input.confidence !== "number" || input.confidence < 0 || input.confidence > 1)) {
    return { ok: false, reason: "confidence لازم يكون رقم بين 0 و 1، مش كلمة زي \"medium\"" };
  }
  return { ok: true };
};

const LINK_RELATIONS = ["leads_to", "co_occurs", "explains", "contradicts"];

/**
 * الفحص الأساسي هنا إن الـid الاتنين **موجودين في نفس الـsnapshot** اللي الموديل شايفه.
 * الدالة في قاعدة البيانات بتتحقق من الملكية وبترفض أي id مش بتاع العميل، لكن الرفض ده
 * بيرجع كخطأ SQL بعد نداء شبكة. الفحص هنا بيمسك الحالة الأشهر — الموديل بيخترع id —
 * قبل ما توصل القاعدة أصلاً، وبيديله سبب مفهوم يقدر يصحح بناءً عليه.
 */
export const validateLinkMemory: Validator = (input, snap, ctx) => {
  if ((ctx.counts["link_memory"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ روابط في المرة" };
  if (!LINK_RELATIONS.includes(input.relation)) {
    return { ok: false, reason: `relation لازم يكون واحد من: ${LINK_RELATIONS.join(", ")}` };
  }
  if (!input.from_id || !input.to_id) return { ok: false, reason: "محتاج from_id و to_id" };
  if (input.from_id === input.to_id) return { ok: false, reason: "مينفعش تربط ملاحظة بنفسها" };
  if (input.strength !== undefined && (typeof input.strength !== "number" || input.strength < 0 || input.strength > 1)) {
    return { ok: false, reason: "strength لازم يكون رقم بين 0 و 1" };
  }
  const known = new Set((snap.memory ?? []).map((m: any) => m.id));
  if (!known.has(input.from_id)) return { ok: false, reason: "from_id مش موجود في memory — استخدم id زي ما هو من القايمة" };
  if (!known.has(input.to_id)) return { ok: false, reason: "to_id مش موجود في memory — استخدم id زي ما هو من القايمة" };
  return { ok: true };
};

export const validateReconcileCashBalance: Validator = (input, snap, ctx) => {
  if ((ctx.counts["reconcile_cash_balance"] ?? 0) >= 1) return { ok: false, reason: "تصحيح واحد بس في المرة" };
  if (typeof input.reported_amount !== "number" || !Number.isFinite(input.reported_amount) || input.reported_amount < 0) {
    return { ok: false, reason: "reported_amount لازم يكون رقم موجب" };
  }
  return { ok: true };
};

// ═══════════════════════════════════════════════════════════
// المرحلة ٢-ب — أدوات المحادثة (agent_turn). الأدوات فوق دي أدوات تحليل خلفي؛ دي
// الأدوات اللي المستخدم بيطلبها بصوته أو بكتابته.
//
// التلاتة الأولانية (log_transaction/update_transaction/set_monthly_limit) بيكتبوا على
// فلوس حقيقية، فمابينفذوش من الحلقة أبداً — بيتحوّلوا لاقتراح ينتظر تأكيد صريح. التحقق
// هنا بيحصل **قبل** ما الاقتراح يتعرض على العميل، عشان اقتراح فيه رقم مستحيل مايوصلوش
// أصلاً، وبيحصل تاني عند التأكيد.
// ═══════════════════════════════════════════════════════════

/** أقصى مبلغ في معاملة واحدة من محادثة — رقم شارد من الموديل أو سوء فهم لجملة. */
const MAX_TRANSACTION_AMOUNT = 1_000_000;

export const validateLogTransaction: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["log_transaction"] ?? 0) >= 5) return { ok: false, reason: "وصلت لحد أقصى ٥ معاملات في المرة" };
  const amount = input.amount;
  if (typeof amount !== "number" || !Number.isFinite(amount) || amount <= 0) {
    return { ok: false, reason: "المبلغ لازم يكون رقم موجب" };
  }
  if (amount > MAX_TRANSACTION_AMOUNT) return { ok: false, reason: "المبلغ ده كبير بشكل غير منطقي — تأكد منه" };
  if (!["expense", "income"].includes(input.txn_kind)) {
    return { ok: false, reason: "txn_kind لازم يكون expense أو income" };
  }
  if (!input.title || String(input.title).trim().length === 0) {
    return { ok: false, reason: "اكتب وصف قصير للمعاملة" };
  }
  return { ok: true };
};

export const validateUpdateTransaction: Validator = (input, snap, ctx) => {
  if ((ctx.counts["update_transaction"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ تعديلات في المرة" };
  if (!input.transaction_id) return { ok: false, reason: "محتاج transaction_id من قائمة المعاملات" };
  // نفس حارس set_transaction_category: المعاملة لازم تكون في سنابشوت العميل ده، عشان
  // معرّف مخترع (أو بتاع عميل تاني) يترفض قبل ما يوصل الداتابيز أصلاً.
  const known = (snap.recent_transaction_ids ?? []);
  if (known.length > 0 && !known.includes(input.transaction_id)) {
    return { ok: false, reason: "المعاملة دي مش في معاملات العميل الأخيرة" };
  }
  if (input.amount !== undefined) {
    if (typeof input.amount !== "number" || !Number.isFinite(input.amount) || input.amount <= 0) {
      return { ok: false, reason: "المبلغ لازم يكون رقم موجب" };
    }
    if (input.amount > MAX_TRANSACTION_AMOUNT) return { ok: false, reason: "المبلغ ده كبير بشكل غير منطقي" };
  }
  if (input.txn_kind !== undefined && !["expense", "income"].includes(input.txn_kind)) {
    return { ok: false, reason: "txn_kind لازم يكون expense أو income" };
  }
  if (input.amount === undefined && input.title === undefined && input.category === undefined && input.txn_kind === undefined) {
    return { ok: false, reason: "مفيش حاجة تتعدل — حدد المبلغ أو الوصف أو الفئة أو النوع" };
  }
  return { ok: true };
};

export const validateDeleteTransaction: Validator = (input, snap, ctx) => {
  if ((ctx.counts["delete_transaction"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف معاملات في المرة" };
  if (!input.transaction_id) return { ok: false, reason: "محتاج transaction_id من قائمة المعاملات" };
  const known = (snap.recent_transaction_ids ?? []);
  if (known.length > 0 && !known.includes(input.transaction_id)) {
    return { ok: false, reason: "المعاملة دي مش في معاملات العميل الأخيرة" };
  }
  return { ok: true };
};

export const validateSetMonthlyLimit: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["set_monthly_limit"] ?? 0) >= 1) return { ok: false, reason: "تعديل سقف واحد بس في المرة" };
  if (typeof input.monthly_limit !== "number" || !Number.isFinite(input.monthly_limit) || input.monthly_limit <= 0) {
    return { ok: false, reason: "السقف لازم يكون رقم موجب" };
  }
  if (input.monthly_limit > 100_000_000) return { ok: false, reason: "الرقم ده غير منطقي كسقف شهري" };
  return { ok: true };
};

/**
 * إضافة صنف **جديد** للمخزون. مقصود إنها منفصلة عن [validateUpdateInventoryQty]:
 * دي بترفض لو الصنف موجود بالفعل (التعديل شغلانة الأداة التانية)، والتانية بترفض لو
 * الصنف مش موجود. الفصل ده هو اللي بيمنع الموديل إنه "يضيف" صنف قايم فيدهس كميته.
 */
const INVENTORY_CATEGORIES = ["البقالة", "الخضار", "الفواكه", "اللحوم", "الألبان", "المشروبات", "العناية", "أخرى"];

export const validateAddInventoryItem: Validator = (input, snap, ctx) => {
  if ((ctx.counts["add_inventory_item"] ?? 0) >= 10) return { ok: false, reason: "وصلت لحد أقصى ١٠ أصناف في المرة" };
  const name = String(input.item_name ?? "").trim();
  if (name.length < 2) return { ok: false, reason: "اسم الصنف قصير أوي" };
  if (typeof input.quantity !== "number" || input.quantity <= 0 || input.quantity > 999) {
    return { ok: false, reason: "الكمية لازم تكون بين ١ و٩٩٩" };
  }
  // كان مفيش أي تحقق هنا — الموديل بيسيب category فاضية أو يخترع كلمة (زي "عام")
  // ما بتطابقش تابات المخزون، فالصنف يظهر في "أخرى" بس بدل تابه الصح (الألبان،
  // الخضار...). نفس مبدأ validateSetTransactionCategory: لازم يكون من قايمة معروفة.
  if (!INVENTORY_CATEGORIES.includes(String(input.category ?? ""))) {
    return { ok: false, reason: `الفئة لازم تكون واحدة من: ${INVENTORY_CATEGORIES.join("، ")}` };
  }
  const exists = (snap.stock ?? []).some((s: any) => s.name === name);
  if (exists) return { ok: false, reason: "الصنف موجود بالفعل — استخدم update_inventory_qty عشان تعدّل كميته" };
  return { ok: true };
};

/** حذف المخزون مقصود للحالات التي لا يريد فيها العميل تتبع الصنف إطلاقاً؛ النفاد
 * العادي يستخدم update_inventory_qty إلى صفر حتى يظل التعلم وسجل الاستهلاك موجودين. */
export const validateDeleteInventoryItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_inventory_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف أصناف في المرة" };
  if (String(input.item_name ?? "").trim().length < 2) return { ok: false, reason: "اسم الصنف قصير أوي" };
  return { ok: true };
};

const PHARMACY_UNITS = [
  "قرص", "أقراص", "حبة", "حبات", "حبوب", "كبسولة", "كبسولات",
  "مل", "كريم", "بخاخ", "نقطة", "قطرة", "كيس", "أكياس", "أمبول", "أمبولات", "علبة"
];
const DOSE_TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

/**
 * تطبيع أوقات الجرعات: الأرقام العربية الهندية (٠٣:٤٩) بتتحول لأرقام ASCII،
 * والفواصل العربية (،) والشرطات بتتحول لفاصلة. الفحص الحي (2026-08-22) لقى دواء
 * اتسجل بساعة "٠٣:٤٩" عدّت التخزين لكن الـ AlarmManager عمره ما هيفهمها.
 */
export function normalizeDoseTimes(raw: string): string {
  return raw
    .replace(/[٠-٩]/g, (d) => String("٠١٢٣٤٥٦٧٨٩".indexOf(d)))
    .replace(/[۰-۹]/g, (d) => String("۰۱۲۳۴۵۶۷۸۹".indexOf(d)))
    .replace(/،/g, ",")
    .replace(/\s*-\s*/g, ",")
    .split(",")
    .map((part) => {
      const t = part.trim();
      // أكمل الصفر البادئ للساعة فقط: "9:15" → "09:15" (مش بنلمس الدقايق)
      const m = /^(\d{1,2}):(\d{1,2})$/.exec(t);
      return m ? `${m[1].padStart(2, "0")}:${m[2].padStart(2, "0")}` : t;
    })
    .join(",");
}

export const validateAddPharmacyItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["add_pharmacy_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ أدوية في المرة" };
  const name = String(input.name ?? "").trim();
  if (name.length < 2) return { ok: false, reason: "اسم الدواء قصير أوي" };
  if (input.unit !== undefined && !PHARMACY_UNITS.includes(input.unit)) {
    return { ok: false, reason: `الوحدة لازم تكون واحدة من: ${PHARMACY_UNITS.join("، ")}` };
  }
  if (input.dose_times !== undefined && input.dose_times !== null && String(input.dose_times).length > 0) {
    // طبّع الأول (أرقام عربية، فواصل عربية/شرطات) ثم تحقق — بدل رفض صامت
    input.dose_times = normalizeDoseTimes(String(input.dose_times));
    const times = String(input.dose_times).split(",").map((t: string) => t.trim());
    if (!times.every((t: string) => DOSE_TIME_RE.test(t))) {
      return { ok: false, reason: "المواعيد لازم تكون بصيغة HH:MM بنظام ٢٤ ساعة، مفصولة بفاصلة (ممنوع 24:00)" };
    }
    // جدول جرعات بيفتح منبهات متكررة فعلية — عدد المواعيد لازم يطابق العدد المعلن،
    // وإلا المستخدم بيتقاله "٣ مرات" ويوصله منبهين.
    if (input.daily_dose_count !== undefined && input.daily_dose_count !== times.length) {
      return { ok: false, reason: "daily_dose_count لازم يساوي عدد المواعيد في dose_times" };
    }
  }
  if (input.quantity !== undefined && (typeof input.quantity !== "number" || input.quantity < 0 || input.quantity > 9999)) {
    return { ok: false, reason: "الكمية لازم تكون بين ٠ و٩٩٩٩" };
  }
  return { ok: true };
};

// كود بلد ISO 3166-1 alpha-2 وكود عملة ISO 4217 — الاتنين حرفين/تلاتة كابيتال بالظبط.
const COUNTRY_RE = /^[A-Z]{2}$/;
const CURRENCY_RE = /^[A-Z]{3}$/;

export const validateSetMarket: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["set_market"] ?? 0) >= 1) return { ok: false, reason: "تحديد سوق واحد بس في المرة" };
  if (!CURRENCY_RE.test(String(input.currency ?? ""))) {
    return { ok: false, reason: "العملة لازم كود ISO من ٣ حروف كابيتال (مثل EGP أو SAR)" };
  }
  if (!COUNTRY_RE.test(String(input.country ?? ""))) {
    return { ok: false, reason: "البلد لازم كود ISO من حرفين كابيتال (مثل EG أو SA)" };
  }
  return { ok: true };
};

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export const validateAddSubscription: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["add_subscription"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ اشتراكات في المرة" };
  if (String(input.title ?? "").trim().length < 2) return { ok: false, reason: "اسم الاشتراك قصير أوي" };
  if (typeof input.amount !== "number" || !Number.isFinite(input.amount) || input.amount <= 0) {
    return { ok: false, reason: "المبلغ لازم يكون رقم موجب" };
  }
  if (input.renewal_date !== undefined && input.renewal_date !== null && !DATE_RE.test(String(input.renewal_date))) {
    return { ok: false, reason: "تاريخ التجديد لازم بصيغة YYYY-MM-DD" };
  }
  if (input.billing_cycle !== undefined && !["MONTHLY", "YEARLY"].includes(input.billing_cycle)) {
    return { ok: false, reason: "billing_cycle لازم MONTHLY أو YEARLY" };
  }
  return { ok: true };
};

export const validateUpdateSubscription: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["update_subscription"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ تعديلات اشتراك في المرة" };
  if (String(input.title ?? "").trim().length < 2) return { ok: false, reason: "اسم الاشتراك مطلوب" };
  if (input.new_amount !== undefined && (typeof input.new_amount !== "number" || input.new_amount <= 0)) {
    return { ok: false, reason: "المبلغ الجديد لازم يكون رقم موجب" };
  }
  if (input.new_renewal_date !== undefined && !DATE_RE.test(String(input.new_renewal_date))) {
    return { ok: false, reason: "تاريخ التجديد لازم بصيغة YYYY-MM-DD" };
  }
  return { ok: true };
};

export const validateDeleteSubscription: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_subscription"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف اشتراكات في المرة" };
  if (String(input.title ?? "").trim().length < 2) return { ok: false, reason: "اسم الاشتراك قصير أوي" };
  return { ok: true };
};

export const validateAddDebt: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["add_debt"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ ديون في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الدين قصير أوي" };
  if (typeof input.remaining_balance !== "number" || !Number.isFinite(input.remaining_balance) || input.remaining_balance <= 0) {
    return { ok: false, reason: "الرصيد المتبقي لازم يكون رقم موجب" };
  }
  if (input.minimum_payment !== undefined && (typeof input.minimum_payment !== "number" || input.minimum_payment < 0)) {
    return { ok: false, reason: "الحد الأدنى الشهري ماينفعش سالب" };
  }
  if (input.due_day !== undefined && input.due_day !== null && (typeof input.due_day !== "number" || input.due_day < 1 || input.due_day > 31)) {
    return { ok: false, reason: "يوم الاستحقاق لازم بين ١ و٣١" };
  }
  return { ok: true };
};

export const validateUpdateDebt: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["update_debt"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ تعديلات دين في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الدين مطلوب" };
  if (input.new_remaining_balance !== undefined && (typeof input.new_remaining_balance !== "number" || input.new_remaining_balance < 0)) {
    return { ok: false, reason: "الرصيد المتبقي الجديد ماينفعش سالب" };
  }
  if (input.new_minimum_payment !== undefined && (typeof input.new_minimum_payment !== "number" || input.new_minimum_payment < 0)) {
    return { ok: false, reason: "الحد الأدنى الشهري الجديد ماينفعش سالب" };
  }
  return { ok: true };
};

export const validateDeleteDebt: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_debt"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف ديون في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الدين قصير أوي" };
  return { ok: true };
};

// إيجار/قسط ثابت/فاتورة/مصاريف دراسية — كان معندهاش أداة إضافة مباشرة خالص، الطريقة
// الوحيدة كانت الاكتشاف التلقائي (٣ شهور من نفس المبلغ). العميل كان يقول "عندي إيجار
// ٣٠٠٠" في الشات ومفيش أداة تسجّله فورًا.
const OBLIGATION_ADD_KINDS = ["rent", "installment", "tuition", "utility", "other"];

export const validateAddObligation: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["add_obligation"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ التزامات في المرة" };
  if (String(input.title ?? "").trim().length < 2) return { ok: false, reason: "اسم الالتزام قصير أوي" };
  if (typeof input.amount !== "number" || !Number.isFinite(input.amount) || input.amount <= 0) {
    return { ok: false, reason: "المبلغ لازم يكون رقم موجب" };
  }
  if (!OBLIGATION_ADD_KINDS.includes(String(input.kind ?? ""))) {
    return { ok: false, reason: `kind لازم يكون واحد من: ${OBLIGATION_ADD_KINDS.join("، ")} — لو دين برصيد بينقص استخدم add_debt بدلها` };
  }
  if (input.recurrence !== undefined && !["monthly", "quarterly", "yearly"].includes(input.recurrence)) {
    return { ok: false, reason: "recurrence لازم يكون monthly أو quarterly أو yearly" };
  }
  if (input.due_day !== undefined && input.due_day !== null && (typeof input.due_day !== "number" || input.due_day < 1 || input.due_day > 31)) {
    return { ok: false, reason: "يوم الاستحقاق لازم بين ١ و٣١" };
  }
  return { ok: true };
};

export const validateUpdateObligation: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["update_obligation"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ تعديلات التزام في المرة" };
  if (String(input.title ?? "").trim().length < 2) return { ok: false, reason: "اسم الالتزام مطلوب" };
  if (input.new_amount !== undefined && (typeof input.new_amount !== "number" || input.new_amount <= 0)) {
    return { ok: false, reason: "المبلغ الجديد لازم يكون رقم موجب" };
  }
  if (input.new_due_day !== undefined && (typeof input.new_due_day !== "number" || input.new_due_day < 1 || input.new_due_day > 31)) {
    return { ok: false, reason: "يوم الاستحقاق الجديد لازم بين ١ و٣١" };
  }
  if (input.new_amount === undefined && input.new_due_day === undefined) {
    return { ok: false, reason: "مفيش حاجة تتعدل — حدد المبلغ أو يوم الاستحقاق" };
  }
  return { ok: true };
};

export const validateDeleteObligation: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_obligation"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف التزامات في المرة" };
  if (String(input.title ?? "").trim().length < 2) return { ok: false, reason: "اسم الالتزام قصير أوي" };
  return { ok: true };
};

export const validateAddMaintenanceItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["add_maintenance_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ أجهزة في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الجهاز قصير أوي" };
  if (input.warranty_expiry_date !== undefined && input.warranty_expiry_date !== null && !DATE_RE.test(String(input.warranty_expiry_date))) {
    return { ok: false, reason: "تاريخ انتهاء الضمان لازم بصيغة YYYY-MM-DD" };
  }
  if (input.service_interval_days !== undefined && input.service_interval_days !== null && (typeof input.service_interval_days !== "number" || input.service_interval_days <= 0)) {
    return { ok: false, reason: "الفاصل بين الصيانات لازم رقم موجب" };
  }
  return { ok: true };
};

export const validateUpdateMaintenanceItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["update_maintenance_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ تعديلات أجهزة في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الجهاز مطلوب" };
  if (input.last_service_date !== undefined && !DATE_RE.test(String(input.last_service_date))) {
    return { ok: false, reason: "تاريخ آخر صيانة لازم بصيغة YYYY-MM-DD" };
  }
  if (input.warranty_expiry_date !== undefined && !DATE_RE.test(String(input.warranty_expiry_date))) {
    return { ok: false, reason: "تاريخ انتهاء الضمان لازم بصيغة YYYY-MM-DD" };
  }
  return { ok: true };
};

export const validateDeleteMaintenanceItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_maintenance_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف أجهزة في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الجهاز قصير أوي" };
  return { ok: true };
};

export const validateUpdateEmergencyFundBalance: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["update_emergency_fund_balance"] ?? 0) >= 1) return { ok: false, reason: "تعديل واحد بس في المرة" };
  if (typeof input.new_balance !== "number" || !Number.isFinite(input.new_balance) || input.new_balance < 0) {
    return { ok: false, reason: "الرصيد لازم يكون رقم غير سالب" };
  }
  return { ok: true };
};

export const validateLogPharmacyDose: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["log_pharmacy_dose"] ?? 0) >= 5) return { ok: false, reason: "وصلت لحد أقصى ٥ جرعات في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الدواء قصير أوي" };
  return { ok: true };
};

/** W7 — نفس منطق validateLogPharmacyDose بالظبط (اسم مش id، الـ snapshot مايدّيش
 *  الموديل أي id لأدوية الصيدلية). الوجود الفعلي بيتحقق في executeTool وقت البحث
 *  بالاسم، مش هنا — هنا شكل الإدخال بس. */
export const validateDeletePharmacyItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_pharmacy_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ حذف في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الدواء قصير أوي" };
  return { ok: true };
};

export const validateUpdatePharmacyItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["update_pharmacy_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ تعديلات أدوية في المرة" };
  if (String(input.name ?? "").trim().length < 2) return { ok: false, reason: "اسم الدواء مطلوب" };
  if (input.remaining_quantity !== undefined && (!Number.isFinite(input.remaining_quantity) || input.remaining_quantity < 0 || input.remaining_quantity > 9999)) return { ok: false, reason: "كمية الدواء لازم تكون بين ٠ و٩٩٩٩" };
  if (input.dose_times !== undefined) {
    input.dose_times = normalizeDoseTimes(String(input.dose_times));
    const times = String(input.dose_times).split(",").map((t: string) => t.trim());
    if (!times.length || !times.every((t: string) => DOSE_TIME_RE.test(t))) return { ok: false, reason: "المواعيد لازم تكون HH:MM مفصولة بفاصلة" };
    if (input.daily_dose_count !== undefined && input.daily_dose_count !== times.length) return { ok: false, reason: "عدد الجرعات لازم يساوي عدد المواعيد" };
  }
  if (input.dosage === undefined && input.remaining_quantity === undefined && input.dose_times === undefined && input.daily_dose_count === undefined) return { ok: false, reason: "حدد الكمية أو الجرعة أو المواعيد المطلوب تعديلها" };
  return { ok: true };
};

export const validateCompleteShoppingItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["complete_shopping_item"] ?? 0) >= 5) return { ok: false, reason: "وصلت لحد أقصى ٥ أصناف في المرة" };
  if (String(input.item_name ?? "").trim().length < 2) return { ok: false, reason: "اسم الصنف قصير أوي" };
  return { ok: true };
};

export const validateDeleteShoppingItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["delete_shopping_item"] ?? 0) >= 5) return { ok: false, reason: "وصلت لحد أقصى ٥ حذف في المرة" };
  if (String(input.item_name ?? "").trim().length < 2) return { ok: false, reason: "اسم الصنف قصير أوي" };
  return { ok: true };
};

/** أقصى مدة تأجيل — ٣٠ يوم. أبعد من كده أقرب لتذكير سنوي مش "مهمة مؤجلة"، ومهام
 *  متراكمة من غير سقف زمني بتفضل قاعدة وبتتنسى فعلياً. */
const MAX_SCHEDULE_DAYS_AHEAD = 30;
/** سماحية دقيقتين للماضي — الموديل والعميل ممكن يقولوا "دلوقتي" وبينهم فرق ثواني
 *  عن وقت وصول الطلب فعلاً؛ رفض أي حاجة فاتت عليها أكتر من كده بجد فات وقتها. */
const PAST_GRACE_MINUTES = 2;

export const validateScheduleTask: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["schedule_task"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ مهام مؤجلة في المرة" };
  const desc = String(input.task_description ?? "").trim();
  if (desc.length < 10) return { ok: false, reason: "وصف المهمة قصير أوي، وضّح المطلوب أكتر" };
  if (desc.length > 300) return { ok: false, reason: "وصف المهمة طويل أوي، لخّصه" };
  const runAt = new Date(String(input.run_at ?? ""));
  if (Number.isNaN(runAt.getTime())) return { ok: false, reason: "run_at لازم يكون تاريخ ووقت صحيح بصيغة ISO 8601" };
  const now = Date.now();
  if (runAt.getTime() < now - PAST_GRACE_MINUTES * 60_000) return { ok: false, reason: "الوقت ده فات بالفعل — حدد وقت في المستقبل" };
  if (runAt.getTime() > now + MAX_SCHEDULE_DAYS_AHEAD * 86_400_000) return { ok: false, reason: `أقصى تأجيل ${MAX_SCHEDULE_DAYS_AHEAD} يوم` };
  return { ok: true };
};

export const validateQueryFamily: Validator = (_input, _snap, ctx) => {
  if ((ctx.counts["query_family"] ?? 0) >= 2) return { ok: false, reason: "استعلمت عن العيلة بالفعل في اللفة دي" };
  return { ok: true };
};

export const VALIDATORS: Record<string, Validator> = {
  log_transaction: validateLogTransaction,
  update_transaction: validateUpdateTransaction,
  delete_transaction: validateDeleteTransaction,
  set_monthly_limit: validateSetMonthlyLimit,
  add_inventory_item: validateAddInventoryItem,
  delete_inventory_item: validateDeleteInventoryItem,
  add_pharmacy_item: validateAddPharmacyItem,
  update_pharmacy_item: validateUpdatePharmacyItem,
  set_market: validateSetMarket,
  log_pharmacy_dose: validateLogPharmacyDose,
  delete_pharmacy_item: validateDeletePharmacyItem,
  schedule_task: validateScheduleTask,
  query_family: validateQueryFamily,
  emit_insight: validateEmitInsight,
  ask_user: validateAskUser,
  update_inventory_qty: validateUpdateInventoryQty,
  set_transaction_category: validateSetTransactionCategory,
  suggest_budget_change: validateSuggestBudgetChange,
  add_shopping_item: validateAddShoppingItem,
  complete_shopping_item: validateCompleteShoppingItem,
  delete_shopping_item: validateDeleteShoppingItem,
  remember: validateRemember,
  link_memory: validateLinkMemory,
  // قراءة بس — مفيش كتابة ولا حد استدعاء، زي query_family بالظبط.
  family_digest: () => ({ ok: true }),
  // قراءة بس، بس بحد أقصى عشان ماتتنادش في لفة واحدة كذا مرة وتحرق كوتة على نداءات
  // شبكة خارجية (Overpass/LocationIQ) بدل ما الموديل يرد.
  // مرة واحدة في اللفة: ترشيح منتج مرتين في نفس الرد بيتحوّل من مساعدة لإعلان.
  suggest_product: (_i, _s, ctx) =>
    (ctx.counts["suggest_product"] ?? 0) >= 1
      ? { ok: false, reason: "رشّحت منتج خلاص في اللفة دي" } : { ok: true },
  find_nearby_stores: (_i, _s, ctx) =>
    (ctx.counts["find_nearby_stores"] ?? 0) >= 2
      ? { ok: false, reason: "بحثت عن محلات مرتين خلاص في اللفة دي" } : { ok: true },
  check_price_online: (i, _s, ctx) => {
    if ((ctx.counts["check_price_online"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد ٣ استعلامات سعر في المرة" };
    if (!i.item_name || String(i.item_name).trim().length < 2) return { ok: false, reason: "اسم الصنف قصير أوي" };
    return { ok: true };
  },
  merge_duplicate_expense: () => ({ ok: true }),
  reconcile_cash_balance: validateReconcileCashBalance,
  confirm_cycle_start: validateConfirmCycleStart,
  confirm_obligation: validateConfirmObligation,
  add_subscription: validateAddSubscription,
  update_subscription: validateUpdateSubscription,
  delete_subscription: validateDeleteSubscription,
  add_debt: validateAddDebt,
  update_debt: validateUpdateDebt,
  delete_debt: validateDeleteDebt,
  add_obligation: validateAddObligation,
  update_obligation: validateUpdateObligation,
  delete_obligation: validateDeleteObligation,
  add_maintenance_item: validateAddMaintenanceItem,
  update_maintenance_item: validateUpdateMaintenanceItem,
  delete_maintenance_item: validateDeleteMaintenanceItem,
  update_emergency_fund_balance: validateUpdateEmergencyFundBalance,
};

/**
 * الأدوات اللي بتغيّر بيانات فعلاً — بيتحسبوا في سقف الـ ٥ تعديلات لكل جلسة.
 * أدوات القراءة (query_family) وأدوات الرؤى (emit_insight/ask_user، ليها سقفها الخاص)
 * مش هنا عمداً.
 */
export const MUTATING_TOOLS = [
  "update_inventory_qty", "set_transaction_category", "merge_duplicate_expense",
  "reconcile_cash_balance", "confirm_cycle_start", "confirm_obligation",
  // المرحلة ٢-ب
  "log_transaction", "update_transaction", "delete_transaction", "set_monthly_limit",
  "add_inventory_item", "delete_inventory_item", "add_pharmacy_item", "update_pharmacy_item", "set_market", "log_pharmacy_dose", "delete_pharmacy_item",
  "add_shopping_item", "complete_shopping_item", "delete_shopping_item",
  "schedule_task",
  // W9 — تغطية كاملة (اشتراكات/ديون/صيانة/صندوق الطوارئ)، نفس مستوى خطورة المخزون
  // والصيدلية فوق: بيانات حقيقية بس مش دفتر معاملات فعلي، فمافيش داعي تأكيد بزرار.
  "add_subscription", "update_subscription", "delete_subscription",
  "add_debt", "update_debt", "delete_debt",
  "add_obligation", "update_obligation", "delete_obligation",
  "add_maintenance_item", "update_maintenance_item", "delete_maintenance_item",
  "update_emergency_fund_balance",
];

/**
 * الأدوات اللي بتلمس فلوس حقيقية. دي **مابتتنفذش** من حلقة agent_turn أبداً — بتتحوّل
 * لاقتراح ينتظر ضغطة تأكيد صريحة من العميل، وبعدين بتتنفذ من agent_confirm بنفس مسار
 * التحقق والتنفيذ. حارس أمان، مش تفصيل تقني.
 */
export const CONFIRM_REQUIRED_TOOLS = ["log_transaction", "update_transaction", "delete_transaction", "set_monthly_limit"];

/** بوابة الفحص العامة — الحدود المشتركة (mutation cap, 3-strikes abort) قبل ما توصل للـ validator المتخصص */
export async function validateTool(name: string, input: any, snap: any, ctx: RunContext): Promise<Validation> {
  if (ctx.abortedTools.has(name)) {
    return { ok: false, reason: "الأداة دي اتوقفت الجلسة دي بعد ٣ محاولات فاشلة" };
  }
  if (ctx.mutationCount >= 5 && MUTATING_TOOLS.includes(name)) {
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

/**
 * هل اللفة دي رد العميل على سؤال العقل؟
 *
 * لو أيوة، غياب `remember()` فشل حقيقي: الإجابة بتتنفّذ وبتترمي، فنفس السؤال بيترجع
 * الشهر الجاي. (الدليل: `zad_memory` فيه ٤ صفوف كلهم من رفض تنبيهات، ولا صف من إجابة.)
 *
 * الكشف بطريقتين عن قصد:
 *  - `answered_question: true` — العقد الصريح، للنسخ الجاية من التطبيق.
 *  - بادئة النص اللي `ZadViewModel.answerBrainQuestion` بيبنيها — ربط هش بين رانتايمين
 *    على نص حرفي، بس هو الوحيد اللي بيشتغل مع النسخة **المتسطبة على أجهزة الناس دلوقتي**.
 *    من غيره الميزة تفضل ميتة لحد ما كل واحد يحدّث التطبيق. بيتشال لما العلم يبقى منتشر.
 */
export const ANSWER_PREFIX = "العميل جاوب على سؤال:";

export function looksLikeAnsweredQuestion(
  trigger: string,
  userMessage: unknown,
  explicitFlag?: unknown,
): boolean {
  if (explicitFlag === true) return true;
  if (trigger !== "event") return false;
  return typeof userMessage === "string" && userMessage.trimStart().startsWith(ANSWER_PREFIX);
}
