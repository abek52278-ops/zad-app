import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { mediaGate } from "./entitlement.ts";

function sbWith(responses: Record<string, unknown>) {
  return {
    rpc: (name: string) => Promise.resolve({ data: responses[name] ?? null, error: null }),
  };
}

Deno.test("المستخدم المجاني بيتقفل على الصوت والصور برسالة ترقية", async () => {
  const sb = sbWith({ zad_entitlement_status: { telegram_media: false, tier: "free" } });

  const voice = await mediaGate(sb, "u1", "voice");
  assertEquals(voice.allowed, false);
  assertEquals(voice.reply!.includes("الصوتية"), true);
  // لازم توضّح إن النص لسه مجاني — القفل ده تسويق مش طرد.
  assertEquals(voice.reply!.includes("مجاناً"), true);

  const photo = await mediaGate(sb, "u1", "scan");
  assertEquals(photo.allowed, false);
  assertEquals(photo.reply!.includes("الفواتير"), true);
});

Deno.test("المشترك اللي عنده رصيد بيعدي", async () => {
  const sb = sbWith({
    zad_entitlement_status: { telegram_media: true, tier: "plus" },
    zad_entitlement_consume: { allowed: true, reason: "quota", left: 99 },
  });
  assertEquals(await mediaGate(sb, "u1", "voice"), { allowed: true });
  assertEquals(await mediaGate(sb, "u1", "scan"), { allowed: true });
});

Deno.test("المشترك اللي رصيده خلص بياخد رسالة رصيد مش رسالة ترقية", async () => {
  const sb = sbWith({
    zad_entitlement_status: { telegram_media: true, tier: "starter" },
    zad_entitlement_consume: { allowed: false, reason: "quota_exhausted" },
  });
  const r = await mediaGate(sb, "u1", "voice");
  assertEquals(r.allowed, false);
  assertEquals(r.reply!.includes("خلص رصيد"), true);
});

// الفرق المقصود عن بوابة zad-brain: دي بتفشل مقفولة. لو الفحص وقع، الميزة المدفوعة
// ماتبقاش مجانية لأي حد بيجرب في اللحظة دي.
Deno.test("فشل الفحص بيقفل الوسائط، مش بيفتحها", async () => {
  const sbErr = { rpc: () => Promise.resolve({ data: null, error: { message: "boom" } }) };
  assertEquals((await mediaGate(sbErr, "u1", "voice")).allowed, false);

  const sbThrow = { rpc: () => Promise.reject(new Error("net")) };
  assertEquals((await mediaGate(sbThrow, "u1", "scan")).allowed, false);
});

Deno.test("رد ناقص من الـ RPC بيتعامل معاه كقفل", async () => {
  const sb = sbWith({ zad_entitlement_status: {} });
  assertEquals((await mediaGate(sb, "u1", "voice")).allowed, false);
});
