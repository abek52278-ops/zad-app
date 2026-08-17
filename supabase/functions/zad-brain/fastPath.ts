// The deterministic layer (gaps item 4).
//
// Most of what the customer types is not ambiguous. "سجل ٥٠ قهوة" and "فاضل كام" do not
// need a language model to understand — they need a regex and a SQL read. Routing them
// through Gemini costs a request from a 20-per-day-per-key budget, adds a second of
// latency, and on the bad day (2026-08-15: 14 failures, all quota) returns nothing at all.
//
// So the model is for the ambiguous, and this is for the rest.
//
// ## The bar for matching is deliberately high
//
// A false positive here is worse than a miss. If "سجل ٥٠ قهوة بس متحسبهاش على الميزانية"
// matches the simple log pattern, the customer's actual instruction is silently dropped
// and they get a confirmation for something they did not ask for. A miss just costs one
// model call — the thing we already do today.
//
// So: every pattern is anchored to the whole message, anything with a conjunction or a
// second clause is refused, and anything left over goes to the model untouched.
//
// ## Money still gets confirmed
//
// A matched expense does NOT write. It produces the same proposal the model's
// log_transaction tool would, and the customer confirms it the same way. The fast path
// changes who parsed the sentence, not who approves the spend.

/** What the message turned out to be, or null when the model should handle it. */
export type FastIntent =
  | { kind: "balance" }
  | { kind: "log_expense"; amount: number; title: string; wallet: "cash" | "card" | null };

const ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩";
const EASTERN_ARABIC_INDIC = "۰۱۲۳۴۵۶۷۸۹";

/** ٥٠ and ۵۰ are both fifty. Postgres will not take either. */
export function normalizeDigits(input: string): string {
  let out = "";
  for (const ch of input) {
    const ai = ARABIC_INDIC.indexOf(ch);
    if (ai >= 0) { out += String(ai); continue; }
    const ei = EASTERN_ARABIC_INDIC.indexOf(ch);
    if (ei >= 0) { out += String(ei); continue; }
    out += ch;
  }
  return out;
}

/**
 * Anything that means the sentence carries more than one instruction. A message with any
 * of these goes to the model even if the rest of it looks like a clean match — "و" is the
 * one that matters most, since "سجل ٥٠ قهوة و٣٠ شاي" is two transactions and the naive
 * pattern would silently record one.
 */
// NOTE ON \b: JavaScript's word boundary is defined against [A-Za-z0-9_]. Arabic letters
// are not word characters, so `\bكمان\b` sits between two non-word positions and never
// matches — the first version of this file used \b throughout and every Arabic keyword
// guard was silently dead. Boundaries here are explicit: start/space before, space/end
// after.
const AR_START = "(?<=^|\\s)";
const AR_END = "(?=\\s|$)";
const MULTI_CLAUSE = new RegExp(
  "(\\sو|^و|،|,|\\+|؛|;|\\?|؟|" +
  AR_START + "(?:و?[اأآ]يضا|كمان|بعدين|ولا|بس|لكن)" + AR_END + ")",
);

