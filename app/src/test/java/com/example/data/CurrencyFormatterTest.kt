package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — CurrencyFormatter.format(context, tx)
 * لازم يعرض عملة المعاملة نفسها، مش عملة الـ Market الحالي المختار في التطبيق.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CurrencyFormatterTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        MarketPrefs.setCurrentMarketForTest(Market.SAUDI_ARABIA)
    }

    @After
    fun tearDown() {
        MarketPrefs.setCurrentMarketForTest(Market.SAUDI_ARABIA)
    }

    private fun tx(amount: Double, currency: String?) = ZadTransaction(
        amount = amount, title = "test", currency = currency
    )

    @Test
    fun `transaction with null currency falls back to the current market's symbol`() {
        assertEquals("100 ر.س", CurrencyFormatter.format(context, tx(100.0, null)))
    }

    @Test
    fun `transaction currency matching the current market uses the market's familiar symbol`() {
        assertEquals("100 ر.س", CurrencyFormatter.format(context, tx(100.0, "SAR")))
    }

    @Test
    fun `transaction currency different from the current market renders that currency's own symbol, not the market's`() {
        // المستخدم سوقه سعودي، بس المعاملة دي جت من رسالة بنك مصري وأنت مسافر — لازم
        // تتعرض "ج.م" مش "ر.س"، وإلا رجعنا لنفس بق مرحلة ٠ب بس للعملة.
        assertEquals("300 ج.م", CurrencyFormatter.format(context, tx(300.0, "EGP")))
    }

    @Test
    fun `unrecognized currency code renders as-is instead of a misleading symbol`() {
        assertEquals("50 USD", CurrencyFormatter.format(context, tx(50.0, "USD")))
    }
}
