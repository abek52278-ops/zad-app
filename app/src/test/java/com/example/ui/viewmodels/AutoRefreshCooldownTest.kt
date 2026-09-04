package com.example.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الحارس اللي بيمنع `LaunchedEffect(Unit)` من إنه يولّد نداء نموذج كل مرة الشاشة
 * تدخل الـcomposition. الاختبار بيحاكي الحارس زي ما ZadViewModel بيستعمله بالظبط:
 * وقت آخر تشغيل بيتخزن **بس لما النداء يشتغل فعلاً**.
 */
class AutoRefreshCooldownTest {

    private val cooldownMs = 5 * 60 * 1000L

    /** بيرجع عدد المرات اللي النداء اشتغل فيها فعلاً عبر المواقيت المدخلة. */
    private fun runsAcross(openTimes: List<Long>): Int {
        var lastRunAt: Long? = null
        var runs = 0
        openTimes.forEach { now ->
            if (autoRefreshShouldRun(lastRunAt, now, cooldownMs)) {
                lastRunAt = now
                runs++
            }
        }
        return runs
    }

    @Test
    fun `first screen open always runs`() {
        assertTrue(autoRefreshShouldRun(lastRunAt = null, now = 0L, cooldownMs = cooldownMs))
    }

    @Test
    fun `three opens inside the window produce exactly one call`() {
        // فتح الرئيسية، رجوع بعد ٣٠ ثانية، رجوع تاني بعد دقيقتين.
        assertEquals(1, runsAcross(listOf(0L, 30_000L, 120_000L)))
    }

    @Test
    fun `open after the window runs again`() {
        // الرابع بره الـ٥ دقايق — لازم يجدد، الحارس مش بيقفل للأبد.
        assertEquals(2, runsAcross(listOf(0L, 30_000L, 120_000L, 300_000L)))
    }

    @Test
    fun `exactly at the window boundary runs`() {
        assertTrue(autoRefreshShouldRun(lastRunAt = 0L, now = cooldownMs, cooldownMs = cooldownMs))
    }

    @Test
    fun `inside the window is blocked`() {
        assertFalse(autoRefreshShouldRun(lastRunAt = 0L, now = cooldownMs - 1, cooldownMs = cooldownMs))
    }

    @Test
    fun `force always runs even inside the window`() {
        // حفظ تعديل ميزانية يدوي — فعل واحد من العميل، نداء واحد، من غير انتظار.
        assertTrue(autoRefreshShouldRun(lastRunAt = 0L, now = 1_000L, cooldownMs = cooldownMs, force = true))
    }
}
