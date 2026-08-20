package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeActivationTest {
    @Test
    fun `new household starts with three useful setup steps`() {
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = false,
            bankReadingEnabled = false,
            hasInventory = false,
        )

        assertEquals(0, progress.completedCount)
        assertEquals(3, progress.totalCount)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `progress reflects only completed setup work`() {
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = true,
            bankReadingEnabled = false,
            hasInventory = true,
        )

        assertEquals(2, progress.completedCount)
        assertTrue(progress.isComplete(HomeActivationStep.SET_BALANCE))
        assertFalse(progress.isComplete(HomeActivationStep.ENABLE_BANK_READING))
        assertTrue(progress.isComplete(HomeActivationStep.ADD_FIRST_INVENTORY_ITEM))
    }

    @Test
    fun `activation disappears only when all foundations are ready`() {
        val progress = buildHomeActivationProgress(
            hasConfirmedBalance = true,
            bankReadingEnabled = true,
            hasInventory = true,
        )

        assertEquals(3, progress.completedCount)
        assertTrue(progress.isComplete)
    }
}
