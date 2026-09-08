import { assertEquals } from "jsr:@std/assert@1";

Deno.env.set("SUPABASE_URL", "https://example.supabase.co");
Deno.env.set("SUPABASE_SERVICE_ROLE_KEY", "test-service-key");
Deno.env.set("SUPABASE_ANON_KEY", "test-anon-key");

const { normalizePharmacyCategory } = await import("./index.ts");

Deno.test("normalizePharmacyCategory maps valid categories correctly", () => {
  assertEquals(normalizePharmacyCategory("مسكن"), "مسكن");
  assertEquals(normalizePharmacyCategory("مضاد حيوي"), "مضاد حيوي");
  assertEquals(normalizePharmacyCategory("فيتامين"), "فيتامين");
  assertEquals(normalizePharmacyCategory("مزمن"), "مزمن");
  assertEquals(normalizePharmacyCategory("عام"), "عام");
});

Deno.test("normalizePharmacyCategory strips leading definite article", () => {
  assertEquals(normalizePharmacyCategory("المسكن"), "مسكن");
  assertEquals(normalizePharmacyCategory("المضاد حيوي"), "مضاد حيوي");
  assertEquals(normalizePharmacyCategory("الفيتامين"), "فيتامين");
});

Deno.test("normalizePharmacyCategory falls back to عام on unknown or blank input", () => {
  assertEquals(normalizePharmacyCategory(""), "عام");
  assertEquals(normalizePharmacyCategory(null), "عام");
  assertEquals(normalizePharmacyCategory("اختراع غير معروف"), "عام");
});
