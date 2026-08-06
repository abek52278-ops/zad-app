package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * اختبارات وحدة نقية لـ [BalanceAnchor.computeCorrection] — منطق القبول/الرفض بس،
 * بدون DB أو شبكة (زي TxDeduplicator.isAmountMatch). هنا بالظبط الحماية من إن misparse
 * واحد للرصيد يفسد الرصيد الكلي، فمهم يتغطى صراحة.
 */
class BalanceAnchorTest {

    @Test
    fun `small plausible drift within sanity cap is corrected`() {
        // معاملة 50، فرق 40 (أقل من 3x الـ50) — معقول، كأنه إشعار متفوت واحد صغير
        val delta = BalanceAnchor.computeCorrection(computedBalance = 1000.0, bankBalance = 1040.0, transactionAmount = 50.0)
        assertEquals(40.0, delta!!, 0.001)
    }

    @Test
    fun `deficit drift is corrected as a negative delta`() {
        val delta = BalanceAnchor.computeCorrection(computedBalance = 1000.0, bankBalance = 950.0, transactionAmount = 30.0)
        assertEquals(-50.0, delta!!, 0.001)
    }

    @Test
    fun `drift far larger than the sanity cap is rejected, not corrected`() {
        // معاملة 20 بس، فرق 5000 — على الأرجح حساب/محفظة تانية خالص أو رقم اتفسّر غلط،
        // مش كذا إشعار متفوت بحجم مشابه. لازم يترفض
        val delta = BalanceAnchor.computeCorrection(computedBalance = 1000.0, bankBalance = 6000.0, transactionAmount = 20.0)
        assertNull(delta)
    }

    @Test
    fun `drift exactly at the sanity cap boundary is accepted`() {
        // سقف = 3x المعاملة = 150 بالظبط
        val delta = BalanceAnchor.computeCorrection(computedBalance = 1000.0, bankBalance = 1150.0, transactionAmount = 50.0)
        assertEquals(150.0, delta!!, 0.001)
    }

    @Test
    fun `zero drift needs no correction`() {
        val delta = BalanceAnchor.computeCorrection(computedBalance = 1000.0, bankBalance = 1000.0, transactionAmount = 50.0)
        assertNull(delta)
    }

    @Test
    fun `sub-cent rounding noise needs no correction`() {
        val delta = BalanceAnchor.computeCorrection(computedBalance = 1000.0, bankBalance = 1000.001, transactionAmount = 50.0)
        assertNull(delta)
    }
}
