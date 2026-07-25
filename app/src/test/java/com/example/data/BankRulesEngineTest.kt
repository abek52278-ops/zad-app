package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات bank_rules.json — القواعد المصرية الجديدة بتتحمل وتتطابق صح، وترجع null بهدوء
 * لو مفيش sender يطابق أو الرسالة مش شكلها متوقع (مفيش تخمين).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BankRulesEngineTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

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
}
