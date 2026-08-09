// Task 16.4 — the 12 required tests. 1-7 and 9 are pure validator tests (no network,
// no DB, per the spec: "validators... are pure functions given a snapshot"). 10 tests
// retry/backoff with an injected fake fetch. 11 and 12 test the pure decision functions
// extracted for exactly this reason (shared.ts) since the real handler needs a live DB.
// 8 (near-duplicate memory upsert) is a live-Postgres trigram behavior — verified
// directly against the deployed zad_memory_upsert() function via execute_sql, not here;
// see the Task 8/16 execution report for that evidence.
//
// All validator calls are awaited even though the current implementations are
// synchronous — the Validator type allows Promise<Validation>, and await on a plain
// value just resolves immediately, so this matches how validateTool actually calls them.

import { assert, assertEquals, assertRejects, assertStringIncludes } from "jsr:@std/assert@1";
import {
  CONFIRM_REQUIRED_TOOLS,
  MUTATING_TOOLS,
  VALIDATORS,
  freshContext,
  validateLogPharmacyDose,
  validateDeletePharmacyItem,
  validateScheduleTask,
  validateAddInventoryItem,
  validateAddPharmacyItem,
  validateAddShoppingItem,
  validateLogTransaction,
  validateQueryFamily,
  validateSetMarket,
  validateSetMonthlyLimit,
  validateUpdateTransaction,
  validateAskUser,
  validateConfirmCycleStart,
  validateConfirmObligation,
  validateEmitInsight,
  validateReconcileCashBalance,
  validateRemember,
  validateSetTransactionCategory,
  validateSuggestBudgetChange,
  validateTool,
  validateUpdateInventoryQty,
} from "./validators.ts";
import { callModelWithRetry } from "./retry.ts";
import { decideOnBrainFailure, hasRecentMutatingRun } from "./shared.ts";

const healthySnapshot = {
  budget: 3000, spent: 500, remaining: 2500, velocity: 0.4,
  stock: [{ name: "تونة", qty: 10, daysLeft: 20, rateKnown: true }],
  stock_unknown: [], upcoming: [], dismissed_keys: [],
  distinct_categories: ["البقالة", "المطاعم"], shopping_list_pending: [],
};

function assertRejected(v: { ok: true } | { ok: false; reason: string }): asserts v is { ok: false; reason: string } {
  assert(!v.ok, "expected validation to fail");
}

// 1. Negative quantity → rejected with a message containing "بالسالب"
Deno.test("update_inventory_qty rejects a negative quantity", async () => {
  const v = await validateUpdateInventoryQty({ item_name: "تونة", new_qty: -5, reason: "العميل قال خلصت" }, healthySnapshot, freshContext("u1"));
  assertRejected(v);
  assertStringIncludes(v.reason, "بالسالب");
});

// 2. priority: critical with a healthy snapshot → rejected
Deno.test("emit_insight rejects critical priority when nothing in the snapshot justifies it", async () => {
  const v = await validateEmitInsight(
    { title: "خطر!", body: "صرفت 500 جنيه", dedupe_key: "test_critical", priority: "critical", surface: "home_card" },
    healthySnapshot, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "critical");
});

// 3. surface: voice with priority: normal → rejected
Deno.test("emit_insight rejects voice surface unless priority is critical", async () => {
  const v = await validateEmitInsight(
    { title: "تنبيه", body: "صرفت 50 جنيه", dedupe_key: "test_voice", priority: "normal", surface: "voice" },
    healthySnapshot, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "الصوت");
});

// 4. A body with no digits → rejected
Deno.test("emit_insight rejects a body with no number in it", async () => {
  const v = await validateEmitInsight(
    { title: "تنبيه", body: "صرفت فلوس كتير قوي", dedupe_key: "test_no_digit", priority: "normal", surface: "home_card" },
    healthySnapshot, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "رقم");
});

// 5. Fourth emit_insight in one run → rejected with the cap message
Deno.test("emit_insight rejects the fourth insight in one run", async () => {
  const ctx = freshContext("u1");
  ctx.insightCount = 3;
  const v = await validateEmitInsight(
    { title: "تنبيه", body: "صرفت 20 جنيه", dedupe_key: "test_fourth", priority: "normal", surface: "home_card" },
    healthySnapshot, ctx,
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "٣ رؤى");
});

