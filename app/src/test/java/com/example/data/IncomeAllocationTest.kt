package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The balance is a ledger: every deposit counts, on arrival, without being asked about.
 *
 * This file used to assert the opposite, and the reversal is deliberate. The rule was
 * `remaining = monthly_limit - spent + income the customer explicitly allocated`, so a
 * deposit only moved the figure once someone answered a question about it. That was right
 * while the figure was a *spending ceiling* — 20,000 landing is not 20,000 more of
 * household grocery money.
 *
 * It stopped being right when the ceiling became an opening balance
 * (migration 20260816010000). A balance going up is exactly what a deposit is, and the
 * question in between was doing real damage: a 10,000 salary sat in the account, visible
 * in `income`, while the customer was shown a balance of -500.
 *
 * These assertions mirror `zad_budget_state` (the wrapper, which computes the ledger from
 * `income` rather than `income_allocated`). If one changes, both change — a mirror that
 * has drifted is exactly the four-different-numbers problem Task 19.0 existed to close.
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
    fun `a deposit raises the balance without being allocated first`() {
        val txs = listOf(tx(30000.0, isExpense = false), tx(1000.0, isExpense = true))
        assertEquals(39000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
    }

    /**
     * The exact shape of the complaint that prompted the re-architecture: a salary had
     * landed and the customer was still being shown a negative balance.
     */
    @Test
    fun `the salary that produced the negative balance now counts`() {
        val txs = listOf(
            tx(10000.0, isExpense = false),   // الراتب — كان counts_toward_budget = null
            tx(3500.0, isExpense = true),
        )
        // Ceiling rule: 3000 - 3500 + 0 = -500.
        assertEquals(9500.0, BudgetMath.remainingInCycle(3000.0, txs, cycleStart, cycleEnd)!!, 0.01)
    }

    /**
     * `counts_toward_budget = false` no longer excludes the money either. The column still
     * records what the customer said, and the agent still reads it, but a balance is a
     * balance: money that arrived is money that is there.
     */
    @Test
    fun `income the customer declined still counts toward the balance`() {
        val txs = listOf(tx(30000.0, isExpense = false, countsTowardBudget = false))
        assertEquals(40000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
        // ...while the allocation itself stays visible to whoever wants to reason about it.
        assertEquals(0.0, BudgetMath.allocatedIncomeInCycle(txs, cycleStart, cycleEnd), 0.01)
    }

    @Test
    fun `a mixed cycle counts every deposit regardless of its allocation`() {
        val txs = listOf(
            tx(10000.0, isExpense = false, countsTowardBudget = true),   // للبيت
            tx(10000.0, isExpense = false, countsTowardBudget = false),  // مدخرات
            tx(10000.0, isExpense = false),                              // لسه ما اتسألش
            tx(2000.0, isExpense = true),
        )
        assertEquals(38000.0, BudgetMath.remainingInCycle(10000.0, txs, cycleStart, cycleEnd)!!, 0.01)
        assertEquals(30000.0, BudgetMath.incomeInCycle(txs, cycleStart, cycleEnd), 0.01)
        assertEquals(10000.0, BudgetMath.allocatedIncomeInCycle(txs, cycleStart, cycleEnd), 0.01)
    }

    @Test
    fun `the calendar-month helper follows the same rule as the cycle one`() {
        // FamilyState/BudgetTracker/ZadCentralBrain still call remaining(); if the two
        // helpers disagreed they would print different numbers for the same month, which
        // is the divergence Task 19.0 was created to prevent.
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
    fun `no opening balance still means unknown rather than zero`() {
        val txs = listOf(tx(5000.0, isExpense = false, countsTowardBudget = true))
        assertEquals(null, BudgetMath.remainingInCycle(0.0, txs, cycleStart, cycleEnd))
    }

    /**
     * The balance is still allowed to go negative — spending more than arrived is a real
     * situation and hiding it is the one thing this figure must never do. What changed is
     * that it now takes real overspending to get there, not an unanswered question.
     */
    @Test
    fun `spending past everything that arrived is still negative`() {
        val txs = listOf(tx(1000.0, isExpense = false), tx(4000.0, isExpense = true))
        assertEquals(-1000.0, BudgetMath.remainingInCycle(2000.0, txs, cycleStart, cycleEnd)!!, 0.01)
    }

    // ── Manual override ───────────────────────────────────────────────────────
    // The customer's correction has to survive the arithmetic: recording the delta the
    // helper returns must land the ledger exactly on the number they typed.

    @Test
    fun `the correction delta lands the balance exactly on the target`() {
        val txs = listOf(
            tx(10000.0, isExpense = false),
            tx(2500.0, isExpense = true),
            tx(300.0, isExpense = true, day = 12),
        )
        val opening = 3000.0
        val target = 4200.0
        val delta = BudgetMath.correctionToReachBalance(target, opening, txs, cycleStart, cycleEnd)

        // Applying it the way ZadViewModel.setBalanceTo does: an income row when positive,
        // an expense row when negative.
        val corrected = txs + tx(kotlin.math.abs(delta), isExpense = delta < 0, day = 20)
        assertEquals(target, BudgetMath.balanceInCycle(opening, corrected, cycleStart, cycleEnd), 0.01)
    }

    /**
     * Correcting downwards is the direction that broke the obvious implementation. Editing
     * the opening balance would have needed it to go negative, and every reader treats a
     * non-positive opening as "never set" — so the card would have blanked instead of
     * showing the lower number. A correction row has no such problem: it is just an
     * expense, and the opening balance never moves.
     */
    @Test
    fun `correcting downwards records an expense and leaves the opening balance alone`() {
        val txs = listOf(tx(10000.0, isExpense = false), tx(500.0, isExpense = true))
        val opening = 3000.0
        assertEquals(12500.0, BudgetMath.balanceInCycle(opening, txs, cycleStart, cycleEnd), 0.01)

        val delta = BudgetMath.correctionToReachBalance(100.0, opening, txs, cycleStart, cycleEnd)
        assertEquals(-12400.0, delta, 0.01)

        val corrected = txs + tx(kotlin.math.abs(delta), isExpense = true, day = 20)
        assertEquals(100.0, BudgetMath.balanceInCycle(opening, corrected, cycleStart, cycleEnd), 0.01)
    }

    @Test
    fun `a target that already matches needs no correction`() {
        val txs = listOf(tx(1000.0, isExpense = false), tx(400.0, isExpense = true))
        val opening = 2000.0
        val current = BudgetMath.balanceInCycle(opening, txs, cycleStart, cycleEnd)
        assertEquals(0.0, BudgetMath.correctionToReachBalance(current, opening, txs, cycleStart, cycleEnd), 0.001)
    }
}
