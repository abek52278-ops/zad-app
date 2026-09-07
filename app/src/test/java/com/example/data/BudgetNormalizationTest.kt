package com.example.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * الخيار (أ): معاملة مالهاش سعر صرف **تتشال من الإجمالي** ويتعرض عددها.
 *
 * البديل اللي كان شغال هو الأخطر: `convert` بترجّع ١:١ فالمعاملة بتدخل الإجمالي
 * بقيمة غلط تماماً. ١٠٠ ليرة سورية كانت بتتجمع كأنها ١٠٠ جنيه — أي ~٤٠٠ ضعف قيمتها.
 */
class BudgetNormalizationTest {

    @After fun tearDown() = CurrencyExchange.resetForTest()

    private fun tx(amount: Double, currency: String?) =
        ZadTransaction(title = "t", amount = amount, isExpense = true, currency = currency)

    @Test
    fun `a currency with no rate is excluded and counted, not folded in at 1 to 1`() {
        CurrencyExchange.setLiveForTest(mapOf("USD" to 1.0, "EGP" to 0.02), System.currentTimeMillis())
        val out = BudgetMath.normalizedToCurrency(
            listOf(tx(100.0, "EGP"), tx(50.0, "XYZ"), tx(20.0, null)), "EGP",
        )
        assertEquals(2, out.transactions.size)
        assertEquals(1, out.excludedCount)
        assertEquals("XYZ", out.excluded.single().currency)
        // المتبقي مجموعه صادق: ١٠٠ (نفس العملة) + ٢٠ (بلا عملة = عملة الحساب)
        assertEquals(120.0, out.transactions.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `nothing is excluded when every currency has a rate`() {
        CurrencyExchange.setLiveForTest(
            mapOf("USD" to 1.0, "EGP" to 0.02, "SAR" to 0.25), System.currentTimeMillis(),
        )
        val out = BudgetMath.normalizedToCurrency(listOf(tx(100.0, "EGP"), tx(8.0, "SAR")), "EGP")
        assertEquals(0, out.excludedCount)
        assertEquals(200.0, out.transactions.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `a null home currency converts nothing and excludes nothing`() {
        val txs = listOf(tx(100.0, "XYZ"))
        val out = BudgetMath.normalizedToCurrency(txs, null)
        assertEquals(1, out.transactions.size)
        assertEquals(0, out.excludedCount)
    }
}
