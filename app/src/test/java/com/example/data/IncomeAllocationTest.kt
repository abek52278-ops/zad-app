package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The budget ceiling is a ceiling, not a balance.
 *
 * The rule used to be `remaining = monthly_limit - spent + income`, so a deposit landing
 * quietly enlarged what the customer was told they could spend — 20,000 arriving does not
 * mean 20,000 more of household grocery money. Income now moves the ceiling only when the
 * customer has explicitly said it funds this month.
 *
 * These assertions mirror `zad_budget_state_legacy` (migration 20260815140000), which is
 * the authority. If one changes, both change — a mirror that has drifted is exactly the
 * four-different-numbers problem Task 19.0 existed to close.
 */
class IncomeAllocationTest {

    private val cycleStart: LocalDate = LocalDate.of(2026, 8, 1)
    private val cycleEnd: LocalDate = LocalDate.of(2026, 9, 1)

    private fun tx(
        amount: Double,
        isExpense: Boolean,
        countsTowardBudget: Boolean? = null,
        day: Int = 10,
    ) = ZadTransaction(
        amount = amount,
        title = if (isExpense) "مصروف" else "إيداع",
        isExpense = isExpense,
        countsTowardBudget = countsTowardBudget,
        createdAt = "2026-08-${day.toString().padStart(2, '0')}T10:00:00Z",
    )

    @Test
    fun `unanswered income does not raise the ceiling`() {
        val txs = listOf(tx(30000.0, isExpense = false), tx(1000.0, isExpense = true))
        assertEquals(9000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
    }

    @Test
    fun `income the customer declined does not raise the ceiling`() {
        val txs = listOf(tx(30000.0, isExpense = false, countsTowardBudget = false))
        assertEquals(10000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
    }

    @Test
    fun `income the customer allocated does raise the ceiling`() {
        val txs = listOf(tx(5000.0, isExpense = false, countsTowardBudget = true), tx(1000.0, isExpense = true))
        assertEquals(14000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
    }

    @Test
    fun `a mixed cycle counts only the allocated part`() {
        val txs = listOf(
            tx(10000.0, isExpense = false, countsTowardBudget = true),   // للبيت
            tx(10000.0, isExpense = false, countsTowardBudget = false),  // مدخرات
            tx(10000.0, isExpense = false),                              // لسه ما اتسألش
            tx(2000.0, isExpense = true),
        )
        assertEquals(18000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
        // `income` بيفضل الإجمالي الصادق — العميل لازم يشوف اللي دخله فعلاً
        assertEquals(30000.0, BudgetMath.incomeInCycle(txs, cycleStart, cycleEnd), 0.01)
        assertEquals(10000.0, BudgetMath.allocatedIncomeInCycle(txs, cycleStart, cycleEnd), 0.01)
    }

    @Test
    fun `only unanswered income is queued for the question`() {
        val txs = listOf(
            tx(10000.0, isExpense = false, countsTowardBudget = true),
            tx(20000.0, isExpense = false, countsTowardBudget = false),
            tx(30000.0, isExpense = false, day = 12),
            tx(40000.0, isExpense = true), // مصروف — عمره ما بيتسأل عنه
        )
        val pending = BudgetMath.incomeAwaitingDecisionInCycle(txs, cycleStart, cycleEnd)
        assertEquals(1, pending.size)
        assertEquals(30000.0, pending.first().amount, 0.01)
    }

    @Test
    fun `the calendar-month helper follows the same rule as the cycle one`() {
        // FamilyState/BudgetTracker/ZadCentralBrain still call remaining(); if it kept
        // summing all income they would print a bigger number than Home does for the same
        // month, which is the divergence Task 19.0 was created to prevent.
        val asOf = LocalDate.of(2026, 8, 15)
        val txs = listOf(
            tx(5000.0, isExpense = false, countsTowardBudget = true),
            tx(9000.0, isExpense = false),
            tx(1000.0, isExpense = true),
        )
        assertEquals(
            BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!,
            BudgetMath.remaining(10000.0, txs, asOf)!!,
            0.01,
        )
    }

    @Test
    fun `no ceiling still means unknown rather than zero`() {
        val txs = listOf(tx(5000.0, isExpense = false, countsTowardBudget = true))
        assertEquals(null, BudgetMath.remainingInCycle(0.0, txs, cycleStart, cycleEnd))
    }
}
