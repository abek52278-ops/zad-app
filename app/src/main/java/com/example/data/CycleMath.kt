package com.example.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Task 25 (PRODUCT_PLAN.md) — دورة الراتب بدل الشهر التقويمي. `cycleStartDay` جاي من
 * `zad_users.cycle_start_day` (nullable — null يعني لسه متكتشفش، فبيرجع لشهر تقويمي عادي،
 * مفيش افتراض إنه يوم ١). كل حساب هنا خالص من التاريخ — مالوش أي علاقة بالمعاملات نفسها،
 * BudgetMath هي اللي بتستخدم النطاق ده على المعاملات (spentInCycle/incomeInCycle).
 */
object CycleMath {

    /**
     * عطلة نهاية الأسبوع بتختلف حسب السوق — أغلب الشرق الأوسط جمعة/سبت، لكن تركيا/المغرب/
     * تونس/الجزائر/لبنان سبت/حد. last_working_day محتاج يعرف يتفاداها.
     */
    fun weekendDays(market: Market): Set<DayOfWeek> = when (market) {
        Market.TURKEY, Market.MOROCCO, Market.TUNISIA, Market.ALGERIA, Market.LEBANON ->
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        else -> setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    }

    /** بداية الدورة الحالية (أقرب تاريخ راتب <= asOf) */
    fun cycleStart(asOf: LocalDate, cycleStartDay: Int?, cycleAnchor: String, market: Market): LocalDate {
        if (cycleStartDay == null) return asOf.withDayOfMonth(1)
        val thisMonthAnchor = anchoredDay(asOf.year, asOf.monthValue, cycleStartDay, cycleAnchor, market)
        return if (asOf.isBefore(thisMonthAnchor)) {
            val prevMonth = asOf.minusMonths(1)
            anchoredDay(prevMonth.year, prevMonth.monthValue, cycleStartDay, cycleAnchor, market)
        } else {
            thisMonthAnchor
        }
    }

    /** بداية الدورة الجاية (تاريخ الراتب الجاي) — دي حدود "متاح" في Task 26 كمان، مش بس نهاية الدورة الحالية */
    fun cycleEnd(asOf: LocalDate, cycleStartDay: Int?, cycleAnchor: String, market: Market): LocalDate {
        if (cycleStartDay == null) return asOf.withDayOfMonth(1).plusMonths(1)
        val start = cycleStart(asOf, cycleStartDay, cycleAnchor, market)
        val nextMonth = start.plusMonths(1)
        return anchoredDay(nextMonth.year, nextMonth.monthValue, cycleStartDay, cycleAnchor, market)
    }

    /** طول الدورة بالأيام — مش دايماً ٣٠، بيختلف شهر عن شهر خصوصاً مع last_working_day */
    fun cycleLengthDays(cycleStart: LocalDate, cycleEnd: LocalDate): Int =
        ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt()

    /** شامل النهاردة نفسه — يوم واحد من الدورة خلص فعلاً لو النهاردة هو يوم البداية */
    fun daysElapsed(asOf: LocalDate, cycleStart: LocalDate): Int =
        ChronoUnit.DAYS.between(cycleStart, asOf).toInt() + 1

    fun daysLeft(asOf: LocalDate, cycleEnd: LocalDate): Int =
        ChronoUnit.DAYS.between(asOf, cycleEnd).toInt()

    private fun anchoredDay(year: Int, month: Int, day: Int, anchor: String, market: Market): LocalDate {
        val lastDayOfMonth = LocalDate.of(year, month, 1).lengthOfMonth()
        var date = LocalDate.of(year, month, day.coerceAtMost(lastDayOfMonth))
        if (anchor == "last_working_day") {
            val weekend = weekendDays(market)
            while (date.dayOfWeek in weekend) date = date.minusDays(1)
        }
        return date
    }
}
