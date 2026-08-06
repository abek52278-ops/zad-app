package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * تغطية لفلتر [InventoryFlowEngine.looksLikeNonProductName] — الدفاع اللي بيشيل صفوف
 * مخزون قديمة اسمها لقب/وظيفة (زي "مصمم كوسموس") بدل منتج حقيقي، بعد ما اتلقت مباشرة
 * من رد شات AI غير مُتحقق منه قبل الفيكس.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InventoryFlowEngineTest {

    @Test
    fun `flags a name containing a job-title word`() {
        assertTrue(InventoryFlowEngine.looksLikeNonProductName("مصمم كوسموس"))
        assertTrue(InventoryFlowEngine.looksLikeNonProductName("دكتور أحمد"))
        assertTrue(InventoryFlowEngine.looksLikeNonProductName("مهندسة سارة"))
    }

    @Test
    fun `does not flag real grocery product names`() {
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("بيض"))
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("حليب المراعي"))
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("زيت زيتون"))
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("كريم شعر"))
    }

    @Test
    fun `does not flag a plain person name with no job-title word (known filter limitation)`() {
        // فلتر عالي الدقة بيمسك ألقاب وظيفية بس، مش أي اسم شخص — عشان يتجنب حذف
        // منتجات حقيقية غلط. أسماء زي دي المفروض تتمنع أصلاً بتأكيد الشات (Commit 1)
        // مش بالفلتر الدفاعي ده.
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("جمال كامل"))
    }

    @Test
    fun `namesMatch still treats Arabic definite article and variants as equal`() {
        assertTrue(InventoryFlowEngine.namesMatch("الحليب", "حليب"))
        assertTrue(InventoryFlowEngine.namesMatch("حليب المراعي", "المراعي حليب"))
    }

    // ─── Task 23: isStagnant ────────────────────────────────────────────────

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun daysAgo(days: Long): String = Instant.now().minus(days, ChronoUnit.DAYS).toString()

    /** يكتب حدث مباشرة في نفس تنسيق ConsumptionLearner (epoch days مفصولة بفاصلة) — عشان نقدر نحاكي حدث "قديم" من غير ما نستنى ٣٠ يوم فعلياً */
    private fun seedEvent(prefix: String, itemName: String, daysAgo: Long) {
        val key = prefix + InventoryFlowEngine.normalizeName(itemName)
        val epochDay = LocalDate.now().minusDays(daysAgo).toEpochDay()
        context.getSharedPreferences("zad_consumption", android.content.Context.MODE_PRIVATE)
            .edit().putString(key, epochDay.toString()).apply()
    }

    @Test
    fun `a brand new item with no events is not stagnant yet`() {
        val item = ZadInventory(itemName = "معلبة تونة جديدة", quantity = 3, createdAt = daysAgo(1))
        assertFalse(InventoryFlowEngine.isStagnant(context, item))
    }

    @Test
    fun `an item added 40 days ago with zero consumption samples is stagnant`() {
        val item = ZadInventory(itemName = "تونة راكدة", quantity = 5, createdAt = daysAgo(40))
        assertTrue(InventoryFlowEngine.isStagnant(context, item))
    }

    @Test
    fun `an item actively repurchased recently is not stagnant even if added long ago`() {
        val item = ZadInventory(itemName = "أرز متجدد", quantity = 5, createdAt = daysAgo(60))
        seedEvent("buy_", "أرز متجدد", daysAgo = 3)
        assertFalse(InventoryFlowEngine.isStagnant(context, item))
    }

    @Test
    fun `an item with any consumption sample ever is never stagnant, even an old one`() {
        val item = ZadInventory(itemName = "زيت قديم الاستهلاك", quantity = 5, createdAt = daysAgo(90))
        seedEvent("use_", "زيت قديم الاستهلاك", daysAgo = 89)
        assertFalse(InventoryFlowEngine.isStagnant(context, item))
    }

    @Test
    fun `a fully depleted item (quantity zero) is never stagnant`() {
        val item = ZadInventory(itemName = "فاضي خالص", quantity = 0, createdAt = daysAgo(90))
        assertFalse(InventoryFlowEngine.isStagnant(context, item))
    }

    // ─── مرحلة ٣ (docs/agent/PLAN_2026_08_06_rebuild.md): getCheckInCandidates + snooze ──

    /** بيحضّر صنف متوقّع يخلص خلال يوم أو أقل — interval محسوب وشراء آخر قبل الـ interval بالظبط */
    private fun seedPredictedDepleted(itemName: String, intervalDays: Long = 10) {
        val prefs = context.getSharedPreferences("zad_consumption", android.content.Context.MODE_PRIVATE)
        val normalized = InventoryFlowEngine.normalizeName(itemName)
        prefs.edit()
            .putFloat("smoothed_interval_$normalized", intervalDays.toFloat())
            .putString("buy_$normalized", LocalDate.now().minusDays(intervalDays).toEpochDay().toString())
            .apply()
    }

    @Test
    fun `getCheckInCandidates includes an item predicted to run out within a day`() {
        val item = ZadInventory(itemName = "حليب متوقع يخلص", quantity = 2)
        seedPredictedDepleted(item.itemName)
        val candidates = InventoryFlowEngine.getCheckInCandidates(context, listOf(item))
        assertTrue(candidates.any { it.item.itemName == item.itemName })
    }

    @Test
    fun `snoozeCheckIn removes the item from candidates until the snooze window passes`() {
        val item = ZadInventory(itemName = "بيض مؤجل", quantity = 2)
        seedPredictedDepleted(item.itemName)
        assertTrue(InventoryFlowEngine.getCheckInCandidates(context, listOf(item)).isNotEmpty())

        ConsumptionLearner.snoozeCheckIn(context, item.itemName)

        assertTrue(ConsumptionLearner.isSnoozed(context, item.itemName))
        assertTrue(InventoryFlowEngine.getCheckInCandidates(context, listOf(item)).isEmpty())
    }

    @Test
    fun `an item with no snooze recorded is not snoozed`() {
        assertFalse(ConsumptionLearner.isSnoozed(context, "صنف عادي ما اتسألش عليه أبداً"))
    }
}
