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
  freshContext,
  validateAddShoppingItem,
  validateAskUser,
  validateEmitInsight,
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
