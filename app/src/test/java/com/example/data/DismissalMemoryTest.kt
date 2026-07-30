package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task 28 (PRODUCT_PLAN.md) — informative dismissal. Pure-function tests only; the
 * Supabase write path (SupabaseRepo.dismissInsightWithReason) isn't unit-testable here.
 */
class DismissalMemoryTest {

    private fun insight(title: String, aboutItem: String? = null) = ZadInsight(
        id = "i1", title = title, body = "body", aboutItem = aboutItem
    )

    @Test
    fun `not_relevant produces a dismissal-scoped note about the subject`() {
        val note = DismissalMemory.noteFor("not_relevant", insight("اشتراك نتفليكس"))!!
        assertEquals("dismissal", note.scope)
        assertEquals(true, note.note.contains("اشتراك نتفليكس"))
    }

    @Test
    fun `wrong_data uses a distinct scope and higher confidence than the other two reasons`() {
        // PRODUCT_PLAN Task 28 — "الرقم غلط... a free bug report... that signal is being
        // thrown away" — must not be indistinguishable from a plain "not interested".
        val wrongData = DismissalMemory.noteFor("wrong_data", insight("الميزانية"))!!
        val notRelevant = DismissalMemory.noteFor("not_relevant", insight("الميزانية"))!!
        val timing = DismissalMemory.noteFor("timing", insight("الميزانية"))!!
        assertEquals("data_quality", wrongData.scope)
        assert(wrongData.confidence > notRelevant.confidence)
        assert(wrongData.confidence > timing.confidence)
    }

    @Test
    fun `timing reason produces the lowest-confidence note`() {
        val note = DismissalMemory.noteFor("timing", insight("فاتورة الكهرباء"))!!
        assertEquals("dismissal", note.scope)
        assertEquals(0.3, note.confidence, 0.001)
    }

    @Test
    fun `subject prefers aboutItem over title when both are present`() {
        val note = DismissalMemory.noteFor("not_relevant", insight(title = "عنوان عام", aboutItem = "بيض"))!!
        assertEquals(true, note.note.contains("بيض"))
        assertEquals(false, note.note.contains("عنوان عام"))
    }

    @Test
    fun `unknown reason returns null instead of a garbage note`() {
        assertNull(DismissalMemory.noteFor("snoozed", insight("test")))
    }
}