// 6. A category not in the user's set → rejected, message lists valid categories
Deno.test("set_transaction_category rejects an invented category and lists valid ones", async () => {
  const v = await validateSetTransactionCategory({ category: "تسلية" }, healthySnapshot, freshContext("u1"));
  assertRejected(v);
  assertStringIncludes(v.reason, "البقالة");
  assertStringIncludes(v.reason, "المطاعم");
});

// 7. new_budget at 10x current → rejected
Deno.test("suggest_budget_change rejects a suggestion 10x the current budget", async () => {
  const v = await validateSuggestBudgetChange({ new_budget: 30000, reason: "زيادة كبيرة" }, healthySnapshot, freshContext("u1"));
  assertRejected(v);
  assertStringIncludes(v.reason, "بعيد جداً");
});

// 9. Sixth mutation in a run → rejected (global mutation cap, enforced in validateTool
// itself since it applies across multiple mutation-shaped tools, not one validator)
Deno.test("validateTool rejects the sixth mutation in a run via the global cap", async () => {
  const ctx = freshContext("u1");
  ctx.mutationCount = 5;
  const v = await validateTool("update_inventory_qty", { item_name: "تونة", new_qty: 3, reason: "تصحيح بعد الجرد" }, healthySnapshot, ctx);
  assertRejected(v);
  assertStringIncludes(v.reason, "الحد الأقصى");
});

// 10a. 429 then 200 → succeeds after one backoff
Deno.test("callModelWithRetry succeeds after one 429 retry", async () => {
  let calls = 0;
  const fakeFetch = (() => {
    calls++;
    if (calls === 1) {
      return Promise.resolve(new Response("rate limited", { status: 429, headers: { "retry-after": "0" } }));
    }
    return Promise.resolve(new Response(JSON.stringify({ ok: true }), { status: 200 }));
  }) as typeof fetch;
  let slept = -1;
  const result = await callModelWithRetry({}, {
    url: "https://example.test/api", headers: { "Authorization": "Bearer test" },
    fetchFn: fakeFetch, sleepFn: (ms) => { slept = ms; return Promise.resolve(); },
  });
  assertEquals(result.ok, true);
  assertEquals(calls, 2);
  assert(slept >= 0);
});

// 10b. 401 → throws immediately, no retry
Deno.test("callModelWithRetry throws immediately on 401 without retrying", async () => {
  let calls = 0;
  const fakeFetch = (() => {
    calls++;
    return Promise.resolve(new Response("unauthorized", { status: 401 }));
  }) as typeof fetch;
  await assertRejects(
    () => callModelWithRetry({}, { url: "https://example.test/api", headers: { "Authorization": "Bearer bad" }, fetchFn: fakeFetch, sleepFn: () => Promise.resolve() }),
  );
  assertEquals(calls, 1);
});

// 11. Exhausted retries → decideOnBrainFailure says to queue (non-chat) and never a 500
Deno.test("decideOnBrainFailure queues for daily/event triggers and never returns a 500", () => {
  const daily = decideOnBrainFailure("daily");
  assert(daily.shouldQueue);
  assert(daily.status !== 500);

  const event = decideOnBrainFailure("event");
  assert(event.shouldQueue);
  assert(event.status !== 500);
});

// 11b. chat does not queue silently — gets a real message instead
Deno.test("decideOnBrainFailure does not queue chat and returns a real message", () => {
  const chat = decideOnBrainFailure("chat");
  assertEquals(chat.shouldQueue, false);
  assert(chat.status !== 500);
  assertStringIncludes(String(chat.body.message), "مش قادر");
});

// 12. A second daily run within 12h of a run with mutations → skipped
Deno.test("hasRecentMutatingRun detects a prior run that actually changed data", () => {
  assertEquals(hasRecentMutatingRun([{ mutations: [] }, { mutations: [{ tool: "x" }] }]), true);
  assertEquals(hasRecentMutatingRun([{ mutations: [] }]), false);
  assertEquals(hasRecentMutatingRun([]), false);
});

// ─── Extra coverage beyond the required 12, cheap to keep ───────────────────

Deno.test("ask_user rejects a second question in the same run", async () => {
  const ctx = freshContext("u1");
  ctx.counts["ask_user"] = 1;
  const v = await validateAskUser({ title: "?", body: "?", dedupe_key: "q2", answer_type: "yes_no" }, healthySnapshot, ctx);
  assertRejected(v);
});

// ── Task 18 cooldown (acceptance #4 and #5) ────────────────────────────────────
// These are the guard against Fault B: an item needs four observations before
// samples>=3, and without a cooldown the brain re-asks about it on every daily run
// in the meantime — the "asks and forgets" behaviour the user originally reported.

