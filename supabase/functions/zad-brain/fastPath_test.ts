// The bar for these is asymmetric on purpose. A missed match costs one model call — the
// thing we already do today. A wrong match silently answers a question the customer did
// not ask, or records a transaction they did not describe. So most of these tests are
// about what must NOT match.

import { assertEquals } from "jsr:@std/assert@1";
import { formatBalanceReply, normalizeDigits, parseFastPath } from "./fastPath.ts";

Deno.test("Arabic-indic and Persian digits normalize to ASCII", () => {
  assertEquals(normalizeDigits("٥٠"), "50");
  assertEquals(normalizeDigits("۱۲۳"), "123");
  assertEquals(normalizeDigits("سجل ٧٫٥"), "سجل 7٫5");
});

Deno.test("balance questions are recognised in both scripts", () => {
  for (const q of ["فاضل كام", "كام فاضل", "رصيدي", "رصيدي كام", "المتاح", "كام معايا", "balance", "my balance"]) {
    assertEquals(parseFastPath(q), { kind: "balance" }, q);
  }
});

Deno.test("a simple expense parses to a proposal, digits in either script", () => {
  assertEquals(parseFastPath("سجل 50 قهوة"), { kind: "log_expense", amount: 50, title: "قهوة", wallet: null });
  assertEquals(parseFastPath("سجل ٥٠ قهوة"), { kind: "log_expense", amount: 50, title: "قهوة", wallet: null });
  assertEquals(parseFastPath("سجّل 120 بنزين"), { kind: "log_expense", amount: 120, title: "بنزين", wallet: null });
});

Deno.test("the wallet is picked up when the customer says it", () => {
  assertEquals(parseFastPath("سجل 50 قهوة كاش"), { kind: "log_expense", amount: 50, title: "قهوة", wallet: "cash" });
  assertEquals(parseFastPath("سجل 50 قهوة بالكارت"), { kind: "log_expense", amount: 50, title: "قهوة", wallet: "card" });
});

Deno.test("currency words are stripped from the title, not left in it", () => {
  assertEquals(parseFastPath("سجل 50 جنيه قهوة"), { kind: "log_expense", amount: 50, title: "قهوة", wallet: null });
});

Deno.test("two items in one sentence go to the model, never recorded as one", () => {
  // This is the case the guard exists for: naive parsing records 50 and drops the 30.
  assertEquals(parseFastPath("سجل ٥٠ قهوة و٣٠ شاي"), null);
  assertEquals(parseFastPath("سجل 50 قهوة، 30 شاي"), null);
});

Deno.test("an extra instruction disqualifies the whole message", () => {
  assertEquals(parseFastPath("سجل ٥٠ قهوة بس متحسبهاش على الميزانية"), null);
  assertEquals(parseFastPath("سجل 50 قهوة كمان"), null);
});

Deno.test("ambiguous or incomplete money messages go to the model", () => {
  assertEquals(parseFastPath("سجل قهوة"), null);           // no amount
  assertEquals(parseFastPath("سجل 50"), null);             // no title
  assertEquals(parseFastPath("سجل 50 60 قهوة"), null);     // which one is the amount
  assertEquals(parseFastPath("سجل -50 قهوة"), null);       // not a spend
  assertEquals(parseFastPath("صرفت 50 على القهوة"), null); // not an imperative we claim
});

Deno.test("questions about spending are not treated as commands to record it", () => {
  assertEquals(parseFastPath("صرفت كام على القهوة؟"), null);
  assertEquals(parseFastPath("سجل 50 قهوة؟"), null);
});

Deno.test("empty, huge and absurd inputs are refused", () => {
  assertEquals(parseFastPath(""), null);
  assertEquals(parseFastPath("   "), null);
  assertEquals(parseFastPath("سجل 9999999 قهوة"), null);
  assertEquals(parseFastPath("سجل 50 " + "ا".repeat(200)), null);
});

Deno.test("the balance reply says the limit is unset instead of reporting zero left", () => {
  const reply = formatBalanceReply({ monthly_limit: null, spent: 120, currency: "EGP" });
  assertEquals(reply.includes("مش محدد"), true);
  assertEquals(reply.includes("120.00 EGP"), true);
});

Deno.test("the balance reply reports remaining, available and days left", () => {
  const reply = formatBalanceReply({
    monthly_limit: 1000, remaining: 1000, income: 1500, spent: 1500,
    committed: 0, available: 1000, days_left: 15, currency: "EGP",
  });
  assertEquals(reply.includes("رصيدك: 1000.00 EGP"), true);
  assertEquals(reply.includes("المتاح بعد خصم المحجوز"), true);
  assertEquals(reply.includes("فاضل 15 يوم"), true);
});

Deno.test("a null state is reported as unreadable, not as zero", () => {
  assertEquals(formatBalanceReply(null).includes("مقدرتش"), true);
});
