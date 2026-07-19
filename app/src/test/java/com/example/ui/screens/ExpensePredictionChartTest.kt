package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ZadTransaction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest=Config.NONE)
class ExpensePredictionChartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test ExpensePredictionChart renders with mock transactions without crashing`() {
        val mockTransactions = listOf(
            ZadTransaction(title = "Tx1", amount = 100.0, isExpense = true, category = "Food", createdAt = "2026-07-01"),
            ZadTransaction(title = "Tx2", amount = 200.0, isExpense = true, category = "Food", createdAt = "2026-07-02"),
            ZadTransaction(title = "Tx3", amount = 50.0, isExpense = false, category = "Income", createdAt = "2026-07-03")
        )

        composeTestRule.setContent {
            ExpensePredictionChart(transactions = mockTransactions)
        }

        // Just assert that the composable tree is created successfully
        composeTestRule.onRoot().assertExists()
    }
    
    @Test
    fun `test ExpensePredictionChart renders with empty transactions`() {
        composeTestRule.setContent {
            ExpensePredictionChart(transactions = emptyList())
        }

        composeTestRule.onRoot().assertExists()
    }
}
