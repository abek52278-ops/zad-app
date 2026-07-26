package com.example.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Task 19.0 — الحساب الوحيد لـ "المصروف الشهري" و"المتبقي" في التطبيق كله. أي مكان
 * محتاج الرقمين دول بينده هنا بدل ما يعيد الفلترة بنفسه — عشان اختلاف بسيط في منطق
 * الفلترة (تنسيق تاريخ مختلف، حد شهر مختلف) بين مكانين ما يبقاش معناه رقمين مختلفين
 * لنفس المفهوم. مقصود يكون شهر تقويمي عادي لحد ما Task 25 (دورة الراتب) يحل محله —
 * كل استدعاء هنا موثّق ليه بديل مؤقت، مش نهائي.
 *
 * الرصيد نفسه (monthlyLimit) مايتخزّنش هنا ولا في أي مكان — بييجي من
 * SupabaseRepo.getMonthlyLimit() في كل مرة، عشان يفضل مشتق مش متراكم.
 */
object BudgetMath {

    private fun txDate(tx: ZadTransaction): LocalDate? = tx.createdAt?.let {
        try {
            Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (e: Exception) {
            try { LocalDate.parse(it.take(10)) } catch (e2: Exception) { null }
        }
    }

    fun spentThisMonth(transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double {
        val monthStart = asOf.withDayOfMonth(1)
        return transactions
            .filter { it.isExpense && (txDate(it) ?: asOf) >= monthStart }
            .sumOf { it.amount }
    }

    fun incomeThisMonth(transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double {
        val monthStart = asOf.withDayOfMonth(1)
        return transactions
            .filter { !it.isExpense && (txDate(it) ?: asOf) >= monthStart }
            .sumOf { it.amount }
    }

    /** monthlyLimit من zad_users.monthly_limit — الصفر أو الأقل بيرجع 0.0، مفيش "متبقي" لسقف مش معروف */
    fun remaining(monthlyLimit: Double, transactions: List<ZadTransaction>, asOf: LocalDate = LocalDate.now()): Double {
        if (monthlyLimit <= 0.0) return 0.0
        return monthlyLimit - spentThisMonth(transactions, asOf) + incomeThisMonth(transactions, asOf)
    }
}
