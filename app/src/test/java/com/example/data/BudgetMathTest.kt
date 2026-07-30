package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
        transferTo: String? = null,
        createdAt: String? = null
    ) = ZadTransaction(
        amount = amount,
        title = "test",
        txnKind = txnKind,
        wallet = wallet,
        transferTo = transferTo,
        createdAt = createdAt
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

    // totalIncome/totalExpense — 5 شاشات كانت بتعيد نفس المنطق ده بـ isExpense بدل txnKind
    // (AUDIT.md "Rule 1"). التوحيد ده هو اللي بيثبت إن سحب ATM (transfer) مابيتحسبش
    // مصروف/دخل في أي منهم، مش بس في spentThisMonth.

    @Test
    fun `totalExpense sums only expense-kind transactions`() {
        val txs = listOf(
            tx(amount = 200.0, txnKind = "expense"),
            tx(amount = 50.0, txnKind = "expense"),
            tx(amount = 1000.0, txnKind = "income")
        )
        assertEquals(250.0, BudgetMath.totalExpense(txs), 0.001)
    }

    @Test
    fun `totalIncome sums only income-kind transactions`() {
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "income"),
            tx(amount = 200.0, txnKind = "expense")
        )
        assertEquals(1000.0, BudgetMath.totalIncome(txs), 0.001)
    }

    @Test
    fun `ATM withdrawal is excluded from both totalExpense and totalIncome`() {
        // ده بالظبط بق 19.1: سحب ATM isExpense=true قديماً كان بيتحسب مصروف زيادة عن
        // صرف الكاش الفعلي بعد كده. txnKind=transfer بيستبعده من الاتنين خالص.
        val txs = listOf(
            tx(amount = 500.0, txnKind = "transfer", transferTo = "cash"),
            tx(amount = 100.0, txnKind = "expense", wallet = "cash")
        )
        assertEquals(100.0, BudgetMath.totalExpense(txs), 0.001)
        assertEquals(0.0, BudgetMath.totalIncome(txs), 0.001)
    }

    @Test
    fun `totalIncome and totalExpense are zero with no transactions`() {
        assertEquals(0.0, BudgetMath.totalIncome(emptyList()), 0.001)
        assertEquals(0.0, BudgetMath.totalExpense(emptyList()), 0.001)
    }

    // Task 25 — نفس منطق spentThisMonth/incomeThisMonth بس بحدود دورة الراتب بدل الشهر
    // التقويمي. cycleStart/cycleEnd هنا محسوبين يدوي (مش عن طريق CycleMath) عشان الاختبار
    // يفحص BudgetMath وحدها، منفصل عن CycleMathTest اللي بيفحص حساب الحدود نفسه.

    @Test
    fun `spentInCycle only counts expenses inside the cycle window`() {
        val cycleStart = LocalDate.of(2026, 7, 28)
        val cycleEnd = LocalDate.of(2026, 8, 28)
        val txs = listOf(
            tx(amount = 100.0, txnKind = "expense", createdAt = "2026-07-27"), // قبل الدورة
            tx(amount = 200.0, txnKind = "expense", createdAt = "2026-07-28"), // أول يوم فيها
            tx(amount = 50.0, txnKind = "expense", createdAt = "2026-08-15"),  // جوه الدورة
            tx(amount = 300.0, txnKind = "expense", createdAt = "2026-08-28")  // أول يوم الدورة الجاية، برّه
        )
        assertEquals(250.0, BudgetMath.spentInCycle(txs, cycleStart, cycleEnd), 0.001)
    }

    @Test
    fun `remainingInCycle matches remaining when cycle equals a calendar month`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 8, 1)
        val txs = listOf(
            tx(amount = 1000.0, txnKind = "income", createdAt = "2026-07-05"),
            tx(amount = 300.0, txnKind = "expense", createdAt = "2026-07-10")
        )
        assertEquals(BudgetMath.remaining(2000.0, txs, LocalDate.of(2026, 7, 15)),
            BudgetMath.remainingInCycle(2000.0, txs, cycleStart, cycleEnd), 0.001)
    }

    @Test
    fun `velocityInCycle above 1 means spending faster than the cycle allows`() {
        // نص الدورة (يوم ١٥ من ٣٠) وصرف أوعى نص الميزانية بالظبط — ده متوقع، سرعة = ١
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 7, 31)
        val txs = listOf(tx(amount = 500.0, txnKind = "expense", createdAt = "2026-07-14"))
        val velocity = BudgetMath.velocityInCycle(1000.0, txs, cycleStart, cycleEnd, LocalDate.of(2026, 7, 15))
        assertEquals(1.0, velocity, 0.05)
    }

    @Test
    fun `dailyAllowanceInCycle splits remaining across days left in the cycle`() {
        val cycleStart = LocalDate.of(2026, 7, 1)
        val cycleEnd = LocalDate.of(2026, 7, 11) // ١٠ أيام بالظبط
        val txs = emptyList<ZadTransaction>()
        // متبقي = ١٠٠٠ (مفيش صرف)، ١٠ أيام باقيين من يوم ١ — ١٠٠ في اليوم
        val allowance = BudgetMath.dailyAllowanceInCycle(1000.0, txs, cycleStart, cycleEnd, LocalDate.of(2026, 7, 1))
        assertEquals(100.0, allowance, 0.001)
    }
}
