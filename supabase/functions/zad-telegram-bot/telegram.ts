// Pure helpers — no grammY, no Supabase, no network. Framework-agnostic on purpose so
// they stay testable without spinning up a Bot instance (grammY's Bot needs a live
// token to construct in some code paths); index.ts is the only place that touches
// grammY or Supabase.

export interface InlineKeyboardButton { text: string; callback_data: string }

export function mainMenuKeyboard(): InlineKeyboardButton[][] {
  return [
    [{ text: "الرصيد المتبقي", callback_data: "b" }],
    [{ text: "آخر المعاملات", callback_data: "t" }],
    [{ text: "التنبيهات المعلقة", callback_data: "i" }],
  ];
}

/** dismiss_reason الأقصر ممكن — Telegram callback_data محدود بـ٦٤ بايت، ومحتاج نضم insight id كمان */
const DISMISS_REASON_CODES: Record<string, string> = { n: "not_relevant", w: "wrong_data", t: "timing" };
const DISMISS_REASON_LABELS: Record<string, string> = { n: "مش مهم", w: "الرقم غلط", t: "عرفت خلاص" };

export function reasonForCode(code: string): string | null {
  return DISMISS_REASON_CODES[code] ?? null;
}

export function dismissKeyboard(insightId: string): InlineKeyboardButton[][] {
  return [Object.keys(DISMISS_REASON_LABELS).map((code) => ({
    text: DISMISS_REASON_LABELS[code],
    callback_data: `d:${insightId}:${code}`,
  }))];
}

/** Task 28's dismiss reason → zad_memory note. Mirrors DismissalMemory.kt (client) and
 * zad-brain/validators.ts's own dismissal handling — same intentional small duplication
 * as BudgetMath.kt/buildSnapshot's cycle math (different runtimes, not worth a shared
 * module yet across a Kotlin app + two independent Deno functions). */
export function memoryNoteForDismissal(reason: string, subject: string): { scope: string; note: string; confidence: number } | null {
  switch (reason) {
    case "not_relevant": return { scope: "dismissal", note: `مش مهتم بتنبيهات زي "${subject}"`, confidence: 0.5 };
    case "wrong_data": return { scope: "data_quality", note: `العميل قال إن "${subject}" غلط — البيانات المصدر محتاجة مراجعة`, confidence: 0.7 };
    case "timing": return { scope: "dismissal", note: `عرف بالفعل عن "${subject}" وقت الرفض ده`, confidence: 0.3 };
    default: return null;
  }
}

/** grammY's ctx.match for a command handler is already just the text after the command
 * (or "" if none) — no more "/start " prefix to strip, unlike a hand-rolled regex. */
export function normalizeBindingCode(arg: string | undefined): string | null {
  const trimmed = (arg ?? "").trim();
  if (!/^[A-Za-z0-9-]{4,32}$/.test(trimmed)) return null;
  return trimmed.toUpperCase();
}

/** Confirm/cancel for a parsed spend intent. Only the pending-row id travels in
 * callback_data — the amount/title/category live in telegram_pending_writes, because
 * callback_data is capped at 64 bytes and a truncated amount would be a silent
 * data-corruption bug. */
export function confirmSpendKeyboard(pendingId: string): InlineKeyboardButton[][] {
  return [[
    { text: "✅ أكد التسجيل", callback_data: `x:${pendingId}` },
    { text: "✖️ إلغاء", callback_data: `c:${pendingId}` },
  ]];
}

/** "x:<uuid>" (confirm) / "c:<uuid>" (cancel) */
export function parseSpendCallback(data: string): { action: "confirm" | "cancel"; pendingId: string } | null {
  const parts = data.split(":");
  if (parts.length !== 2) return null;
  if (parts[0] !== "x" && parts[0] !== "c") return null;
  if (!/^[0-9a-fA-F-]{36}$/.test(parts[1])) return null;
  return { action: parts[0] === "x" ? "confirm" : "cancel", pendingId: parts[1] };
}

/**
 * تأكيد/إلغاء لأي أداة تانية محتاجة موافقة غير `log_transaction` — تعديل معاملة،
 * مسحها، أو تغيير السقف الشهري.
 *
 * من غير الكيبورد ده، الاقتراحات دي كانت بتوصل للعميل كسطر نصي بيقول "ابعتها لوحدها
 * عشان أأكدها معاك" — وهو أصلاً باعتها لوحدها، فنفس السطر بيتكرر للأبد. "امسح
 * المعاملة دي" من تليجرام كانت مستحيلة حرفياً، مش صعبة.
 *
 * "tx:"/"tc:" متمايزين عن "x:"/"c:" (فلوس) و"mx:"/"mc:" (دوا) عشان الراوتر يفرّق بين
 * التلات طوابير قبل ما يلمس أي جدول. نفس القاعدة: الـ id بس هو اللي بيسافر في
 * callback_data — الأداة ومدخلاتها في telegram_pending_tools، لأن الحد ٦٤ بايت.
 */
export function confirmToolKeyboard(pendingId: string): InlineKeyboardButton[][] {
  return [[
    { text: "✅ أكد", callback_data: `tx:${pendingId}` },
    { text: "✖️ إلغاء", callback_data: `tc:${pendingId}` },
  ]];
}

/** "tx:<uuid>" (confirm) / "tc:<uuid>" (cancel) */
export function parseToolCallback(data: string): { action: "confirm" | "cancel"; pendingId: string } | null {
  const parts = data.split(":");
  if (parts.length !== 2) return null;
  if (parts[0] !== "tx" && parts[0] !== "tc") return null;
  if (!/^[0-9a-fA-F-]{36}$/.test(parts[1])) return null;
  return { action: parts[0] === "tx" ? "confirm" : "cancel", pendingId: parts[1] };
}

