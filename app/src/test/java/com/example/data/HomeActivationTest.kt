package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeActivationTest {
    @Test
    fun `new household starts with four useful setup steps`() {
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = false,
            bankReadingEnabled = false,
            hasInventory = false,
            hasActiveGoal = false,
        )

        assertEquals(0, progress.completedCount)
        assertEquals(4, progress.totalCount)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `progress reflects only completed setup work`() {
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = true,
            bankReadingEnabled = false,
            hasInventory = true,
            hasActiveGoal = false,
        )

        assertEquals(2, progress.completedCount)
        assertTrue(progress.isComplete(HomeActivationStep.SET_BALANCE))
        assertFalse(progress.isComplete(HomeActivationStep.ENABLE_BANK_READING))
        assertTrue(progress.isComplete(HomeActivationStep.ADD_FIRST_INVENTORY_ITEM))
        assertFalse(progress.isComplete(HomeActivationStep.SET_FIRST_GOAL))
    }

    @Test
    fun `activation disappears only when all foundations are ready`() {
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = true,
            bankReadingEnabled = true,
            hasInventory = true,
            hasActiveGoal = true,
        )

        assertEquals(4, progress.completedCount)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `a household with every old step done still sees the goal step`() {
        // الـ٤ مستخدمين الحقيقيين خلّصوا الخطوات التلاتة قبل ما خطوة الهدف تتضاف، وagent_goals
        // عندهم صفر. الكارت لازم يرجع يظهر لهم بخطوة الهدف بس — ده المقصود مش عيب.
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = true,
            bankReadingEnabled = true,
            hasInventory = true,
            hasActiveGoal = false,
        )

        assertEquals(3, progress.completedCount)
        assertFalse(progress.isComplete)
        assertFalse(progress.isComplete(HomeActivationStep.SET_FIRST_GOAL))
    }
}
