package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تغطية للفيكس: قبل كده dedupe الاستيراد كان مبلغ+اتجاه+يوم بس — يومين معاملات مختلفة
 * فعلاً بنفس المبلغ في نفس اليوم كانت هتتحسب مكررة غلط وتتشال بصمت (فقدان بيانات، مش تكرار).
 */
class StatementCsvImporterTest {

    private fun tx(amount: Double, isExpense: Boolean, day: String, title: String = "", merchant: String? = null) =
        ZadTransaction(amount = amount, title = title, isExpense = isExpense, createdAt = "${day}T10:00:00Z", merchantName = merchant)

    private fun row(amount: Double, isExpense: Boolean, day: String, title: String) =
        StatementCsvImporter.PreviewRow(rowIndex = 0, date = day, title = title, amount = amount, isExpense = isExpense, category = "أخرى", hasError = false)

    @Test
    fun `same amount, direction, day and compatible description is a duplicate`() {
        val existing = tx(50.0, true, "2026-07-01", title = "كارفور مصر الجديدة")
        val imported = row(50.0, true, "2026-07-01", "كارفور")
        assertTrue(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }

    @Test
    fun `same amount, direction and day but different merchant is NOT a duplicate`() {
        // ده كان الباج: يومين قهوة بنفس السعر في نفس اليوم من محلين مختلفين كانوا بيضيعوا
        val existing = tx(20.0, true, "2026-07-01", title = "ستاربكس")
        val imported = row(20.0, true, "2026-07-01", "كوستا")
        assertFalse(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }

    @Test
    fun `generic imported title always matches (blank merchant passes)`() {
        val existing = tx(100.0, true, "2026-07-01", title = "أمازون مصر")
        val imported = row(100.0, true, "2026-07-01", "معاملة مستوردة")
        assertTrue(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }

    @Test
    fun `different day is not a duplicate even with same amount and merchant`() {
        val existing = tx(50.0, true, "2026-07-01", title = "كارفور")
        val imported = row(50.0, true, "2026-07-02", "كارفور")
        assertFalse(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }

    @Test
    fun `different direction is not a duplicate`() {
        val existing = tx(50.0, true, "2026-07-01", title = "راتب")
        val imported = row(50.0, false, "2026-07-01", "راتب")
        assertFalse(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }

    @Test
    fun `amount within 0-005 tolerance still counts as duplicate`() {
        val existing = tx(99.995, true, "2026-07-01", title = "نون")
        val imported = row(100.0, true, "2026-07-01", "نون")
        assertTrue(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }

    @Test
    fun `merchantName on the existing transaction takes priority over its title`() {
        val existing = tx(30.0, true, "2026-07-01", title = "مصرف الإنماء: شراء", merchant = "أوبر")
        val imported = row(30.0, true, "2026-07-01", "أوبر")
        assertTrue(StatementCsvImporter.isDuplicateOfExisting(existing, imported))
    }
}
