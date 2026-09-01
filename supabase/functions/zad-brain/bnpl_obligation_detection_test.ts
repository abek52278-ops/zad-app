import { assertEquals } from "jsr:@std/assert@1";
import { detectBnplObligationCandidate } from "./index.ts";

Deno.test("detectBnplObligationCandidate بيكتشف مزوّد ظهر مرتين بنفس المبلغ بالظبط", () => {
  const tx = [
    { bank_name: "تابي", amount: 250, created_at: "2026-08-01T00:00:00Z" },
    { bank_name: "تابي", amount: 250, created_at: "2026-08-29T00:00:00Z" },
  ];
  const out = detectBnplObligationCandidate(tx, []);
  assertEquals(out?.provider, "تابي");
  assertEquals(out?.amount, 250);
  assertEquals(out?.title, "قسط تابي");
});

Deno.test("detectBnplObligationCandidate بيرفض مرة واحدة بس", () => {
  const tx = [{ bank_name: "تمارة", amount: 100, created_at: "2026-08-01T00:00:00Z" }];
  assertEquals(detectBnplObligationCandidate(tx, []), null);
});

Deno.test("detectBnplObligationCandidate بيتجاهل معاملات مش من مزوّد BNPL معروف", () => {
  const tx = [
    { bank_name: "بنك الراجحي", amount: 250, created_at: "2026-08-01T00:00:00Z" },
    { bank_name: "بنك الراجحي", amount: 250, created_at: "2026-08-29T00:00:00Z" },
  ];
  assertEquals(detectBnplObligationCandidate(tx, []), null);
});

Deno.test("detectBnplObligationCandidate بيرفض مبلغ مختلف حتى لو نفس المزوّد", () => {
  const tx = [
    { bank_name: "فاليو", amount: 100, created_at: "2026-08-01T00:00:00Z" },
    { bank_name: "فاليو", amount: 150, created_at: "2026-08-29T00:00:00Z" },
  ];
  assertEquals(detectBnplObligationCandidate(tx, []), null);
});

Deno.test("detectBnplObligationCandidate بيستبعد مزوّد+مبلغ متسجل كالتزام بالفعل", () => {
  const tx = [
    { bank_name: "تابي", amount: 250, created_at: "2026-08-01T00:00:00Z" },
    { bank_name: "تابي", amount: 250, created_at: "2026-08-29T00:00:00Z" },
  ];
  const existing = [{
    id: "1", title: "قسط تابي", amount: 250, kind: "installment",
    due_day: 1, due_date: null, recurrence: "monthly", confirmed: true, active: true,
    provider: "تابي",
  }];
  assertEquals(detectBnplObligationCandidate(tx, existing), null);
});

Deno.test("detectBnplObligationCandidate بيختار أكبر مجموعة لو أكتر من مرشح", () => {
  const tx = [
    { bank_name: "تابي", amount: 100, created_at: "2026-06-01T00:00:00Z" },
    { bank_name: "تابي", amount: 100, created_at: "2026-07-01T00:00:00Z" },
    { bank_name: "تمارة", amount: 200, created_at: "2026-06-01T00:00:00Z" },
    { bank_name: "تمارة", amount: 200, created_at: "2026-07-01T00:00:00Z" },
    { bank_name: "تمارة", amount: 200, created_at: "2026-08-01T00:00:00Z" },
  ];
  const out = detectBnplObligationCandidate(tx, []);
  assertEquals(out?.provider, "تمارة"); // 3 مرات ضد 2
});

Deno.test("detectBnplObligationCandidate بيرجع dedupe_key مستقر لنفس المدخل", () => {
  const tx = [
    { bank_name: "تابي", amount: 250, created_at: "2026-08-01T00:00:00Z" },
    { bank_name: "تابي", amount: 250, created_at: "2026-08-29T00:00:00Z" },
  ];
  const a = detectBnplObligationCandidate(tx, []);
  const b = detectBnplObligationCandidate(tx, []);
  assertEquals(a?.dedupe_key, b?.dedupe_key);
  assertEquals(typeof a?.dedupe_key, "string");
});
