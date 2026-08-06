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

/** "d:<insight_id>:<reason_code>" callback_data */
export function parseDismissCallback(data: string): { insightId: string; reasonCode: string } | null {
  const parts = data.split(":");
  if (parts.length !== 3 || parts[0] !== "d") return null;
  return { insightId: parts[1], reasonCode: parts[2] };
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

export function formatBalanceMessage(budget: number, spent: number, income: number): string {
  const remaining = budget - spent + income;
  // "الرصيد المتبقي" هنا شهر تقويمي بسيط عمدًا — مش نفس "متاح" اللي في التطبيق (دورة
  // راتب + التزامات ثابتة، BudgetMath.availableInCycle/buildSnapshot's available). دمج
  // نفس الحساب هنا محتاج استخراج المنطق لموديول مشترك، مؤجل عمدًا — انظر PROGRESS.md.
  return `الرصيد المتبقي الشهر ده: ${remaining.toFixed(2)}\n(الميزانية: ${budget.toFixed(2)} — المصروف: ${spent.toFixed(2)})`;
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
