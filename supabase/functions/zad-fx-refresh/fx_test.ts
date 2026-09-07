import { assert, assertEquals } from "jsr:@std/assert@1";
import { parseProviderPayload, REQUIRED_CODES, significantMoves } from "./fx.ts";

function payloadWith(overrides: Record<string, unknown> = {}, result = "success") {
  const rates: Record<string, unknown> = {};
  for (const c of REQUIRED_CODES) rates[c] = c === "USD" ? 1 : 2;
  return { result, rates: { ...rates, ...overrides } };
}

Deno.test("the inverse is the whole conversion: usd_rate = 1 / rate", () => {
  // المزوّد: كام وحدة تساوي دولار. العمود عندنا: كام دولار تساوي وحدة.
  const parsed = parseProviderPayload(payloadWith({ EGP: 50 }));
  assert(parsed.ok);
  const egp = parsed.rows.find((r) => r.code === "EGP")!;
  assertEquals(egp.usd_rate, 1 / 50);
  assertEquals(parsed.rows.find((r) => r.code === "USD")!.usd_rate, 1);
});

Deno.test("a complete payload yields every required code, once", () => {
  const parsed = parseProviderPayload(payloadWith());
  assert(parsed.ok);
  assertEquals(parsed.rows.length, REQUIRED_CODES.length);
  assertEquals(new Set(parsed.rows.map((r) => r.code)).size, REQUIRED_CODES.length);
});

// ── بوابة القبول: الكل أو لا شيء ──────────────────────────────────────────────

Deno.test("one missing currency rejects the whole batch", () => {
  const p = payloadWith();
  delete (p.rates as Record<string, unknown>).TRY;
  const parsed = parseProviderPayload(p);
  assert(!parsed.ok);
  assert(parsed.reason.includes("TRY"), parsed.reason);
});

Deno.test("zero, negative and non-numeric rates reject the batch", () => {
  for (const bad of [0, -1, "abc", null, undefined, Number.NaN, Number.POSITIVE_INFINITY]) {
    const parsed = parseProviderPayload(payloadWith({ SDG: bad }));
    assert(!parsed.ok, `قيمة ${String(bad)} كان لازم ترفض الدفعة`);
    assert(parsed.reason.includes("SDG"), parsed.reason);
  }
});

Deno.test("a non-success provider result is never written", () => {
  const parsed = parseProviderPayload(payloadWith({}, "error"));
  assert(!parsed.ok);
});

Deno.test("garbage payloads are rejected rather than throwing", () => {
  for (const junk of [null, undefined, 42, "text", [], {}]) {
    const parsed = parseProviderPayload(junk);
    assert(!parsed.ok, `${JSON.stringify(junk)} كان لازم يترفض`);
  }
});

// ── الحركات الكبيرة تتسجّل ولا تترفض ──────────────────────────────────────────

Deno.test("a huge correction is reported, not blocked", () => {
  // الحالة الحقيقية: SYP المطبوعة 0.0000769 والصح 0.00821738 — عامل ~107.
  // سقف يرفض القفزات كان هيمنع التصحيح ده بالظبط، فالمقصود إنه يعدّي ويتسجّل.
  const previous = new Map([["SYP", 0.0000769]]);
  const moves = significantMoves(previous, [{ code: "SYP", usd_rate: 0.00821738 }]);
  assertEquals(moves.length, 1);
  assertEquals(moves[0].code, "SYP");
  assert(moves[0].pct > 1000, `${moves[0].pct}`);
});

Deno.test("pegged currencies stay quiet", () => {
  // الخليجية مربوطة بالدولار، فالهاردكودنغ صح ليها — ومحصلش إنها تتحرك.
  const previous = new Map([["SAR", 0.2667], ["AED", 0.2723]]);
  const moves = significantMoves(previous, [
    { code: "SAR", usd_rate: 0.26666667 },
    { code: "AED", usd_rate: 0.27229408 },
  ]);
  assertEquals(moves, []);
});

Deno.test("moves are ordered by magnitude and skip unknown or zero baselines", () => {
  const previous = new Map([["TRY", 0.0295], ["LYD", 0.2058], ["EGP", 0]]);
  const moves = significantMoves(previous, [
    { code: "TRY", usd_rate: 0.020635 },  // ~+43%
    { code: "LYD", usd_rate: 0.157580 },  // ~+31%
    { code: "EGP", usd_rate: 0.019649 },  // أساس صفر — يتخطى
    { code: "NEW", usd_rate: 1.0 },       // مالوش سابق — يتخطى
  ]);
  assertEquals(moves.map((m) => m.code), ["TRY", "LYD"]);
});
