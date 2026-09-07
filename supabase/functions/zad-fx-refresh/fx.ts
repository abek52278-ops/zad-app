// المنطق النقي لتحديث أسعار الصرف — متفصّل عن Deno.serve عشان يتختبر لوحده.

/**
 * العملات اللي التطبيق بيدعمها فعلاً (نفس مفاتيح `CurrencyExchange.usdRate`
 * و`MarketProfile`). الدفعة لازم تغطيهم **كلهم** أو ماتتكتبش — جدول نصه محدَّث
 * ونصه قديم أسوأ من جدول قديم كله، لأن المقارنة بين عملتين وقتها بتبقى بين
 * تاريخين مختلفين من غير ما حد ياخد باله.
 */
export const REQUIRED_CODES: readonly string[] = [
  "USD", "SAR", "EGP", "AED", "KWD", "QAR", "BHD", "OMR", "JOD", "LBP",
  "IQD", "SYP", "YER", "ILS", "LYD", "SDG", "MAD", "TND", "DZD", "TRY",
];

export interface FxRow {
  code: string;
  /** كام دولار تساوي وحدة واحدة من العملة — نفس تعريف عمود `usd_rate`. */
  usd_rate: number;
}

export type ParseResult =
  | { ok: true; rows: FxRow[] }
  | { ok: false; reason: string };

/**
 * المزوّد بيرجّع `rates[c]` = كام وحدة من العملة تساوي دولار واحد، والعمود عندنا
 * معرّف بالعكس، فالمقلوب هو التحويل: `usd_rate = 1 / rates[c]`.
 *
 * **بوابة القبول متعمدة إنها ترفض الدفعة كلها** لو أي رمز ناقص أو أي قيمة مش
 * منطقية. وبالمقابل **مافيش سقف على حجم القفزة**: قاعدة زي «ارفض أي تغيير أكبر
 * من ٥٠٪» كانت هتحمي الغلط بدل ما تمنعه — الليرة السورية اتغيّرت بعامل ~١٠٧
 * بعد إعادة التقويم، والسعر المطبوع في التطبيق غلط بنفس النسبة دلوقتي. القفزة
 * بتتسجّل في اللوج عشان تتراجع، مش بتترفض.
 */
export function parseProviderPayload(payload: unknown): ParseResult {
  if (typeof payload !== "object" || payload === null) {
    return { ok: false, reason: "payload is not an object" };
  }
  const body = payload as { result?: unknown; rates?: unknown };
  if (body.result !== "success") {
    return { ok: false, reason: `provider result=${String(body.result)}` };
  }
  if (typeof body.rates !== "object" || body.rates === null) {
    return { ok: false, reason: "rates missing" };
  }
  const rates = body.rates as Record<string, unknown>;

  const rows: FxRow[] = [];
  const missing: string[] = [];
  for (const code of REQUIRED_CODES) {
    const raw = rates[code];
    const perUsd = typeof raw === "number" ? raw : Number(raw);
    if (!Number.isFinite(perUsd) || perUsd <= 0) {
      missing.push(code);
      continue;
    }
    rows.push({ code, usd_rate: 1 / perUsd });
  }
  if (missing.length > 0) {
    return { ok: false, reason: `missing or invalid: ${missing.join(",")}` };
  }
  return { ok: true, rows };
}

export interface RateMove {
  code: string;
  from: number;
  to: number;
  pct: number;
}

/**
 * الحركات الكبيرة بتترصد عشان تبان في اللوج. مش حارس — قرار الكتابة اتاخد خلاص
 * عند بوابة القبول؛ ده أثر عشان لو المزوّد رجّع رقم غريب يبقى فيه دليل مكتوب.
 */
export function significantMoves(
  previous: ReadonlyMap<string, number>,
  next: readonly FxRow[],
  thresholdPct = 10,
): RateMove[] {
  const moves: RateMove[] = [];
  for (const row of next) {
    const before = previous.get(row.code);
    if (before === undefined || before <= 0) continue;
    const pct = ((row.usd_rate - before) / before) * 100;
    if (Math.abs(pct) >= thresholdPct) {
      moves.push({ code: row.code, from: before, to: row.usd_rate, pct });
    }
  }
  return moves.sort((a, b) => Math.abs(b.pct) - Math.abs(a.pct));
}
