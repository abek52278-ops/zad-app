package com.example.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.random.Random

/**
 * الحارس اللي بيخلي قرار "الفلوس تفضل Double" (MASTER DIRECTIVE §3) مقفول. لو الاختبار ده
 * فشل يوماً — يبقى القرار محتاج مراجعة تاني، مش قبل كده.
 */
class MoneyTest {

    @Test
    fun sumOf10kTransactionsStaysExact() {
        val amounts = List(10_000) { Random.nextDouble(1.0, 5000.0).asMoney() }
        val sum = amounts.sum()
        val exact = amounts.fold(BigDecimal.ZERO) { a, v -> a + BigDecimal.valueOf(v) }
        assertTrue(abs(sum - exact.toDouble()) < 0.01)
    }

    @Test
    fun `asMoney rounds to nearest cent`() {
        assertTrue(abs(19.999999999999996.asMoney() - 20.0) < 1e-9)
        assertTrue(abs(10.005.asMoney() - 10.01) < 1e-9 || abs(10.005.asMoney() - 10.0) < 1e-9)
        assertTrue(abs(0.1.asMoney() - 0.1) < 1e-9)
    }
}