const BALANCE_PATTERNS: RegExp[] = [
  /^(فاضل|فاضلي|باقي|متبقي)\s*(كام|قد ايه|قد إيه)$/,
  /^(كام|قد ايه|قد إيه)\s*(فاضل|فاضلي|باقي|متبقي)$/,
  /^(رصيدي|رصيد)( كام)?$/,
  /^(المتاح|متاح)( كام)?$/,
  /^كام معايا$/,
  /^معايا كام$/,
  /^(what'?s )?my balance$/i,
  /^balance$/i,
  /^how much (do i have|is left)$/i,
];

/**
 * Verbs that mean "record this as spent". Deliberately not including bare "دفعت"/"اشتريت"
 * without an amount, and not including anything that could be a question.
 */
const LOG_VERB = /^(سجل|سجّل|اكتب|احسب|ضيف|أضف|log|add)\s+/;

const CASH_HINT = new RegExp(AR_START + "(?:كاش|نقدي|cash)" + AR_END);
const CARD_HINT = new RegExp(AR_START + "(?:بالكارت|كارت|فيزا|card|visa)" + AR_END);

/** Words that carry no meaning for the title once the amount and verb are removed. */
const TITLE_NOISE = new RegExp(
  AR_START + "(?:جنيه|جنية|ريال|درهم|دينار|egp|sar|aed|kwd|على|علي|بتاع|من|في)" + AR_END,
  "g",
);

/**
 * Reads a message and says what it unambiguously is, or null.
 *
 * Never throws, never partially matches: the caller can treat null as "not my problem"
 * and hand the original text to the model unchanged.
 */
export function parseFastPath(raw: string): FastIntent | null {
  const message = normalizeDigits(String(raw ?? "").trim())
    .replace(/\s+/g, " ")
    .replace(/[.!]+$/, "");
  if (message.length === 0 || message.length > 120) return null;

  const lowered = message.toLowerCase();

  for (const p of BALANCE_PATTERNS) {
    if (p.test(lowered)) return { kind: "balance" };
  }

  // Everything below writes money, so the multi-clause guard applies from here down only.
  // A balance question with a question mark is still just a balance question, but
  // "سجل ٥٠ قهوة و٣٠ شاي" must never be read as one transaction.
  if (MULTI_CLAUSE.test(message)) return null;

  if (!LOG_VERB.test(lowered)) return null;
  const body = message.replace(LOG_VERB, "").trim();

  // Exactly one number. Two numbers means we do not know which is the amount.
  // The sign is captured deliberately: "سجل -50 قهوة" is not a spend, and matching only
  // the digits would have turned it into one.
  const numbers = body.match(/-?\d+(?:\.\d{1,2})?/g);
  if (!numbers || numbers.length !== 1) return null;
  const amount = Number(numbers[0]);
  if (!Number.isFinite(amount) || amount <= 0 || amount > 1_000_000) return null;

  const wallet = CASH_HINT.test(lowered) ? "cash" as const
    : CARD_HINT.test(lowered) ? "card" as const
    : null;

  const title = body
    .replace(numbers[0], " ")
    .replace(CASH_HINT, " ")
    .replace(CARD_HINT, " ")
    .replace(TITLE_NOISE, " ")
    .replace(/\s+/g, " ")
    .trim();

  // No title means we would be proposing "spend 50 on ???". The model can ask.
  if (title.length < 2) return null;

  return { kind: "log_expense", amount, title, wallet };
}

/**
 * The balance reply, built from zad_budget_state. Same three lines the Telegram bot
 * sends, on purpose: the customer should not get two different answers to the same
 * question depending on which surface they asked from.
 */
export function formatBalanceReply(state: Record<string, unknown> | null): string {
  if (!state) return "مقدرتش أقرا رصيدك دلوقتي — جرب تاني بعد شوية.";
  const currency = typeof state.currency === "string" && state.currency !== "غير معروف"
    ? ` ${state.currency}` : "";
  const num = (k: string): number | null => {
    const v = state[k];
    return typeof v === "number" ? v : (typeof v === "string" && v.trim() !== "" ? Number(v) : null);
  };
  const fmt = (n: number | null) => n === null || !Number.isFinite(n) ? "غير معروف" : `${n.toFixed(2)}${currency}`;

  const limit = num("monthly_limit");
  if (limit === null) {
    return [
      `مصروف الدورة دي: ${fmt(num("spent"))}`,
      "رصيدك لسه مش محدد، فمقدرش أقولك فاضل كام.",
      "ظبّطه من التطبيق وأنا أحسبهولك.",
    ].join("\n");
  }
  const daysLeft = num("days_left");
  return [
    `رصيدك: ${fmt(num("remaining"))}`,
    `(بدأت الدورة بـ ${fmt(limit)} — دخل: ${fmt(num("income"))} — مصروف: ${fmt(num("spent"))})`,
    `المتاح بعد خصم المحجوز (${fmt(num("committed"))}): ${fmt(num("available"))}` +
      (daysLeft === null ? "" : ` — فاضل ${daysLeft} يوم في الدورة.`),
  ].join("\n");
}