Deno.test("ask_user rejects the same about_item asked within 72 hours", async () => {
  const snap = { ...healthySnapshot, stock_unknown: ["بيض"], asked_recently: ["بيض"] };
  const v = await validateAskUser(
    { title: "البيض", body: "كام؟", dedupe_key: "ask_eggs_qty", answer_type: "number", about_item: "بيض" },
    snap, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "٣ أيام");
});

Deno.test("ask_user rejects an item whose consumption rate is already known", async () => {
  const snap = { ...healthySnapshot, stock_unknown: ["بيض"], rate_known_items: ["بيض"] };
  const v = await validateAskUser(
    { title: "البيض", body: "كام؟", dedupe_key: "ask_eggs_qty", answer_type: "number", about_item: "بيض" },
    snap, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "المعدل معروف");
});

Deno.test("ask_user still allows a fresh unknown-rate item", async () => {
  const snap = { ...healthySnapshot, stock_unknown: ["بيض"], asked_recently: ["لبن"], rate_known_items: [] };
  const v = await validateAskUser(
    { title: "البيض", body: "كام؟", dedupe_key: "ask_eggs_qty", answer_type: "number", about_item: "بيض" },
    snap, freshContext("u1"),
  );
  assertEquals(v.ok, true);
});

Deno.test("add_shopping_item rejects an item already pending", async () => {
  const snap = { ...healthySnapshot, shopping_list_pending: ["لبن"] };
  const v = await validateAddShoppingItem({ item_name: "لبن", quantity: 2 }, snap, freshContext("u1"));
  assertRejected(v);
});

Deno.test("remember rejects a note shorter than 10 characters", async () => {
  const v = await validateRemember({ note: "قصيرة" }, healthySnapshot, freshContext("u1"));
  assertRejected(v);
});

// Live-bug regression (2026-07-25): model sent confidence:"medium" (string) — validator
// let it through, Postgres rejected it at the RPC boundary since p_conf is real. Caught
// live via the same test message/user from the Task 16 session; validator must reject
// this before it ever reaches the DB, not after.
Deno.test("remember rejects a non-numeric confidence value", async () => {
  const v = await validateRemember({ note: "صرفت كتير على المطاعم الشهر ده", confidence: "medium" }, healthySnapshot, freshContext("u1"));
  assertRejected(v);
  assertStringIncludes(v.reason, "confidence");
});

// ── Task 19.5: weekly cash reconciliation ──────────────────────────────────────

const cashSnapshot = {
  ...healthySnapshot,
  cash_reconciliation: { key: "cash_reconciliation_2026_w30", cash_on_hand: 700, needs_ask: true, dismissed_count: 0 },
};

Deno.test("ask_user allows the cash reconciliation question with the exact snapshot key", async () => {
  const v = await validateAskUser(
    { title: "الكاش", body: "فاضل معاك كام؟", dedupe_key: "cash_reconciliation_2026_w30", answer_type: "number" },
    cashSnapshot, freshContext("u1"),
  );
  assertEquals(v.ok, true);
});

Deno.test("ask_user rejects an invented cash_reconciliation key not matching the snapshot", async () => {
  const v = await validateAskUser(
    { title: "الكاش", body: "فاضل معاك كام؟", dedupe_key: "cash_reconciliation_made_up", answer_type: "number" },
    cashSnapshot, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "متخترعش");
});

Deno.test("ask_user rejects cash reconciliation after two dismissals — permanent stop", async () => {
  const snap = { ...cashSnapshot, cash_reconciliation: { ...cashSnapshot.cash_reconciliation, dismissed_count: 2 } };
  const v = await validateAskUser(
    { title: "الكاش", body: "فاضل معاك كام؟", dedupe_key: "cash_reconciliation_2026_w30", answer_type: "number" },
    snap, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "رفض");
});

Deno.test("ask_user rejects cash reconciliation already asked this week", async () => {
  const snap = { ...cashSnapshot, cash_reconciliation: { ...cashSnapshot.cash_reconciliation, needs_ask: false } };
  const v = await validateAskUser(
    { title: "الكاش", body: "فاضل معاك كام؟", dedupe_key: "cash_reconciliation_2026_w30", answer_type: "number" },
    snap, freshContext("u1"),
  );
  assertRejected(v);
});

Deno.test("reconcile_cash_balance rejects a negative reported amount", async () => {
  const v = await validateReconcileCashBalance({ reported_amount: -50 }, cashSnapshot, freshContext("u1"));
  assertRejected(v);
});

