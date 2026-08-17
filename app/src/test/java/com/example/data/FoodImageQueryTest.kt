package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug behind this: the customer's own words went to Pexels verbatim. "مطبخ" came
 * back as an empty kitchen and "لبن ومية" came back as a lake — Pexels never answers
 * "nothing", it answers with the nearest thing it has, so a vague query fails by
 * returning something confidently wrong instead of returning nothing at all.
 *
 * Plain JUnit, no Robolectric: this is string work with no `org.json` and no Context.
 *
 * These mirror `zad-core-intelligence/foodImageQuery_test.ts` case for case on purpose.
 * Both call sites hit the same API and the phone picks between them on whether a Pexels
 * key was compiled in, so a divergence here means the same dish gets two different
 * pictures depending on how the APK happened to be built.
 */
class FoodImageQueryTest {

    @Test
    fun `the two terms that actually broke now translate to food searches`() {
        assertEquals("home cooked meal food", FoodImageQuery.toSearchTerm("مطبخ"))
        // "و" joins two items with no shared picture; the first known one wins.
        assertEquals("milk glass food", FoodImageQuery.toSearchTerm("لبن ومية"))
    }

    @Test
    fun `Arabic dish names become English keywords`() {
        assertEquals("koshari egyptian rice lentils food", FoodImageQuery.toSearchTerm("كشري"))
        assertEquals("bechamel pasta bake food", FoodImageQuery.toSearchTerm("مكرونة بشاميل"))
    }

    @Test
    fun `a cooking method describes the dish instead of replacing it`() {
        // Regression: "مشوي" and "فراخ" are both four characters, so the length sort left
        // "مشوي" first and every grilled-anything searched for "grilled food".
        assertEquals("grilled chicken food", FoodImageQuery.toSearchTerm("فراخ مشوية"))
        assertEquals("fried potatoes food", FoodImageQuery.toSearchTerm("بطاطس مقلية"))
    }

    @Test
    fun `colloquial filler is stripped before the search`() {
        assertEquals("koshari egyptian rice lentils food", FoodImageQuery.toSearchTerm("عايز طبق كشري حلو"))
        assertEquals("fresh salad food", FoodImageQuery.toSearchTerm("وصفة سلطة سهلة"))
    }

    @Test
    fun `an unknown dish still gets the food qualifier rather than going bare`() {
        assertEquals("سليق حساوي food", FoodImageQuery.toSearchTerm("سليق حساوي"))
        assertEquals("tiramisu food", FoodImageQuery.toSearchTerm("tiramisu"))
    }

    @Test
    fun `filler-only input searches for nothing so the caller uses the fallback`() {
        assertEquals("", FoodImageQuery.toSearchTerm("طبق وجبة أكلة"))
        assertEquals("", FoodImageQuery.toSearchTerm("   "))
    }

    @Test
    fun `alt text naming scenery is rejected, food and blank are kept`() {
        assertEquals(false, FoodImageQuery.looksLikeFood("Snow covered mountain under blue sky"))
        assertEquals(false, FoodImageQuery.looksLikeFood("Body of water surrounded by trees"))
        assertEquals(true, FoodImageQuery.looksLikeFood("Cooked food on a white plate"))
        // Plenty of real Pexels photos carry no alt at all; absence is not evidence.
        assertEquals(true, FoodImageQuery.looksLikeFood(""))
        assertEquals(true, FoodImageQuery.looksLikeFood(null))
        // A kitchen shot that is about the meal still counts.
        assertEquals(true, FoodImageQuery.looksLikeFood("Woman cooking a meal in a kitchen"))
    }

    @Test
    fun `the fallback is stable per term, so a card does not swap pictures`() {
        val first = FoodImageQuery.fallbackUrl("كشري")
        assertEquals(first, FoodImageQuery.fallbackUrl("كشري"))
        assertEquals(first, FoodImageQuery.fallbackUrl("  كشري "))
        assertTrue(first.startsWith("https://images.pexels.com/photos/"))
    }
}
