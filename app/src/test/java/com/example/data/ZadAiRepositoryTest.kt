package com.example.data

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZadAiRepositoryTest {

    @Before
    fun setup() {
        mockkObject(ZadAiProxyClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test LearningSystem updatePatterns with 5 transactions`() = runTest {
        // Arrange
        val transactions = listOf(
            ZadTransaction(title = "Coffee", amount = 15.0, isExpense = true, category = "Food", createdAt = "2026-07-01"),
            ZadTransaction(title = "Lunch", amount = 120.0, isExpense = true, category = "Food", createdAt = "2026-07-02"),
            ZadTransaction(title = "Groceries", amount = 300.0, isExpense = true, category = "Supermarket", createdAt = "2026-07-03"),
            ZadTransaction(title = "Salary", amount = 5000.0, isExpense = false, category = "Income", createdAt = "2026-07-04"),
            ZadTransaction(title = "Gas", amount = 80.0, isExpense = true, category = "Transport", createdAt = "2026-07-05")
        )
        val emptyInventory = emptyList<ZadInventory>()

        val expectedInsights = listOf(
            AiInsight("High Food Expense", "You spent 135 on food", "Alert"),
            AiInsight("Great savings", "You saved money", "Tip")
        )

        coEvery { ZadAiProxyClient.generateBehavioralInsights(transactions, emptyInventory) } returns expectedInsights

        // Act
        val result = ZadAiRepository.generateBehavioralInsights(transactions, emptyInventory)

        // Assert
        assertEquals(2, result.size)
        assertEquals("High Food Expense", result[0].title)
        
        coVerify(exactly = 1) { ZadAiProxyClient.generateBehavioralInsights(transactions, emptyInventory) }
    }
}
