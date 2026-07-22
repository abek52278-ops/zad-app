package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تغطية للفيكس: قبل كده TxDeduplicator كان بيعتبر أي عمليتين بنفس المبلغ والاتجاه
 * خلال 10 دقائق مكررة تلقائياً — حتى لو من تاجرين مختلفين تماماً. دلوقتي البصمة
 * بتاخد اسم التاجر/العنوان في الاعتبار لو موجود.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TxDeduplicatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // ينضف SharedPreferences بين الاختبارات عشان بصمات اختبار سابق متأثرش على اللي بعده
        context.getSharedPreferences("zad_tx_dedup", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `same amount direction and merchant within window is a duplicate`() {
        assertTrue(TxDeduplicator.isNewTransaction(context, 50.0, true, "بنده"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 50.0, true, "بنده"))
    }

    @Test
    fun `same amount and direction but different merchant is NOT a duplicate`() {
        // ده كان الـ bug: شرائين بنفس القيمة من محلين مختلفين كانوا بيتحسبوا مكررين
        assertTrue(TxDeduplicator.isNewTransaction(context, 50.0, true, "بنده"))
        assertTrue(TxDeduplicator.isNewTransaction(context, 50.0, true, "ستاربكس"))
    }

    @Test
    fun `merchant comparison is case and whitespace insensitive`() {
        assertTrue(TxDeduplicator.isNewTransaction(context, 20.0, true, "  Uber  "))
        assertFalse(TxDeduplicator.isNewTransaction(context, 20.0, true, "uber"))
    }

    @Test
    fun `no disambiguator falls back to amount and direction only`() {
        assertTrue(TxDeduplicator.isNewTransaction(context, 30.0, false))
        assertFalse(TxDeduplicator.isNewTransaction(context, 30.0, false))
    }

    @Test
    fun `different direction with same amount and merchant is not a duplicate`() {
        assertTrue(TxDeduplicator.isNewTransaction(context, 40.0, true, "الراجحي"))
        assertTrue(TxDeduplicator.isNewTransaction(context, 40.0, false, "الراجحي"))
    }
}
