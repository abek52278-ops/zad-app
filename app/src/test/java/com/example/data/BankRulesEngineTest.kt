package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات bank_rules.json — القواعد المصرية الجديدة بتتحمل وتتطابق صح، وترجع null بهدوء
 * لو مفيش sender يطابق أو الرسالة مش شكلها متوقع (مفيش تخمين).
 *
 * Task 21 — tryParse بقى بيفلتر بـ country النشط (MarketPrefs.currentMarket)، فكل الاختبارات
 * دي (قواعد مصرية) لازم السوق يكون مصر صراحة، مش الافتراضي (السعودية).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BankRulesEngineTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        MarketPrefs.setCurrentMarketForTest(Market.EGYPT)
    }

    @After
    fun tearDown() {
        MarketPrefs.setCurrentMarketForTest(Market.SAUDI_ARABIA)
    }

    @Test
    fun `CIB debit message parses amount, merchant, and category`() {
        val result = BankRulesEngine.tryParse(
            context, "CIB",
            "تم خصم مبلغ 250.00 جنيه من حسابك لدى كارفور مصر الجديدة. مرجع: TX48213"
        )!!
        assertEquals(250.0, result.amount, 0.001)
        assertTrue(result.isExpense)
        assertEquals("كارفور مصر الجديدة", result.merchantName)
        assertEquals(0.9f, result.confidence, 0.001f)
        assertEquals("TX48213", result.externalRef)
    }

    @Test
    fun `Vodafone Cash transfer parses with EGP symbol`() {
        val result = BankRulesEngine.tryParse(
            context, "Vodafone Cash",
            "تم تحويل 100 EGP لصالح أحمد محمد"
        )!!
        assertEquals(100.0, result.amount, 0.001)
        assertTrue(result.isExpense)
    }

    @Test
    fun `Fawry bill payment parses with jr symbol`() {
        val result = BankRulesEngine.tryParse(
            context, "Fawry",
            "تم سداد فاتورة 75.50 ج.م لصالح شركة الكهرباء"
        )!!
        assertEquals(75.50, result.amount, 0.001)
    }

    @Test
    fun `unknown sender returns null (no rule to try, falls through to Kotlin path)`() {
        val result = BankRulesEngine.tryParse(context, "unknown_sender", "تم خصم مبلغ 50 جنيه")
        assertNull(result)
    }

    @Test
    fun `known sender but unmatched message shape returns null, does not guess`() {
        val result = BankRulesEngine.tryParse(context, "CIB", "رسالة ترحيبية من سي آي بي، أهلاً بيك")
        assertNull(result)
    }

    @Test
    fun `arabic-indic digits normalize correctly through the JSON path`() {
        val result = BankRulesEngine.tryParse(
            context, "NBE",
            "تم خصم مبلغ ٣٠٠.٠٠ جنيه من حسابك لدى أمازون مصر"
        )
        assertNotNull(result)
        assertEquals(300.0, result!!.amount, 0.001)
    }

    // ─── Task 21: currency-before-amount (real Egyptian bank SMS shape) ──

    @Test
    fun `currency before amount parses (Purchase of EGP X format)`() {
        val result = BankRulesEngine.tryParse(
            context, "NBE",
            "Purchase of EGP 450.00 with card ending 1234 at CARREFOUR"
        )
        assertNotNull(result)
        assertEquals(450.0, result!!.amount, 0.001)
        assertEquals("CARREFOUR", result.merchantName)
    }

    // ─── Task 21: ATM withdrawal → txn_kind transfer, not expense ─────────

    @Test
    fun `ATM withdrawal classifies as WITHDRAWAL type`() {
        val result = BankRulesEngine.tryParse(
            context, "NBE",
            "ATM withdrawal of EGP 1000.00 from card ending 1234"
        )
        assertNotNull(result)
        assertEquals(1000.0, result!!.amount, 0.001)
        assertEquals(TxType.WITHDRAWAL, result.txType)
    }

    // ─── Task 21: InstaPay incoming vs outgoing ────────────────────────────

    @Test
    fun `InstaPay incoming transfer parses as credit (not expense)`() {
        val result = BankRulesEngine.tryParse(
            context, "InstaPay",
            "تم استلام مبلغ 1500.00 جم من أحمد"
        )
        assertNotNull(result)
        assertEquals(1500.0, result!!.amount, 0.001)
        assertTrue(!result.isExpense)
    }

    @Test
    fun `InstaPay outgoing transfer parses as debit (expense)`() {
        val result = BankRulesEngine.tryParse(
            context, "InstaPay",
            "تم تحويل مبلغ 200 جنيه إلى محمد"
        )
        assertNotNull(result)
        assertTrue(result!!.isExpense)
    }

    // ─── Task 21: country filter — Egypt rules must not apply outside Egypt market ──

    @Test
    fun `Egypt rule does not match when active market is Saudi Arabia`() {
        MarketPrefs.setCurrentMarketForTest(Market.SAUDI_ARABIA)
        val result = BankRulesEngine.tryParse(
            context, "CIB",
            "تم خصم مبلغ 250.00 جنيه من حسابك لدى كارفور مصر الجديدة. مرجع: TX48213"
        )
        assertNull(result)
    }
}