Deno.test("reconcile_cash_balance rejects a second call in the same run", async () => {
  const ctx = freshContext("u1");
  ctx.counts["reconcile_cash_balance"] = 1;
  const v = await validateReconcileCashBalance({ reported_amount: 500 }, cashSnapshot, ctx);
  assertRejected(v);
});

Deno.test("reconcile_cash_balance allows a plain non-negative number", async () => {
  const v = await validateReconcileCashBalance({ reported_amount: 500 }, cashSnapshot, freshContext("u1"));
  assertEquals(v.ok, true);
});

// ── Task 25: salary cycle detection/confirmation ────────────────────────────────

const cycleSnapshot = {
  ...healthySnapshot,
  cycle_detection: { needs_ask: true, suggested_day: 28, dedupe_key: "cycle_start_confirm_28" },
};

Deno.test("ask_user allows the cycle-start question with the exact snapshot key", async () => {
  const v = await validateAskUser(
    { title: "دورة الراتب", body: "راتبك بيجي يوم ٢٨؟", dedupe_key: "cycle_start_confirm_28", answer_type: "yes_no" },
    cycleSnapshot, freshContext("u1"),
  );
  assertEquals(v.ok, true);
});

Deno.test("ask_user rejects an invented cycle_start_confirm key not matching the snapshot", async () => {
  const v = await validateAskUser(
    { title: "دورة الراتب", body: "راتبك بيجي يوم ١؟", dedupe_key: "cycle_start_confirm_1", answer_type: "yes_no" },
    cycleSnapshot, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "متخترعش");
});

Deno.test("ask_user rejects cycle-start question once already confirmed/asked", async () => {
  const snap = { ...cycleSnapshot, cycle_detection: { ...cycleSnapshot.cycle_detection, needs_ask: false } };
  const v = await validateAskUser(
    { title: "دورة الراتب", body: "راتبك بيجي يوم ٢٨؟", dedupe_key: "cycle_start_confirm_28", answer_type: "yes_no" },
    snap, freshContext("u1"),
  );
  assertRejected(v);
});

Deno.test("confirm_cycle_start rejects a day that doesn't match the snapshot's suggested_day", async () => {
  const v = await validateConfirmCycleStart({ cycle_start_day: 15 }, cycleSnapshot, freshContext("u1"));
  assertRejected(v);
  assertStringIncludes(v.reason, "متخترعش");
});

Deno.test("confirm_cycle_start rejects an out-of-range day", async () => {
  const v = await validateConfirmCycleStart({ cycle_start_day: 45 }, cycleSnapshot, freshContext("u1"));
  assertRejected(v);
});

Deno.test("confirm_cycle_start allows the exact suggested_day from the snapshot", async () => {
  const v = await validateConfirmCycleStart({ cycle_start_day: 28 }, cycleSnapshot, freshContext("u1"));
  assertEquals(v.ok, true);
});

Deno.test("confirm_cycle_start rejects a second call in the same run", async () => {
  const ctx = freshContext("u1");
  ctx.counts["confirm_cycle_start"] = 1;
  const v = await validateConfirmCycleStart({ cycle_start_day: 28 }, cycleSnapshot, ctx);
  assertRejected(v);
});

// ── Task 26: committed obligations / "available" ────────────────────────────────

const obligationSnapshot = {
  ...healthySnapshot,
  available: 1500,
  obligation_detection: {
    needs_ask: true, title: "مالك العقار", amount: 3500, due_day: 5,
    dedupe_key: "obligation_confirm_abc123",
  },
};

Deno.test("ask_user allows the obligation question with the exact snapshot key", async () => {
  const v = await validateAskUser(
    { title: "التزام", body: "٣٥٠٠ كل شهر لمالك العقار — إيجار؟", dedupe_key: "obligation_confirm_abc123", answer_type: "yes_no" },
    obligationSnapshot, freshContext("u1"),
  );
  assertEquals(v.ok, true);
});

Deno.test("ask_user rejects an invented obligation_confirm key not matching the snapshot", async () => {
  const v = await validateAskUser(
    { title: "التزام", body: "٣٥٠٠ كل شهر — إيجار؟", dedupe_key: "obligation_confirm_madeup", answer_type: "yes_no" },
    obligationSnapshot, freshContext("u1"),
  );
  assertRejected(v);
  assertStringIncludes(v.reason, "متخترعش");
});

