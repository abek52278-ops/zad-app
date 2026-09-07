package com.example.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * أسعار الصرف بقت من السيرفر، والثوابت بذرة أوفلاين بس.
 *
 * الحالة اللي التغيير ده اتعمل عشانها: `convert` كانت بترجّع المبلغ زي ما هو (١:١)
 * لأي عملة مش معروفة، فالليرة السورية كانت بتتجمع مع الدولار كأنهم نفس الوحدة —
 * ومن غير أي لوج. `null` بتجبر نقطة النداء تقرر بصراحة.
 */
class CurrencyExchangeLiveTest {

    @After fun tearDown() = CurrencyExchange.resetForTest()

    @Test
    fun `unknown currency returns null, never a silent 1 to 1`() {
        assertNull(CurrencyExchange.convert(100.0, "XYZ", "EGP"))
        assertNull(CurrencyExchange.convert(100.0, "EGP", "XYZ"))
        assertNull(CurrencyExchange.rateToUsd("XYZ"))
    }

    @Test
    fun `same currency is returned untouched even when unknown`() {
        // مفيش تحويل مطلوب أصلاً، فمفيش سبب نفشل.
        assertEquals(100.0, CurrencyExchange.convert(100.0, "XYZ", "XYZ")!!, 0.0001)
    }

    @Test
    fun `live rates override the offline seed`() {
        val seeded = CurrencyExchange.rateToUsd("EGP")!!
        CurrencyExchange.setLiveForTest(mapOf("EGP" to 0.5, "USD" to 1.0), System.currentTimeMillis())
        assertEquals(0.5, CurrencyExchange.rateToUsd("EGP")!!, 0.0001)
        assertTrue("البذرة والحي مالهمش نفس القيمة في التست ده", seeded != 0.5)
    }

    @Test
    fun `conversion goes through USD in both directions`() {
        CurrencyExchange.setLiveForTest(
            mapOf("USD" to 1.0, "EGP" to 0.02, "SAR" to 0.25), System.currentTimeMillis(),
        )
        // ١٠٠ جنيه = ٢ دولار = ٨ ريال
        assertEquals(8.0, CurrencyExchange.convert(100.0, "EGP", "SAR")!!, 0.0001)
        assertEquals(100.0, CurrencyExchange.convert(8.0, "SAR", "EGP")!!, 0.0001)
    }

    @Test
    fun `seed alone counts as stale, fresh live rates do not`() {
        // البذرة بتقدم بطبيعتها، فماينفعش تتعامل كأنها محدّثة.
        assertTrue(CurrencyExchange.isStale())
        CurrencyExchange.setLiveForTest(mapOf("USD" to 1.0), System.currentTimeMillis())
        assertFalse(CurrencyExchange.isStale())
    }

    @Test
    fun `live rates older than the window are stale again`() {
        val eightDaysAgo = System.currentTimeMillis() - (8L * 24 * 60 * 60 * 1000)
        CurrencyExchange.setLiveForTest(mapOf("USD" to 1.0), eightDaysAgo)
        assertTrue(CurrencyExchange.isStale())
    }

    @Test
    fun `cache round-trips and drops corrupt entries`() {
        val encoded = CurrencyExchange.encodeForCache(mapOf("EGP" to 0.02, "SAR" to 0.25))
        assertEquals(mapOf("EGP" to 0.02, "SAR" to 0.25), CurrencyExchange.parseCached(encoded))
        // قيم فاسدة تتشال بدل ما تتحول لصفر أو تكسر القراءة كلها
        assertEquals(
            mapOf("EGP" to 0.02),
            CurrencyExchange.parseCached("EGP=0.02;SAR=abc;TRY=0;JOD=;=1.0;junk"),
        )
    }
}
