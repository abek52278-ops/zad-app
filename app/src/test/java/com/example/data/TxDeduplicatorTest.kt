package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
        // وفي كل اختبار مستخدم (لو كان فيه) محتاج أنه يختلف عن الاختبارات التانية
        // لكن CurrentUser ما بيتغير خلال الـ test run — بتحت الرطر بيشتغل كويس
        // Task 20 — TxDeduplicator object واحد مشترك بين كل الاختبارات، فأي اختبار غيّر
        // window/tolerance لازم يترجع للـ defaults قبل الاختبار اللي بعده
        TxDeduplicator.resetToDefaultsForTest()
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

    @Test
    fun `matching externalRef is a duplicate even with a different merchant string`() {
        // نفس المرجع البنكي = نفس العملية أكيد، حتى لو التاجر اتقرا مختلف شكلياً من قناتين
        assertTrue(TxDeduplicator.isNewTransaction(context, 250.0, true, "كارفور", externalRef = "TX48213"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 250.0, true, "Carrefour Egypt", externalRef = "TX48213"))
    }

    @Test
    fun `externalRef match is case-insensitive`() {
        assertTrue(TxDeduplicator.isNewTransaction(context, 75.0, true, externalRef = "abc123"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 75.0, true, externalRef = "ABC123"))
    }

    @Test
    fun `different externalRef with same amount and merchant is still a duplicate by the old rule`() {
        // ref مختلف مش دليل كفاية إنها عملية جديدة — لسه المبلغ+التاجر بيمسكها زي الأول
        assertTrue(TxDeduplicator.isNewTransaction(context, 60.0, true, "فوري", externalRef = "REF001"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 60.0, true, "فوري", externalRef = "REF002"))
    }

    @Test
    fun `amount within 5 percent tolerance is a duplicate`() {
        // 100 و 104 (4% difference) — داخل التسامح ±5%
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.0, true, "كارفور"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 104.0, true, "كارفور"))
    }

    @Test
    fun `amount beyond 5 percent tolerance is NOT a duplicate`() {
        // 100 و 106 (6% difference) — خارج التسامح ±5%
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.0, true, "كارفور"))
        assertTrue(TxDeduplicator.isNewTransaction(context, 106.0, true, "كارفور"))
    }

    @Test
    fun `tolerance is symmetric (order independent)`() {
        // 100.5 first, then 104.8 stored (both should see each other as near)
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.5, true, "محل"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 104.8, true, "محل"))
        // difference = 4.3, max = 104.8, ratio = 4.3/104.8 = 4.1% < 5%
    }

    @Test
    fun `WINDOW_MS is 36 hours`() {
        assertEquals(36 * 60 * 60 * 1000L, TxDeduplicator.WINDOW_MS)
    }

    // ── Task 20: config-driven window/tolerance ────────────────────────────

    @Test
    fun `WINDOW_MS follows a locale-configured window`() {
        TxDeduplicator.applyLocaleConfigForTest(windowHours = 12, tolerancePct = 5.0)
        assertEquals(12 * 60 * 60 * 1000L, TxDeduplicator.WINDOW_MS)
    }

    @Test
    fun `tolerance boundary — exactly the configured percent is still a duplicate`() {
        // 5.0 هو أقصى قيمة مسموحة (applyLocaleConfigForTest نفسها بتقفل عند MAX_TOLERANCE_PCT)،
        // فمينفعش نستخدم قيمة أعلى هنا عشان نختبر الحد فعلاً، مش القيمة المقفولة
        TxDeduplicator.applyLocaleConfigForTest(windowHours = 36, tolerancePct = 5.0)
        // abs(100-95)/max(100,95) = 5/100 = 5% بالظبط، والشرط <= فبيتحسب مكرر
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.0, true, "محل"))
        assertFalse(TxDeduplicator.isNewTransaction(context, 95.0, true, "محل"))
    }

    @Test
    fun `tolerance boundary — just beyond the configured percent is NOT a duplicate`() {
        TxDeduplicator.applyLocaleConfigForTest(windowHours = 36, tolerancePct = 5.0)
        // abs(100-94)/max(100,94) = 6/100 = 6% > 5% → مش مكرر
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.0, true, "محل"))
        assertTrue(TxDeduplicator.isNewTransaction(context, 94.0, true, "محل"))
    }

    @Test
    fun `tolerance is clamped at 5 percent even if config asks for more`() {
        // الوثيقة صراحة: "Do not raise tolerance above 5%". لو صف على السيرفر بيطلب
        // نسبة أعلى (غلطة إدخال أو تجربة)، القفل المحلي لازم يمنعها.
        TxDeduplicator.applyLocaleConfigForTest(windowHours = 36, tolerancePct = 20.0)
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.0, true, "محل"))
        // 8% فرق — كان هيتحسب مكرر لو الـ 20% اتقبلت، بس القفل بيوقفها عند 5%
        assertTrue(TxDeduplicator.isNewTransaction(context, 108.0, true, "محل"))
    }

    @Test
    fun `negative tolerance from a bad config row is clamped to zero, not negative`() {
        TxDeduplicator.applyLocaleConfigForTest(windowHours = 36, tolerancePct = -5.0)
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.0, true, "محل"))
        // حتى فرق تافه (0.5%) لازم يتحسب مش مكرر لو التسامح اتقفل عند صفر
        assertTrue(TxDeduplicator.isNewTransaction(context, 100.5, true, "محل"))
    }
}