Deno.test("ask_user rejects obligation question once already asked/confirmed", async () => {
  const snap = { ...obligationSnapshot, obligation_detection: { ...obligationSnapshot.obligation_detection, needs_ask: false } };
  const v = await validateAskUser(
    { title: "التزام", body: "٣٥٠٠ كل شهر — إيجار؟", dedupe_key: "obligation_confirm_abc123", answer_type: "yes_no" },
    snap, freshContext("u1"),
  );
  assertRejected(v);
});

Deno.test("confirm_obligation rejects an invalid kind", async () => {
  const v = await validateConfirmObligation({ kind: "vacation" }, obligationSnapshot, freshContext("u1"));
  assertRejected(v);
  assertStringIncludes(v.reason, "kind");
});

Deno.test("confirm_obligation rejects when nothing is pending detection in the snapshot", async () => {
  const snap = { ...healthySnapshot, obligation_detection: { needs_ask: false, title: null, amount: null, due_day: null, dedupe_key: null } };
  const v = await validateConfirmObligation({ kind: "rent" }, snap, freshContext("u1"));
  assertRejected(v);
});

Deno.test("confirm_obligation allows a valid kind while detection is pending", async () => {
  const v = await validateConfirmObligation({ kind: "rent" }, obligationSnapshot, freshContext("u1"));
  assertEquals(v.ok, true);
});

Deno.test("confirm_obligation rejects a second call in the same run", async () => {
  const ctx = freshContext("u1");
  ctx.counts["confirm_obligation"] = 1;
  const v = await validateConfirmObligation({ kind: "rent" }, obligationSnapshot, ctx);
  assertRejected(v);
});

Deno.test("emit_insight allows critical priority when available is zero even if remaining is still positive", async () => {
  const snap = { ...healthySnapshot, remaining: 500, available: 0 };
  const v = await validateEmitInsight(
    { title: "خطر!", body: "المتاح وصل لـ 0 جنيه", dedupe_key: "test_available_critical", priority: "critical", surface: "home_card" },
    snap, freshContext("u1"),
  );
  assertEquals(v.ok, true);
});

// ════════════════════════════════════════════════════════════════════════════
// المرحلة ٢-ب — أدوات المحادثة.
//
// الحارس الأهم اللي بتغطيه الاختبارات دي: أدوات الفلوس التلاتة موجودة في
// CONFIRM_REQUIRED_TOOLS، يعني حلقة agent_turn مابتنفذهاش أبداً — بتحوّلها لاقتراح
// مستني تأكيد. لو حد شال أداة من القايمة دي بالغلط، الكتابة على دفتر العميل هتحصل من
// غير موافقته، والاختبار ده هو اللي بيمسك الحالة دي.
// ════════════════════════════════════════════════════════════════════════════

Deno.test("every money-writing tool stays behind explicit confirmation", () => {
  for (const tool of ["log_transaction", "update_transaction", "set_monthly_limit"]) {
    assert(
      CONFIRM_REQUIRED_TOOLS.includes(tool),
      `${tool} بيكتب على فلوس حقيقية ولازم يفضل ورا تأكيد صريح`,
    );
  }
});

Deno.test("inventory and pharmacy stay direct-write, matching the existing risk split", () => {
  for (const tool of ["add_inventory_item", "add_pharmacy_item", "update_inventory_qty", "set_market"]) {
    assert(!CONFIRM_REQUIRED_TOOLS.includes(tool), `${tool} المفروض يفضل كتابة مباشرة`);
  }
});

Deno.test("every confirm-required tool is also counted as a mutation", () => {
  for (const tool of CONFIRM_REQUIRED_TOOLS) {
    assert(MUTATING_TOOLS.includes(tool), `${tool} لازم يتحسب في سقف التعديلات`);
  }
});

// ── log_transaction ─────────────────────────────────────────────────────────

Deno.test("log_transaction accepts a well-formed expense", async () => {
  const v = await validateLogTransaction(
    { amount: 50, txn_kind: "expense", title: "بقالة", category: "بقالة" }, {}, freshContext("u"),
  );
  assertEquals(v.ok, true);
});

Deno.test("log_transaction rejects non-positive, absurd, and non-numeric amounts", async () => {
  for (const amount of [0, -20, 5_000_000, "خمسين", null, NaN]) {
    const v = await validateLogTransaction(
      { amount, txn_kind: "expense", title: "x" }, {}, freshContext("u"),
    );
    assertEquals(v.ok, false, `amount=${amount}`);
  }
});

Deno.test("log_transaction rejects an unknown txn_kind", async () => {
  const v = await validateLogTransaction(
    { amount: 50, txn_kind: "transfer", title: "x" }, {}, freshContext("u"),
  );
  assertEquals(v.ok, false);
});

