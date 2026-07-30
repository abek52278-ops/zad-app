import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  normalizeBindingCode, parseDismissCallback, reasonForCode, memoryNoteForDismissal,
  formatBalanceMessage, formatTransactionsMessage, mainMenuKeyboard, dismissKeyboard,
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

Deno.test("formatBalanceMessage computes remaining as budget minus spent plus income", () => {
  const msg = formatBalanceMessage(1000, 300, 50);
  assert(msg.includes("750.00"));
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
