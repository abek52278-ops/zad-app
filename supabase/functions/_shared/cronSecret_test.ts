import { assert, assertEquals, assertNotEquals } from "jsr:@std/assert@1";
import { fingerprint, secretMatches } from "./cronSecret.ts";

const ENV = "ZAD_TEST_CRON_SECRET";
const VALUE = "22404102f9b8c8558b69806c20d0305d1c621a2d91c889a30403dcd36afda026";

function withSecret<T>(value: string | undefined, fn: () => T): T {
  if (value === undefined) Deno.env.delete(ENV);
  else Deno.env.set(ENV, value);
  try {
    return fn();
  } finally {
    Deno.env.delete(ENV);
  }
}

Deno.test("an exact match is accepted", async () => {
  assert(await withSecret(VALUE, () => secretMatches(VALUE, ENV)));
});

Deno.test("an unset secret rejects rather than failing open", async () => {
  // ده أهم سطر في الملف: دالة بـverify_jwt=false والهيدر هو الحارس الوحيد،
  // فسر ناقص لازم يقفل مش يفتح.
  assert(!(await withSecret(undefined, () => secretMatches(VALUE, ENV))));
});

Deno.test("a missing header rejects", async () => {
  assert(!(await withSecret(VALUE, () => secretMatches(null, ENV))));
  assert(!(await withSecret(VALUE, () => secretMatches("", ENV))));
});

Deno.test("whitespace is not silently tolerated", async () => {
  // مسافة زايدة لازم ترفض — بس اللوج بيقول trimmed match=true فالسبب بيبان.
  assert(!(await withSecret(VALUE, () => secretMatches(VALUE + "\n", ENV))));
  assert(!(await withSecret(VALUE, () => secretMatches(" " + VALUE, ENV))));
});

Deno.test("a different value of the same length rejects", async () => {
  const other = "8".repeat(VALUE.length);
  assertEquals(other.length, VALUE.length);
  assert(!(await withSecret(VALUE, () => secretMatches(other, ENV))));
});

Deno.test("the fingerprint is short, stable and does not leak the value", async () => {
  const fp = await fingerprint(VALUE);
  assertEquals(fp.length, 8);
  assertEquals(fp, await fingerprint(VALUE));           // مستقرة
  assertNotEquals(fp, await fingerprint(VALUE + "x"));  // بتفرّق
  assert(!VALUE.includes(fp), "البصمة مش المفروض تكون جزء من القيمة");
});