Deno.test("log_transaction rejects a blank title", async () => {
  const v = await validateLogTransaction(
    { amount: 50, txn_kind: "expense", title: "   " }, {}, freshContext("u"),
  );
  assertEquals(v.ok, false);
});

Deno.test("log_transaction caps how many transactions one turn can propose", async () => {
  const ctx = freshContext("u");
  ctx.counts["log_transaction"] = 5;
  const v = await validateLogTransaction({ amount: 50, txn_kind: "expense", title: "x" }, {}, ctx);
  assertEquals(v.ok, false);
});

// ── update_transaction ──────────────────────────────────────────────────────

const snapWithTx = { recent_transaction_ids: ["tx-1", "tx-2"] };

Deno.test("update_transaction accepts a known id with a real change", async () => {
  const v = await validateUpdateTransaction({ transaction_id: "tx-1", amount: 120 }, snapWithTx, freshContext("u"));
  assertEquals(v.ok, true);
});

Deno.test("update_transaction rejects an id that is not in the customer's snapshot", async () => {
  // ده الحارس اللي بيمنع الموديل يخترع معرّف — أو يمس معاملة عميل تاني.
  const v = await validateUpdateTransaction({ transaction_id: "tx-999", amount: 120 }, snapWithTx, freshContext("u"));
  assertEquals(v.ok, false);
});

Deno.test("update_transaction rejects a call that changes nothing", async () => {
  const v = await validateUpdateTransaction({ transaction_id: "tx-1" }, snapWithTx, freshContext("u"));
  assertEquals(v.ok, false);
});

Deno.test("update_transaction rejects a missing id outright", async () => {
  const v = await validateUpdateTransaction({ amount: 120 }, snapWithTx, freshContext("u"));
  assertEquals(v.ok, false);
});

// ── set_monthly_limit ───────────────────────────────────────────────────────

Deno.test("set_monthly_limit accepts a positive ceiling once per turn", async () => {
  const ctx = freshContext("u");
  assertEquals((await validateSetMonthlyLimit({ monthly_limit: 20000 }, {}, ctx)).ok, true);
  ctx.counts["set_monthly_limit"] = 1;
  assertEquals((await validateSetMonthlyLimit({ monthly_limit: 20000 }, {}, ctx)).ok, false);
});

Deno.test("set_monthly_limit rejects zero, negative, and absurd ceilings", async () => {
  for (const monthly_limit of [0, -100, 200_000_000, "كتير"]) {
    assertEquals((await validateSetMonthlyLimit({ monthly_limit }, {}, freshContext("u"))).ok, false);
  }
});

// ── add_inventory_item ──────────────────────────────────────────────────────

const snapWithStock = { stock: [{ name: "لبن", qty: 3 }] };

Deno.test("add_inventory_item accepts a genuinely new item", async () => {
  const v = await validateAddInventoryItem({ item_name: "فراخ", quantity: 2 }, snapWithStock, freshContext("u"));
  assertEquals(v.ok, true);
});

Deno.test("add_inventory_item refuses an item that already exists", async () => {
  // الفصل ده هو اللي بيمنع "الإضافة" تدهس كمية صنف قايم بدل ما تزودها.
  const v = await validateAddInventoryItem({ item_name: "لبن", quantity: 2 }, snapWithStock, freshContext("u"));
  assertEquals(v.ok, false);
  assertStringIncludes((v as { reason: string }).reason, "update_inventory_qty");
});

Deno.test("add_inventory_item rejects a too-short name and an out-of-range quantity", async () => {
  assertEquals((await validateAddInventoryItem({ item_name: "ل", quantity: 1 }, snapWithStock, freshContext("u"))).ok, false);
  assertEquals((await validateAddInventoryItem({ item_name: "فراخ", quantity: 0 }, snapWithStock, freshContext("u"))).ok, false);
  assertEquals((await validateAddInventoryItem({ item_name: "فراخ", quantity: 1000 }, snapWithStock, freshContext("u"))).ok, false);
});

Deno.test("add_inventory_item allows a whole grocery run in one turn", async () => {
  // السلوك اللي البروتوكول القديم مكانش بيقدر عليه: أربع أصناف في رسالة واحدة.
  const ctx = freshContext("u");
  for (const item of ["فراخ", "لحمة", "طماطم", "مكرونة"]) {
    const v = await validateAddInventoryItem({ item_name: item, quantity: 2 }, snapWithStock, ctx);
    assertEquals(v.ok, true, item);
    ctx.counts["add_inventory_item"] = (ctx.counts["add_inventory_item"] ?? 0) + 1;
  }
});