/** "d:<insight_id>:<reason_code>" callback_data */
export function parseDismissCallback(data: string): { insightId: string; reasonCode: string } | null {
  const parts = data.split(":");
  if (parts.length !== 3 || parts[0] !== "d") return null;
  return { insightId: parts[1], reasonCode: parts[2] };
}

/** Confirm/cancel for a parsed medication schedule (Smart Medication Parsing). Same
 * "only the pending-row id in callback_data" reasoning as confirmSpendKeyboard — a
 * "mx:"/"mc:" prefix (distinct from spend's "x:"/"c:") so the callback router can tell
 * the two pending flows apart before touching either table. */
export function confirmMedicationKeyboard(pendingId: string): InlineKeyboardButton[][] {
  return [[
    { text: "✅ أكد وفعّل التذكير", callback_data: `mx:${pendingId}` },
    { text: "✖️ إلغاء", callback_data: `mc:${pendingId}` },
  ]];
}

/** "mx:<uuid>" (confirm) / "mc:<uuid>" (cancel) */
export function parseMedicationCallback(data: string): { action: "confirm" | "cancel"; pendingId: string } | null {
  const parts = data.split(":");
  if (parts.length !== 2) return null;
  if (parts[0] !== "mx" && parts[0] !== "mc") return null;
  if (!/^[0-9a-fA-F-]{36}$/.test(parts[1])) return null;
  return { action: parts[0] === "mx" ? "confirm" : "cancel", pendingId: parts[1] };
}

/** Telegram Micro-Checkins — "is <item> still in stock?" prompt buttons. Only the prompt
 * row's id travels in callback_data (same reasoning as confirmSpendKeyboard: a long
 * Arabic item name risks the 64-byte callback_data cap). */
export function checkInKeyboard(promptId: string): InlineKeyboardButton[][] {
  return [[
    { text: "✅ لسه موجود", callback_data: `ck:${promptId}:y` },
    { text: "❌ خلص", callback_data: `ck:${promptId}:n` },
  ]];
}

/** "ck:<uuid>:y|n" callback_data */
export function parseCheckInCallback(data: string): { promptId: string; stillInStock: boolean } | null {
  const parts = data.split(":");
  if (parts.length !== 3 || parts[0] !== "ck") return null;
  if (parts[2] !== "y" && parts[2] !== "n") return null;
  if (!/^[0-9a-fA-F-]{36}$/.test(parts[1])) return null;
  return { promptId: parts[1], stillInStock: parts[2] === "y" };
}

export function checkInPromptMessage(itemName: string): string {
  return `تذكير سريع: ${itemName} لسه موجود عندك ولا خلص؟`;
}

/** The zad_budget_state() row this button renders. Only the fields the message uses. */
export interface BudgetStateRow {
  /** The cycle's opening balance. Named for the column, which predates the ledger. */
  monthly_limit: number | null;
  spent: number;
  income: number;
  /** The ledger balance: opening + income - spent. Null only when no opening is set. */
  remaining: number | null;
  committed: number;
  available: number | null;
  days_left: number;
}

/**
 * Phase 0 — a renderer, not a calculator. It used to take (budget, spent, income) and do
 * `budget - spent + income` over a calendar month, which is not the figure the app shows:
 * the app is salary-cycle aware and subtracts fixed obligations to reach "المتاح". The
 * customer could therefore read one number in the app and a different one in Telegram for
 * the same day. Every value here now arrives already computed by zad_budget_state().
 *
 * A null opening balance prints "مش محدد" — never 0. Telling someone with no balance set
 * that they have 0 left is a different (and worse) statement than telling them it is
 * unknown.
 *
 * The wording changed with the ledger migration (20260816010000) and the numbers changed
 * underneath it. `remaining` is no longer `ceiling - spent`; it is the balance itself,
 * `opening + income - spent`. Calling that "المتبقي من السقف" would have described money
 * the customer has as a budget allowance they have left, which is the exact confusion the
 * ledger exists to remove.
 */
export function formatBalanceMessage(s: BudgetStateRow, currency: string): string {
  const unit = currency && currency !== "غير معروف" ? ` ${currency}` : "";
  const fmt = (n: number | null) => n === null ? "غير معروف" : `${n.toFixed(2)}${unit}`;
  if (s.monthly_limit === null) {
    return [
      `مصروف الدورة دي: ${fmt(s.spent)}`,
      "رصيدك لسه مش محدد، فمقدرش أقولك فاضل كام.",
      "ظبّطه من التطبيق وأنا أحسبهولك.",
    ].join("\n");
  }
  return [
    `رصيدك: ${fmt(s.remaining)}`,
    `(بدأت الدورة بـ ${fmt(s.monthly_limit)} — دخل: ${fmt(s.income)} — مصروف: ${fmt(s.spent)})`,
    `المتاح بعد خصم المحجوز (${fmt(s.committed)}): ${fmt(s.available)} — فاضل ${s.days_left} يوم في الدورة.`,
  ].join("\n");
}

export function formatTransactionsMessage(txs: Array<{ title: string; amount: number; txn_kind: string; created_at: string | null }>): string {
  if (txs.length === 0) return "مفيش معاملات مسجلة لسه.";
  return txs.slice(0, 10).map((t) => {
    const sign = t.txn_kind === "income" ? "+" : "-";
    const date = t.created_at?.slice(0, 10) ?? "";
    return `${sign}${t.amount.toFixed(2)} — ${t.title} (${date})`;
  }).join("\n");
}

export function formatInsightTitle(insight: { title: string; body: string }): string {
  return `${insight.title}\n${insight.body}`;
}
