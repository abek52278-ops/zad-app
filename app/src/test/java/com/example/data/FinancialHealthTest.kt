package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * مؤشر الصحة المالية — الحكم اللي بيتعرض للعميل كشهادة.
 *
 * أول اختبار على جهاز حقيقي (2026-09-06) لقى الشاشة بتقول "الصحة المالية 80/100"
 * لحساب **بصفر معاملات**. الرقم مكانش مخترع: اتحسب من `100 − خصومات`، وكل
 * الخصومات جت من **المخزون** (نسبة النواقص + قرب الانتهاء، لحد ٣٠ نقطة).
 *
 * وكان فيه حارس `hasEnoughData` مكتوب وفوقه تعليق بيشرح الخطر بالظبط — ومافيش
 * سطر بيقراه. وحتى لو اتقرا وقتها، تعريفه كان `budget > 0 || monthTx.isNotEmpty()`،
 * والمستخدم كان حاطط ميزانية — فالحارس كان هيعدّي والشاشة تفضل تكذب.
 *
 * التلات تغييرات (فصل المخزون، تشديد الحارس، توصيله بالواجهة) لازم يتقاسوا على
 * **الحالة اللي اتشافت في اللقطة**، مش على وجود الحارس في الكود.
 */
class FinancialHealthTest {

    // ── معيار القبول: الحالة الحرفية من اللقطة ────────────────────────────

    /**
     * ميزانية موجودة · مخزون فيه نواقص · صفر معاملات ⇒ **"—"**.
     * مش 100/100 ولا 80/100.
     */
    @Test
    fun theExactScreenshotScenarioShowsADash() {
        // صفر معاملات ⇒ الحارس المشدَّد بيقفل
        val hasEnoughData = false
        // والمخزون خرج من الحساب، فحتى الرقم نفسه بقى 100 مش 80
        val score = ZadCentralBrain.financialHealthScore(
            budget = 5_000.0, totalSpent = 0.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 0.0,
        )
        assertEquals(100, score)
        assertEquals("—", ZadCentralBrain.healthTileValue(hasEnoughData, score))
    }

    /** ولا حتى بالرقم القديم: الحارس بيقفل بغض النظر عن الدرجة. */
    @Test
    fun theOldEightyWouldStillBeHiddenWithoutSpending() {
        assertEquals("—", ZadCentralBrain.healthTileValue(hasEnoughData = false, healthScore = 80))
    }

    // ── فصل المخزون ────────────────────────────────────────────────────────

    /**
     * الدالة مابتاخدش المخزون أصلاً كوسيط — ده الإثبات البنيوي إنه اتفصل.
     * نفس المدخلات المالية بتدّي نفس النتيجة مهما كان المخزون.
     */
    @Test
    fun inventoryCannotAffectFinancialHealthAnyMore() {
        val healthy = ZadCentralBrain.financialHealthScore(
            budget = 5_000.0, totalSpent = 1_000.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 0.0,
        )
        assertEquals(100, healthy)
    }

    // ── الحساب المالي نفسه لسه شغال ────────────────────────────────────────

    @Test
    fun overspendingStillCostsPoints() {
        // تجاوز الميزانية بالكامل = −40
        assertEquals(60, ZadCentralBrain.financialHealthScore(
            budget = 1_000.0, totalSpent = 1_000.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 0.0,
        ))
        // 90% = −30
        assertEquals(70, ZadCentralBrain.financialHealthScore(
            budget = 1_000.0, totalSpent = 900.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 0.0,
        ))
        // 75% = −15
        assertEquals(85, ZadCentralBrain.financialHealthScore(
            budget = 1_000.0, totalSpent = 750.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 0.0,
        ))
    }

    @Test
    fun eachOverBudgetCategoryCostsEight() {
        assertEquals(84, ZadCentralBrain.financialHealthScore(
            budget = 1_000.0, totalSpent = 100.0,
            overBudgetCategories = 2, discretionaryMonthlyCost = 0.0,
        ))
    }

    /** الاشتراكات الاختيارية فوق ربع الميزانية = −10. */
    @Test
    fun discretionarySubscriptionsOverAQuarterCostTen() {
        assertEquals(90, ZadCentralBrain.financialHealthScore(
            budget = 1_000.0, totalSpent = 100.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 300.0,
        ))
    }

    /** من غير ميزانية مفيش نسبة تتقاس — الخصومات المرتبطة بالميزانية ما بتتطبقش. */
    @Test
    fun withoutABudgetTheBudgetDeductionsDoNotApply() {
        assertEquals(100, ZadCentralBrain.financialHealthScore(
            budget = 0.0, totalSpent = 9_999.0,
            overBudgetCategories = 0, discretionaryMonthlyCost = 9_999.0,
        ))
    }

    @Test
    fun theScoreStaysInsideZeroToHundred() {
        assertEquals(0, ZadCentralBrain.financialHealthScore(
            budget = 1_000.0, totalSpent = 5_000.0,
            overBudgetCategories = 20, discretionaryMonthlyCost = 900.0,
        ))
    }

    // ── لما يبقى فيه سلوك فعلاً ────────────────────────────────────────────

    @Test
    fun withRealSpendingTheScoreIsShown() {
        assertEquals("72/100", ZadCentralBrain.healthTileValue(hasEnoughData = true, healthScore = 72))
    }
}