// ── add_pharmacy_item ───────────────────────────────────────────────────────

Deno.test("add_pharmacy_item accepts a valid 24-hour schedule", async () => {
  const v = await validateAddPharmacyItem(
    { name: "كونكور", dose_times: "08:00,16:00,00:00", daily_dose_count: 3, unit: "قرص" }, {}, freshContext("u"),
  );
  assertEquals(v.ok, true);
});

Deno.test("add_pharmacy_item rejects 24:00 and other malformed times", async () => {
  for (const dose_times of ["24:00", "8:00", "08:60", "صباحاً"]) {
    const v = await validateAddPharmacyItem({ name: "دوا", dose_times }, {}, freshContext("u"));
    assertEquals(v.ok, false, dose_times);
  }
});

Deno.test("add_pharmacy_item rejects a dose count that disagrees with the schedule", async () => {
  // منبهات متكررة فعلية — العميل اللي اتقاله "٣ مرات" مايوصلوش منبهين.
  const v = await validateAddPharmacyItem(
    { name: "كونكور", dose_times: "08:00,20:00", daily_dose_count: 3 }, {}, freshContext("u"),
  );
  assertEquals(v.ok, false);
});

Deno.test("add_pharmacy_item rejects an unknown unit", async () => {
  const v = await validateAddPharmacyItem({ name: "دوا", unit: "زجاجة" }, {}, freshContext("u"));
  assertEquals(v.ok, false);
});

// ── set_market ──────────────────────────────────────────────────────────────

Deno.test("set_market accepts ISO country and currency codes", async () => {
  assertEquals((await validateSetMarket({ currency: "EGP", country: "EG" }, {}, freshContext("u"))).ok, true);
  assertEquals((await validateSetMarket({ currency: "SAR", country: "SA" }, {}, freshContext("u"))).ok, true);
});

