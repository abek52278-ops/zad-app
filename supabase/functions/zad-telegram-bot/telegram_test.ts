import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  normalizeBindingCode, parseDismissCallback, reasonForCode, memoryNoteForDismissal,
  formatBalanceMessage, formatTransactionsMessage, mainMenuKeyboard, dismissKeyboard,
  checkInKeyboard, parseCheckInCallback, checkInPromptMessage,
  confirmToolKeyboard, parseToolCallback,
  transactionProposalKeyboard, parseTransactionProposalCallback,
  notificationReviewMessage,
  adCreditKeyboard,
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

Deno.test("formatBalanceMessage leads with the ledger balance and shows how it was reached", () => {
  // These numbers arrive from zad_budget_state(), already cycle-aware. Since the ledger
  // migration `remaining` is the balance itself — 1000 opened + 50 income - 300 spent —
  // so it leads, and the three terms behind it are shown so the customer can check the
  // arithmetic themselves. المتاح still follows, because obligations are still reserved.
  const msg = formatBalanceMessage({
    monthly_limit: 1000, spent: 300, income: 50, remaining: 750, committed: 600, available: 150, days_left: 12,
  }, "ر.س");
  assert(msg.includes("رصيدك: 750.00 ر.س"));
  assert(msg.includes("بدأت الدورة بـ 1000.00 ر.س"));
  assert(msg.includes("دخل: 50.00 ر.س"));
  assert(msg.includes("مصروف: 300.00 ر.س"));
  assert(msg.includes("المتاح بعد خصم المحجوز (600.00 ر.س): 150.00 ر.س"));
  assert(msg.includes("فاضل 12 يوم"));
});

Deno.test("formatBalanceMessage says the balance is unset instead of reporting zero left", () => {
  const msg = formatBalanceMessage({
    monthly_limit: null, spent: 300, income: 0, remaining: null, committed: 0, available: null, days_left: 12,
  }, "ر.س");
  assert(msg.includes("رصيدك لسه مش محدد"));
  assert(msg.includes("مصروف الدورة دي: 300.00 ر.س"));
  // No balance line at all, rather than a balance line reading zero.
  assert(!msg.includes("رصيدك: "));
  assert(!msg.includes("المتاح"));
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


// ── tool-confirm callbacks ──────────────────────────────────────────────────
// الطابور التالت (بعد الفلوس والدوا). أهم اختبار فيهم هو التمييز: "x:"/"mx:"/"tx:"
// لازم يفضلوا منفصلين، لأن راوتر واحد بيقرا التلاتة وأي تداخل معناه إن تأكيد أداة
// بيروح لجدول المعاملات المالية.

Deno.test("parseToolCallback parses a confirm callback", () => {
  const uuid = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  assertEquals(parseToolCallback(`tx:${uuid}`), { action: "confirm", pendingId: uuid });
});

Deno.test("parseToolCallback parses a cancel callback", () => {
  const uuid = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  assertEquals(parseToolCallback(`tc:${uuid}`), { action: "cancel", pendingId: uuid });
});

Deno.test("parseToolCallback rejects the spend and medication prefixes", () => {
  const uuid = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  assertEquals(parseToolCallback(`x:${uuid}`), null);
  assertEquals(parseToolCallback(`c:${uuid}`), null);
  assertEquals(parseToolCallback(`mx:${uuid}`), null);
  assertEquals(parseToolCallback(`mc:${uuid}`), null);
});

Deno.test("parseToolCallback rejects a non-uuid payload", () => {
  assertEquals(parseToolCallback("tx:not-a-uuid"), null);
  assertEquals(parseToolCallback("tx:"), null);
  assertEquals(parseToolCallback("tx"), null);
  assertEquals(parseToolCallback("tx:a:b"), null);
});

Deno.test("confirmToolKeyboard stays inside Telegram's 64-byte callback_data cap", () => {
  const uuid = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  const rows = confirmToolKeyboard(uuid);
  const buttons = rows.flat();
  assertEquals(buttons.length, 2);
  for (const b of buttons) {
    assert(new TextEncoder().encode(b.callback_data).length <= 64);
  }
  // والأهم: الرد بيرجع مفكوك لنفس الـ id، فالزرار والراوتر متفقين.
  assertEquals(parseToolCallback(buttons[0].callback_data ?? "")?.action, "confirm");
  assertEquals(parseToolCallback(buttons[1].callback_data ?? "")?.action, "cancel");
});

// ── shared bank-transaction proposals ──────────────────────────────────────

Deno.test("parseTransactionProposalCallback maps every proposal decision", () => {
  const id = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  assertEquals(parseTransactionProposalCallback(`pc:${id}`), { decision: "confirm", proposalId: id });
  assertEquals(parseTransactionProposalCallback(`pr:${id}`), { decision: "reject", proposalId: id });
  assertEquals(parseTransactionProposalCallback(`pe:${id}`), { decision: "expense", proposalId: id });
  assertEquals(parseTransactionProposalCallback(`pi:${id}`), { decision: "income", proposalId: id });
  assertEquals(parseTransactionProposalCallback(`pt:${id}`), { decision: "transfer", proposalId: id });
});

Deno.test("parseTransactionProposalCallback rejects malformed and unrelated callbacks", () => {
  assertEquals(parseTransactionProposalCallback("pc:not-a-uuid"), null);
  assertEquals(parseTransactionProposalCallback("x:3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607"), null);
  assertEquals(parseTransactionProposalCallback("pc:"), null);
});

Deno.test("classification proposal keyboard is complete and within Telegram callback limit", () => {
  const id = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  const buttons = transactionProposalKeyboard(id, "needs_classification").flat();
  assertEquals(buttons.map((button) => parseTransactionProposalCallback(button.callback_data ?? "")?.decision), [
    "expense", "income", "transfer", "reject",
  ]);
  for (const button of buttons) {
    assert(new TextEncoder().encode(button.callback_data).length <= 64);
  }
});

Deno.test("confirmation proposal keyboard offers confirmation, rejection, and corrections", () => {
  const id = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f607";
  const decisions = transactionProposalKeyboard(id, "awaiting_confirmation", "expense")
    .flat()
    .map((button) => parseTransactionProposalCallback(button.callback_data ?? "")?.decision);
  assertEquals(decisions, ["confirm", "reject", "income", "transfer"]);
});

Deno.test("notification review message asks for amount and direction without claiming a write", () => {
  const message = notificationReviewMessage({
    packageName: "com.bank.app",
    title: "حركة على البطاقة",
    body: "تمت عملية غير واضحة",
  });
  assert(message.includes("com.bank.app"));
  assert(message.includes("سحب 250"));
  assert(message.includes("إيداع 1000"));
  assert(message.includes("للتأكيد"));
  assert(!message.includes("اتسجلت"));
});

// الزر ده لازم يفضل زر رابط. لو رجع callback_data بدل url، تليجرام هيبعت
// callback للبوت والعميل مش هيتنقل للتطبيق أصلاً.
Deno.test("adCreditKeyboard is a single link button, not a callback button", () => {
  const rows = adCreditKeyboard();
  assertEquals(rows.length, 1);
  assertEquals(rows[0].length, 1);
  const button = rows[0][0];
  assert(button.url !== undefined, "ad credit button must carry a url");
  assertEquals(button.callback_data, undefined);
  // تليجرام بيرفض السكيمات المخصصة (zad://) في أزرار الروابط، فلازم https.
  assert(button.url!.startsWith("https://"), `expected https url, got ${button.url}`);
});
