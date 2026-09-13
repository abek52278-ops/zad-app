package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * الأهداف الجاهزة لازم تعدّي نفس فحوصات `zad_seed_life_goal` في الداتابيز (20260913220000):
 * عنوان ٤..٢٠٠ حرف، مقياس ≤٢٠٠، هدف موجب، تاريخ مش في الماضي. أي هدف الواجهة تبعته
 * والدالة ترفضه = زرار «ابدأ الهدف» شغال وماحصلش حاجة — عطل صامت.
 */
class LifeGoalSeedsTest {

    private val today = LocalDate.of(2026, 9, 13)

    @Test
    fun `every fixed preset builds a seed the database function accepts`() {
        for (preset in listOf(LifeGoalPreset.STICK_TO_BUDGET, LifeGoalPreset.PAY_OFF_DEBTS, LifeGoalPreset.REDUCE_WASTE)) {
            val seed = LifeGoalSeeds.build(preset, amount = null, customTitle = null, today = today)
            assertNotNull(preset.name, seed)
            assertValidForDatabase(seed!!)
        }
    }

    @Test
    fun `saving needs a real positive amount and writes it into the title`() {
        val seed = LifeGoalSeeds.build(LifeGoalPreset.SAVE_MONTHLY, amount = 500.0, customTitle = null, today = today)
        assertNotNull(seed)
        assertEquals("أوفّر 500 كل شهر", seed!!.title)
        assertTrue(seed.metric.contains("500"))
        assertValidForDatabase(seed)

        assertEquals("أوفّر 250.50 كل شهر", LifeGoalSeeds.build(LifeGoalPreset.SAVE_MONTHLY, 250.5, null, today)!!.title)
        for (bad in listOf(null, 0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY, 200_000_000.0)) {
            assertNull("amount=$bad", LifeGoalSeeds.build(LifeGoalPreset.SAVE_MONTHLY, bad, null, today))
        }
    }

    @Test
    fun `a custom goal is the user's words, trimmed, within the database limits`() {
        val seed = LifeGoalSeeds.build(LifeGoalPreset.CUSTOM, null, "  أجهّز مصاريف المدارس  ", today)
        assertEquals("أجهّز مصاريف المدارس", seed!!.title)
        assertValidForDatabase(seed)

        assertNull(LifeGoalSeeds.build(LifeGoalPreset.CUSTOM, null, "هدف", today))        // ٣ حروف
        assertNull(LifeGoalSeeds.build(LifeGoalPreset.CUSTOM, null, "   ", today))
        assertNull(LifeGoalSeeds.build(LifeGoalPreset.CUSTOM, null, null, today))
        assertNull(LifeGoalSeeds.build(LifeGoalPreset.CUSTOM, null, "س".repeat(201), today))
        assertNotNull(LifeGoalSeeds.build(LifeGoalPreset.CUSTOM, null, "س".repeat(200), today))
    }

    @Test
    fun `progress is counted in weekly check-ins over three months`() {
        // trg_agent_goal_progress بيزوّد current_value بواحد مع كل متابعة بتخلص،
        // فالهدف رقم متابعات مش مبلغ.
        val seed = LifeGoalSeeds.build(LifeGoalPreset.STICK_TO_BUDGET, null, null, today)!!
        assertEquals(12, seed.targetValue)
        assertEquals(LocalDate.of(2026, 12, 12), seed.deadline)
    }

    private fun assertValidForDatabase(seed: LifeGoalSeed) {
        assertTrue("title length ${seed.title.length}", seed.title.trim().length in 4..200)
        assertTrue("metric length ${seed.metric.length}", seed.metric.length <= 200)
        assertTrue(seed.targetValue > 0)
        assertTrue(!seed.deadline.isBefore(today))
    }
}
