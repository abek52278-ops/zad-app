import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  normalizeBindingCode, parseDismissCallback, reasonForCode, memoryNoteForDismissal,
  formatBalanceMessage, formatTransactionsMessage, mainMenuKeyboard, dismissKeyboard,
  checkInKeyboard, parseCheckInCallback, checkInPromptMessage,
} from "./telegram.ts";

Deno.test("normalizeBindingCode uppercases a valid code", () => {
  assertEquals(normalizeBindingCode("abcd1234"), "ABCD1234");
});

Deno.test("normalizeBindingCode accepts an already-uppercase code", () => {
  assertEquals(normalizeBindingCode("ABCD1234"), "ABCD1234");
});

Deno.test("normalizeBindingCode returns null for an empty/undefined arg", () => {
  assertEquals(normalizeBindingCode(undefined), null);
  assertEquals(normalizeBindingCode(""), null);
  assertEquals(normalizeBindingCode("   "), null);
});

Deno.test("normalizeBindingCode returns null for a too-short code", () => {
  assertEquals(normalizeBindingCode("abc"), null);
});

Deno.test("normalizeBindingCode returns null for non-alphanumeric input (e.g. someone pasting a sentence)", () => {
  assertEquals(normalizeBindingCode("أهلاً"), null);
});

Deno.test("parseDismissCallback parses a well-formed dismiss callback", () => {
  const parsed = parseDismissCallback("d:abc-123:w");
  assertEquals(parsed, { insightId: "abc-123", reasonCode: "w" });
});

Deno.test("parseDismissCallback rejects a non-dismiss callback", () => {
  assertEquals(parseDismissCallback("b"), null);
});

Deno.test("parseDismissCallback rejects malformed data", () => {
  assertEquals(parseDismissCallback("d:onlyonepart"), null);
});

Deno.test("reasonForCode maps all three known codes", () => {
  assertEquals(reasonForCode("n"), "not_relevant");
  assertEquals(reasonForCode("w"), "wrong_data");
  assertEquals(reasonForCode("t"), "timing");
});

Deno.test("reasonForCode returns null for an unknown code", () => {
  assertEquals(reasonForCode("x"), null);
});

Deno.test("memoryNoteForDismissal gives wrong_data its own scope and the highest confidence", () => {
  // Mirrors DismissalMemoryTest.kt's client-side assertion — same rule, same reason
  // (Task 28's "free bug report" signal must not blend into generic dismissal noise).
  const wrongData = memoryNoteForDismissal("wrong_data", "x")!;
  const notRelevant = memoryNoteForDismissal("not_relevant", "x")!;
  const timing = memoryNoteForDismissal("timing", "x")!;
  assertEquals(wrongData.scope, "data_quality");
  assert(wrongData.confidence > notRelevant.confidence);
  assert(wrongData.confidence > timing.confidence);
});

Deno.test("memoryNoteForDismissal returns null for an unknown reason", () => {
  assertEquals(memoryNoteForDismissal("snoozed", "x"), null);
});

Deno.test("formatBalanceMessage leads with available, not remaining", () => {
  // Phase 0: these numbers arrive from zad_budget_state(), already cycle-aware and already
  // net of fixed obligations. The customer's actionable figure is المتاح (150), not المتبقي
  // (750) — showing the larger number first is what made the app and the bot feel like two
  // different products quoting two different balances.
  const msg = formatBalanceMessage({
    monthly_limit: 1000, spent: 300, remaining: 750, committed: 600, available: 150, days_left: 12,
  }, "ر.س");
  assert(msg.includes("المتاح الفعلي: 150.00 ر.س"));
  assert(msg.includes("المتبقي قبل خصم الالتزامات: 750.00 ر.س"));
  assert(msg.includes("فاضل 12 يوم"));
});

Deno.test("formatBalanceMessage says the ceiling is unset instead of reporting zero left", () => {
  const msg = formatBalanceMessage({
    monthly_limit: null, spent: 300, remaining: null, committed: 0, available: null, days_left: 12,
  }, "ر.س");
  assert(msg.includes("الميزانية الشهرية مش محددة"));
  assert(msg.includes("مصروف الدورة دي: 300.00 ر.س"));
  // No balance line at all, rather than a balance line reading zero.
  assert(!msg.includes("المتاح الفعلي"));
  assert(!msg.includes("المتبقي"));
});

Deno.test("formatTransactionsMessage reports the empty case in Arabic instead of a blank message", () => {
  assertEquals(formatTransactionsMessage([]), "مفيش معاملات مسجلة لسه.");
});

Deno.test("formatTransactionsMessage signs expenses and income differently", () => {
  const msg = formatTransactionsMessage([
    { title: "قهوة", amount: 25, txn_kind: "expense", created_at: "2026-07-30T10:00:00Z" },
    { title: "راتب", amount: 5000, txn_kind: "income", created_at: "2026-07-01T10:00:00Z" },
  ]);
  assert(msg.includes("-25.00"));
  assert(msg.includes("+5000.00"));
});

Deno.test("mainMenuKeyboard has exactly the three read-only v1 options", () => {
  const kb = mainMenuKeyboard();
  assertEquals(kb.length, 3);
  assertEquals(kb.flat().map((b) => b.callback_data).sort(), ["b", "i", "t"]);
});

Deno.test("dismissKeyboard encodes the insight id and all three reason codes", () => {
  const kb = dismissKeyboard("insight-1");
  const codes = kb[0].map((b) => b.callback_data);
  assertEquals(codes.sort(), ["d:insight-1:n", "d:insight-1:t", "d:insight-1:w"]);
});

Deno.test("checkInKeyboard encodes the prompt id with y/n suffixes", () => {
  const kb = checkInKeyboard("11111111-1111-1111-1111-111111111111");
  const codes = kb[0].map((b) => b.callback_data);
  assertEquals(codes, [
    "ck:11111111-1111-1111-1111-111111111111:y",
    "ck:11111111-1111-1111-1111-111111111111:n",
  ]);
});

Deno.test("parseCheckInCallback parses a well-formed still-in-stock callback", () => {
  const parsed = parseCheckInCallback("ck:11111111-1111-1111-1111-111111111111:y");
  assertEquals(parsed, { promptId: "11111111-1111-1111-1111-111111111111", stillInStock: true });
});

Deno.test("parseCheckInCallback parses a well-formed finished callback", () => {
  const parsed = parseCheckInCallback("ck:11111111-1111-1111-1111-111111111111:n");
  assertEquals(parsed, { promptId: "11111111-1111-1111-1111-111111111111", stillInStock: false });
});

Deno.test("parseCheckInCallback rejects a non-checkin callback", () => {
  assertEquals(parseCheckInCallback("d:insight-1:n"), null);
});

Deno.test("parseCheckInCallback rejects a malformed uuid", () => {
  assertEquals(parseCheckInCallback("ck:not-a-uuid:y"), null);
});

Deno.test("parseCheckInCallback rejects an unknown answer letter", () => {
  assertEquals(parseCheckInCallback("ck:11111111-1111-1111-1111-111111111111:z"), null);
});

Deno.test("checkInPromptMessage includes the item name", () => {
  assert(checkInPromptMessage("لبن").includes("لبن"));
});
