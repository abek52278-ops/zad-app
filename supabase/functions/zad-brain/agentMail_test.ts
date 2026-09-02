import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { agentSenderFor } from "./agentMail.ts";
import { SpecialistId } from "./specialists.ts";

// zad_agent_messages.sender_check (live schema) only allows these six values. This list
// must stay in sync with the DB constraint — a mismatch here would pass while the real
// insert still fails silently in sendAgentReport's try/catch, exactly the bug this test
// exists to catch (see agentSenderFor's doc comment for the incident).
const DB_ALLOWED_SENDERS = new Set([
  "finance", "pantry", "pharmacy", "family", "home", "brain",
]);

Deno.test("agentSenderFor maps every SpecialistId to a value zad_agent_messages accepts", () => {
  const allSpecialistIds: SpecialistId[] = ["finance", "pantry", "pharmacy", "family", "home", "general"];
  for (const id of allSpecialistIds) {
    const sender = agentSenderFor(id);
    if (!DB_ALLOWED_SENDERS.has(sender)) {
      throw new Error(`agentSenderFor(${id}) = "${sender}", not accepted by zad_agent_messages.sender_check`);
    }
  }
});

Deno.test("agentSenderFor maps general to brain specifically, not any other fallback", () => {
  assertEquals(agentSenderFor("general"), "brain");
});

Deno.test("agentSenderFor passes real specialists through unchanged", () => {
  assertEquals(agentSenderFor("finance"), "finance");
  assertEquals(agentSenderFor("pantry"), "pantry");
  assertEquals(agentSenderFor("pharmacy"), "pharmacy");
  assertEquals(agentSenderFor("family"), "family");
  assertEquals(agentSenderFor("home"), "home");
});
