// The bug these guard: the customer's own words went to Pexels verbatim. "مطبخ" came
// back an empty kitchen and "لبن ومية" came back a landscape — Pexels never answers
// "nothing", it answers with the nearest thing it has, so a vague query fails by
// returning something confidently wrong.

import { assertEquals, assert } from "jsr:@std/assert@1";
import { foodFallbackUrl, looksLikeFoodAlt, toFoodSearchTerm } from "./foodImageQuery.ts";

Deno.test("the two terms that actually broke now translate to food searches", () => {
  assertEquals(toFoodSearchTerm("مطبخ"), "home cooked meal food");
  // "و" joins two items with no shared picture; the first known one wins.
  assertEquals(toFoodSearchTerm("لبن ومية"), "milk glass food");
});

Deno.test("Arabic dish names become English keywords", () => {
  assertEquals(toFoodSearchTerm("كشري"), "koshari egyptian rice lentils food");
  assertEquals(toFoodSearchTerm("فراخ مشوية"), "grilled chicken food");
  assertEquals(toFoodSearchTerm("مكرونة بشاميل"), "bechamel pasta bake food");
});

Deno.test("colloquial filler is stripped before the search", () => {
  assertEquals(toFoodSearchTerm("عايز طبق كشري حلو"), "koshari egyptian rice lentils food");
  assertEquals(toFoodSearchTerm("وصفة سلطة سهلة"), "fresh salad food");
});

Deno.test("an unknown name still gets the food qualifier rather than going bare", () => {
  assertEquals(toFoodSearchTerm("سليق حساوي"), "سليق حساوي food");
  assertEquals(toFoodSearchTerm("tiramisu"), "tiramisu food");
});

Deno.test("filler-only input searches for nothing so the caller uses the fallback", () => {
  assertEquals(toFoodSearchTerm("عايز حاجة حلوة النهاردة"), "حاجة food");
  assertEquals(toFoodSearchTerm("   "), "");
  assertEquals(toFoodSearchTerm("طبق وجبة أكلة"), "");
});

Deno.test("alt text that names scenery is rejected, food and blank are kept", () => {
  assertEquals(looksLikeFoodAlt("Snow covered mountain under blue sky"), false);
  assertEquals(looksLikeFoodAlt("Body of water surrounded by trees"), false);
  assertEquals(looksLikeFoodAlt("Cooked food on a white plate"), true);
  // Plenty of real Pexels photos carry no alt at all; absence is not evidence.
  assertEquals(looksLikeFoodAlt(""), true);
  assertEquals(looksLikeFoodAlt(null), true);
  // A kitchen shot that is about the meal still counts.
  assertEquals(looksLikeFoodAlt("Woman cooking a meal in a kitchen"), true);
});

Deno.test("the fallback is stable per term, so a card does not swap pictures", () => {
  const a = foodFallbackUrl("كشري");
  assertEquals(a, foodFallbackUrl("كشري"));
  assertEquals(a, foodFallbackUrl("  كشري "));
  assert(a.startsWith("https://images.pexels.com/photos/"));
});
