package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Task 25 — دورة الراتب. cycleStartDay=null لازم يرجع بالظبط نفس حدود شهر تقويمي عادي،
 * عشان أي مستخدم لسه ماكتشفش له دورة يفضل شغال زي ما كان بالظبط، مفيش افتراض ضمني.
 */
class CycleMathTest {

    @Test
    fun `null cycleStartDay falls back to calendar month start`() {
        val asOf = LocalDate.of(2026, 7, 15)
        assertEquals(LocalDate.of(2026, 7, 1), CycleMath.cycleStart(asOf, null, "day_of_month", Market.SAUDI_ARABIA))
        assertEquals(LocalDate.of(2026, 8, 1), CycleMath.cycleEnd(asOf, null, "day_of_month", Market.SAUDI_ARABIA))
    }

    @Test
    fun `asOf after this month's anchor day uses this month as cycle start`() {
        // راتب يوم ٢٨، النهاردة أول أغسطس — الدورة الحالية بدأت ٢٨ يوليو
        val asOf = LocalDate.of(2026, 8, 1)
        val start = CycleMath.cycleStart(asOf, 28, "day_of_month", Market.EGYPT)
        assertEquals(LocalDate.of(2026, 7, 28), start)
    }

    @Test
    fun `asOf before this month's anchor day uses previous month as cycle start`() {
        // راتب يوم ٢٨، النهاردة ٥ أغسطس (قبل الـ٢٨) — الدورة الحالية لسه بدأت من ٢٨ يوليو
        val asOf = LocalDate.of(2026, 8, 5)
        val start = CycleMath.cycleStart(asOf, 28, "day_of_month", Market.EGYPT)
        assertEquals(LocalDate.of(2026, 7, 28), start)
        val end = CycleMath.cycleEnd(asOf, 28, "day_of_month", Market.EGYPT)
        assertEquals(LocalDate.of(2026, 8, 28), end)
    }

    @Test
    fun `cycle start day beyond a short month clamps to the last real day`() {
        // راتب يوم ٣١ — فبراير مفيهوش ٣١، لازم يترخّم على آخر يوم فعلي في الشهر
        val asOf = LocalDate.of(2026, 2, 20)
        val start = CycleMath.cycleStart(asOf, 31, "day_of_month", Market.SAUDI_ARABIA)
        assertEquals(LocalDate.of(2026, 1, 31), start)
    }

    @Test
    fun `last_working_day shifts a Saudi Friday payday back to Thursday`() {
        // ٧ أغسطس ٢٠٢٦ يوم جمعة — عطلة السعودية/مصر (جمعة-سبت)، المفروض يترجع للخميس
        val start = CycleMath.cycleStart(LocalDate.of(2026, 8, 7), 7, "last_working_day", Market.SAUDI_ARABIA)
        assertEquals(LocalDate.of(2026, 8, 6), start) // الخميس اللي قبلها
    }

    @Test
    fun `last_working_day shifts a Turkish Saturday payday back to Friday`() {
        // نفس اليوم (٧ أغسطس ٢٠٢٦ = جمعة) مش عطلة في تركيا — بس لو وقعت السبت هي اللي بتترجع
        val start = CycleMath.cycleStart(LocalDate.of(2026, 8, 8), 8, "last_working_day", Market.TURKEY)
        // ٨ أغسطس ٢٠٢٦ سبت — عطلة تركيا، يترجع للجمعة ٧
        assertEquals(LocalDate.of(2026, 8, 7), start)
    }

    @Test
    fun `daysElapsed counts today itself as elapsed`() {
        val start = LocalDate.of(2026, 7, 28)
        assertEquals(1, CycleMath.daysElapsed(start, start))
        assertEquals(5, CycleMath.daysElapsed(LocalDate.of(2026, 8, 1), start))
    }

    @Test
    fun `daysLeft counts down to but not including cycle end`() {
        val end = LocalDate.of(2026, 8, 28)
        assertEquals(28, CycleMath.daysLeft(LocalDate.of(2026, 7, 31), end))
        assertEquals(0, CycleMath.daysLeft(end, end))
    }
}
