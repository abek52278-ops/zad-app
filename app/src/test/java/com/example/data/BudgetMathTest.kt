package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 19.4 — BudgetMath.cashOnHand يعكس منطق zad_cash_balance() (migration
 * 20260726060000) بالظبط: transfer→cash يزود، expense مع wallet=cash بينقص. باقي
 * BudgetMath (spentThisMonth/remaining) مالوش اختبارات لسه — خارج نطاق التاسك ده.
 */
class BudgetMathTest {

    private fun tx(
        amount: Double,
        txnKind: String,
        wallet: String = "card",
        transferTo: String? = null
    ) = ZadTransaction(
        amount = amount,
        title = "test",
        txnKind = txnKind,
        wallet = wallet,
        transferTo = transferTo
    )

    @Test
    fun `ATM withdrawal increases cash on hand`() {
        val txs = listOf(tx(amount = 1000.0, txnKind = "transfer", transferTo = "cash"))
        assertEquals(1000.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `cash expense decreases cash on hand`() {
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 300.0, txnKind = "expense", wallet = "cash")
        )
        assertEquals(700.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `card expense does not affect cash on hand`() {
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 300.0, txnKind = "expense", wallet = "card")
        )
        assertEquals(1000.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `transfer to a wallet other than cash does not affect cash on hand`() {
        val txs = listOf(tx(amount = 500.0, txnKind = "transfer", transferTo = "savings"))
        assertEquals(0.0, BudgetMath.cashOnHand(txs), 0.001)
    }

    @Test
    fun `no transactions means zero cash on hand`() {
        assertEquals(0.0, BudgetMath.cashOnHand(emptyList()), 0.001)
    }

    @Test
    fun `cash on hand never goes negative in representation but reflects overdrawn logging`() {
        // لو المستخدم سجّل صرف كاش أكتر من اللي سحبه (خطأ إدخال محتمل) — الدالة بترجع
        // سالب زي ما هي، مفيش تصحيح ضمني. الشاشة هي اللي بتقرر تعرض الكارت (cashOnHand > 0) أو لأ.
        val txs = listOf(
            tx(amount = 100.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 150.0, txnKind = "expense", wallet = "cash")
        )
        assertEquals(-50.0, BudgetMath.cashOnHand(txs), 0.001)
    }
}
