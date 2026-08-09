package com.example.ui.screens

import com.example.data.ZadTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * تغطية للفيكس: قبل كده [calculateStressTest] كانت بترجع `coverageDays = 0` في حالتين
 * مختلفتين تماماً — "رصيد الطوارئ فاضي" و"مفيش معدل صرف يومي مرصود". فمستخدم عنده
 * 50,000 جنيه رصيد طوارئ ومعندوش مصروفات مسجلة في آخر 30 يوم كان بيتقاله "0 يوم تغطية"
 * وحالته CRITICAL. دلوقتي التانية بترجع null + UNKNOWN.
 *
 * وكمان: المقام كان طول النافذة الثابت (30) حتى لو تاريخ المستخدم كله 3 أيام، فمعدله
 * اليومي كان بيطلع مقسوم على 10 وأيام التغطية بتتضخم بنفس النسبة.
 */
class StressTestCalcTest {

    private fun expense(amount: Double, daysAgo: Long) = ZadTransaction(
        title = "t",
        amount = amount,
        isExpense = true,
        createdAt = Instant.now().minusSeconds(daysAgo * 86400L).toString()
    )

    @Test
    fun `no recorded spending returns unknown coverage, not zero days`() {
        val result = calculateStressTest(transactions = emptyList(), liquidSavings = 50_000.0)

        assertNull("مفيش معدل صرف = مفيش رقم تغطية، مش صفر", result.coverageDays)
        assertEquals(StressTestStatus.UNKNOWN, result.status)
        assertEquals(0.0, result.avgDailySpend, 0.0001)
        assertEquals(50_000.0, result.liquidSavings, 0.0001)
        assertEquals("مفيش هدف نقفل فجوته من غير معدل صرف", 0.0, result.suggestedMonthlySaving, 0.0001)
    }

    @Test
    fun `spending outside the window is ignored and still yields unknown`() {
        val result = calculateStressTest(
            transactions = listOf(expense(900.0, daysAgo = 200)),
            liquidSavings = 50_000.0
        )

        assertNull(result.coverageDays)
        assertEquals(StressTestStatus.UNKNOWN, result.status)
    }

    @Test
    fun `emergency fund divided by real burn rate gives real coverage days`() {
        // 1750 على مدى ~7 أيام مرصودة = 250/يوم. 50,000 ÷ 250 = 200 يوم.
        val result = calculateStressTest(
            transactions = listOf(expense(1500.0, daysAgo = 6), expense(250.0, daysAgo = 0)),
            liquidSavings = 50_000.0
        )

        assertEquals(250.0, result.avgDailySpend, 0.01)
        assertEquals(200, result.coverageDays)
        assertEquals(StressTestStatus.HEALTHY, result.status)
    }

    @Test
    fun `burn rate uses observed span, not the nominal 30-day window`() {
        // نفس المصروف بالظبط. القسمة على 30 الثابتة كانت هتدي 100/يوم و500 يوم تغطية —
        // رقم مخترع من إن المستخدم لسه جديد، مش من إنه بيصرف قليل.
        val result = calculateStressTest(
            transactions = listOf(expense(3000.0, daysAgo = 2)),
            liquidSavings = 50_000.0
        )

        assertEquals(1000.0, result.avgDailySpend, 0.01)
        assertEquals(50, result.coverageDays)
    }

    @Test
    fun `empty emergency fund with real spending is genuinely zero and critical`() {
        val result = calculateStressTest(
            transactions = listOf(expense(3000.0, daysAgo = 2)),
            liquidSavings = 0.0
        )

        assertEquals(0, result.coverageDays)
        assertEquals(StressTestStatus.CRITICAL, result.status)
        assertTrue("لسه محتاج يوفّر عشان يوصل للهدف", result.suggestedMonthlySaving > 0.0)
    }
}