Deno.test("set_market rejects free-text country and currency names", async () => {
  // "مصر" و"الجنيه" هما بالظبط اللي العميل بيكتبه — الموديل شغلته يترجمهم لأكواد،
  // والـ validator هو اللي بيضمن إنه عملها قبل ما حاجة تتكتب.
  assertEquals((await validateSetMarket({ currency: "الجنيه", country: "مصر" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateSetMarket({ currency: "egp", country: "eg" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateSetMarket({ currency: "EGP", country: "EGY" }, {}, freshContext("u"))).ok, false);
});

// ── query_family ────────────────────────────────────────────────────────────

Deno.test("query_family is read-only and never counts as a mutation", () => {
  assert(!MUTATING_TOOLS.includes("query_family"));
});

Deno.test("query_family stops repeating itself within one turn", async () => {
  const ctx = freshContext("u");
  assertEquals((await validateQueryFamily({}, {}, ctx)).ok, true);
  ctx.counts["query_family"] = 2;
  assertEquals((await validateQueryFamily({}, {}, ctx)).ok, false);
});

// ── the shared gate still applies to the new tools ──────────────────────────

Deno.test("validateTool applies the mutation cap to the new chat tools", async () => {
  const ctx = freshContext("u");
  ctx.mutationCount = 5;
  const v = await validateTool("add_inventory_item", { item_name: "فراخ", quantity: 1 }, snapWithStock, ctx);
  assertEquals(v.ok, false);
  assertStringIncludes((v as { reason: string }).reason, "الحد الأقصى");
});

Deno.test("validateTool aborts a new tool after three rejections", async () => {
  const ctx = freshContext("u");
  for (let i = 0; i < 3; i++) {
    await validateTool("set_market", { currency: "bad", country: "bad" }, {}, ctx);
  }
  const v = await validateTool("set_market", { currency: "EGP", country: "EG" }, {}, ctx);
  assertEquals(v.ok, false);
  assertStringIncludes((v as { reason: string }).reason, "اتوقفت");
});

// ════════════════════════════════════════════════════════════════════════════
// تغطية بروتوكول [[ACTION]] القديم.
//
// السبب إن الاختبار ده موجود: ZadViewModel.tryAgentTurn بيرجع true لأي رد، فالبروتوكول
// القديم مابيشتغلش خالص لما الوكيل ينجح. يعني أي عملية موجودة في البروتوكول القديم ومش
// موجودة كأداة هنا مش بتبقى "بتقع على المسار القديم" — بتضيع بالكامل والعميل ياخد رد
// كلام بدل تنفيذ. ده بالظبط اللي حصل مع pharmacy_dose قبل ما تتضاف log_pharmacy_dose.
// ════════════════════════════════════════════════════════════════════════════

Deno.test("every [[ACTION]] type has an equivalent chat tool", () => {
  // consume → update_inventory_qty، add → add_inventory_item،
  // add_pharmacy → add_pharmacy_item، pharmacy_dose → log_pharmacy_dose
  const equivalents: Record<string, string> = {
    consume: "update_inventory_qty",
    add: "add_inventory_item",
    add_pharmacy: "add_pharmacy_item",
    pharmacy_dose: "log_pharmacy_dose",
  };
  for (const [legacy, tool] of Object.entries(equivalents)) {
    assert(tool in VALIDATORS, `[[ACTION:${legacy}]] مالوش أداة مكافئة (${tool}) — العملية دي هتضيع`);
  }
});

Deno.test("log_pharmacy_dose rejects a name too short to match anything", async () => {
  assertEquals((await validateLogPharmacyDose({ name: "" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateLogPharmacyDose({ name: "ك" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateLogPharmacyDose({ name: "كونكور" }, {}, freshContext("u"))).ok, true);
});

Deno.test("log_pharmacy_dose counts as a mutation and is not confirm-gated", () => {
  // خصم جرعة تعديل حقيقي، بس مش فلوس — نفس تصنيف المخزون بالظبط.
  assert(MUTATING_TOOLS.includes("log_pharmacy_dose"));
  assert(!CONFIRM_REQUIRED_TOOLS.includes("log_pharmacy_dose"));
});

// W7 — أول أداة وكيل مقابلة لزرار كان موجود من غير أداة (زرار حذف الصيدلية في
// PharmacyScreen). نفس شكل تحقق log_pharmacy_dose بالظبط — الاسم مش id.
Deno.test("delete_pharmacy_item rejects a name too short to match anything", async () => {
  assertEquals((await validateDeletePharmacyItem({ name: "" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateDeletePharmacyItem({ name: "ك" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateDeletePharmacyItem({ name: "كونكور" }, {}, freshContext("u"))).ok, true);
});

Deno.test("delete_pharmacy_item counts as a mutation, is not confirm-gated, and caps at 3 per turn", async () => {
  assert(MUTATING_TOOLS.includes("delete_pharmacy_item"));
  assert(!CONFIRM_REQUIRED_TOOLS.includes("delete_pharmacy_item"));
  const ctx = freshContext("u");
  ctx.counts["delete_pharmacy_item"] = 3;
  const v = await validateDeletePharmacyItem({ name: "بنادول" }, {}, ctx);
  assertEquals(v.ok, false);
});

// W8 — agent_tasks (schedule_task): تأجيل صحيح مستقبلي يعدي، ماضي أو تاريخ فاسد
// أو تأجيل أبعد من ٣٠ يوم يترفض.
Deno.test("schedule_task validates run_at and description bounds", async () => {
  const future = new Date(Date.now() + 3600_000).toISOString();
  const past = new Date(Date.now() - 3600_000).toISOString();
  const tooFar = new Date(Date.now() + 40 * 86_400_000).toISOString();

  assertEquals((await validateScheduleTask({ task_description: "قصير", run_at: future }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateScheduleTask({ task_description: "راجع مصاريف الأسبوع ده", run_at: "مش تاريخ" }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateScheduleTask({ task_description: "راجع مصاريف الأسبوع ده", run_at: past }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateScheduleTask({ task_description: "راجع مصاريف الأسبوع ده", run_at: tooFar }, {}, freshContext("u"))).ok, false);
  assertEquals((await validateScheduleTask({ task_description: "راجع مصاريف الأسبوع ده", run_at: future }, {}, freshContext("u"))).ok, true);
});

Deno.test("schedule_task counts as a mutation, is not confirm-gated, and caps at 3 per turn", async () => {
  assert(MUTATING_TOOLS.includes("schedule_task"));
  assert(!CONFIRM_REQUIRED_TOOLS.includes("schedule_task"));
  const ctx = freshContext("u");
  ctx.counts["schedule_task"] = 3;
  const future = new Date(Date.now() + 3600_000).toISOString();
  const v = await validateScheduleTask({ task_description: "راجع مصاريف الأسبوع ده", run_at: future }, {}, ctx);
  assertEquals(v.ok, false);
});
