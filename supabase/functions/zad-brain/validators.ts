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
export const validateAddInventoryItem: Validator = (input, snap, ctx) => {
  if ((ctx.counts["add_inventory_item"] ?? 0) >= 10) return { ok: false, reason: "وصلت لحد أقصى ١٠ أصناف في المرة" };
  const name = String(input.item_name ?? "").trim();
  if (name.length < 2) return { ok: false, reason: "اسم الصنف قصير أوي" };
  if (typeof input.quantity !== "number" || input.quantity <= 0 || input.quantity > 999) {
    return { ok: false, reason: "الكمية لازم تكون بين ١ و٩٩٩" };
  }
  const exists = (snap.stock ?? []).some((s: any) => s.name === name);
  if (exists) return { ok: false, reason: "الصنف موجود بالفعل — استخدم update_inventory_qty عشان تعدّل كميته" };
  return { ok: true };
};

const PHARMACY_UNITS = ["قرص", "مل", "كريم"];
const DOSE_TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

export const validateAddPharmacyItem: Validator = (input, _snap, ctx) => {
  if ((ctx.counts["add_pharmacy_item"] ?? 0) >= 3) return { ok: false, reason: "وصلت لحد أقصى ٣ أدوية في المرة" };
  const name = String(input.name ?? "").trim();
  if (name.length < 2) return { ok: false, reason: "اسم الدواء قصير أوي" };
  if (input.unit !== undefined && !PHARMACY_UNITS.includes(input.unit)) {
    return { ok: false, reason: `الوحدة لازم تكون واحدة من: ${PHARMACY_UNITS.join("، ")}` };
  }
  if (input.dose_times !== undefined && input.dose_times !== null && String(input.dose_times).length > 0) {
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

export const validateQueryFamily: Validator = (_input, _snap, ctx) => {
  if ((ctx.counts["query_family"] ?? 0) >= 2) return { ok: false, reason: "استعلمت عن العيلة بالفعل في اللفة دي" };
  return { ok: true };
};

export const VALIDATORS: Record<string, Validator> = {
  log_transaction: validateLogTransaction,
  update_transaction: validateUpdateTransaction,
  set_monthly_limit: validateSetMonthlyLimit,
  add_inventory_item: validateAddInventoryItem,
  add_pharmacy_item: validateAddPharmacyItem,
  set_market: validateSetMarket,
  query_family: validateQueryFamily,
  emit_insight: validateEmitInsight,
  ask_user: validateAskUser,
  update_inventory_qty: validateUpdateInventoryQty,
  set_transaction_category: validateSetTransactionCategory,
  suggest_budget_change: validateSuggestBudgetChange,
  add_shopping_item: validateAddShoppingItem,
  remember: validateRemember,
  merge_duplicate_expense: () => ({ ok: true }),
  reconcile_cash_balance: validateReconcileCashBalance,
  confirm_cycle_start: validateConfirmCycleStart,
  confirm_obligation: validateConfirmObligation,
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
  "log_transaction", "update_transaction", "set_monthly_limit",
  "add_inventory_item", "add_pharmacy_item", "set_market",
];

/**
 * الأدوات اللي بتلمس فلوس حقيقية. دي **مابتتنفذش** من حلقة agent_turn أبداً — بتتحوّل
 * لاقتراح ينتظر ضغطة تأكيد صريحة من العميل، وبعدين بتتنفذ من agent_confirm بنفس مسار
 * التحقق والتنفيذ. حارس أمان، مش تفصيل تقني.
 */
export const CONFIRM_REQUIRED_TOOLS = ["log_transaction", "update_transaction", "set_monthly_limit"];

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
