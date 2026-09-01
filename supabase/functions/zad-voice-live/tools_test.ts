import { assertEquals } from "jsr:@std/assert@1";
import {
  CONFIRM_VOICE_TOOLS,
  describeVoiceProposal,
  DIRECT_VOICE_TOOLS,
  isConfirmRequired,
  VOICE_TOOLS,
} from "./tools.ts";

Deno.test("VOICE_TOOLS is the union of direct + confirm tools, no overlap", () => {
  assertEquals(VOICE_TOOLS.length, DIRECT_VOICE_TOOLS.length + CONFIRM_VOICE_TOOLS.length);
  const directNames = new Set(DIRECT_VOICE_TOOLS.map((t) => t.name));
  const confirmNames = new Set(CONFIRM_VOICE_TOOLS.map((t) => t.name));
  for (const name of confirmNames) assertEquals(directNames.has(name), false);
});

Deno.test("isConfirmRequired matches exactly the four money tools (CONFIRM_REQUIRED_TOOLS parity)", () => {
  assertEquals(isConfirmRequired("log_transaction"), true);
  assertEquals(isConfirmRequired("update_transaction"), true);
  assertEquals(isConfirmRequired("delete_transaction"), true);
  assertEquals(isConfirmRequired("set_monthly_limit"), true);
  assertEquals(isConfirmRequired("add_inventory_item"), false);
  assertEquals(isConfirmRequired("add_shopping_item"), false);
  assertEquals(isConfirmRequired("unknown_tool"), false);
});

Deno.test("every direct tool has a valid object schema with at least one required field", () => {
  for (const t of DIRECT_VOICE_TOOLS) {
    const params = t.parameters as { type: string; required?: string[] };
    assertEquals(params.type, "object");
    assertEquals(Array.isArray(params.required) && params.required.length > 0, true);
  }
});

Deno.test("describeVoiceProposal covers all four confirm-required tools including delete_transaction", () => {
  assertEquals(
    describeVoiceProposal("log_transaction", { txn_kind: "expense", amount: 500, title: "إيجار" }),
    "مصروف 500 — إيجار",
  );
  assertEquals(
    describeVoiceProposal("delete_transaction", { transaction_id: "abc" }),
    "حذف معاملة نهائياً",
  );
  assertEquals(
    describeVoiceProposal("set_monthly_limit", { monthly_limit: 3000 }),
    "رصيدك يبقى 3000",
  );
  const updateDesc = describeVoiceProposal("update_transaction", { amount: 200, title: "بقالة" });
  assertEquals(updateDesc.includes("200"), true);
  assertEquals(updateDesc.includes("بقالة"), true);
});
