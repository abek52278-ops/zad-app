package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * جدول الدواء — مصدر حقيقة واحد، مش اتنين.
 *
 * أول اختبار على جهاز حقيقي (2026-09-06) لقى كارت دوا بيقول **"كل ١٢ ساعة"**
 * وتحتها بـ٤dp **٣ مواعيد** مهيكلة. الرقمين خرجوا من نفس الجملة: برومبت
 * `ZadViewModel` بيطلب من الموديل يملا `dosage` نص حر ("وصف الجرعة بالظبط زي ما
 * قاله المستخدم") **و** `daily_dose_count` + `dose_times` في نفس الحركة، ومحدش
 * بيقارن الاتنين. `hasInvalidDoseTime` بيصالح `doseTimes` مع `dailyDoseCount`
 * (ZadViewModel سطر ٧٧٣) — بس النص الحر خارج المصالحة دي تماماً.
 *
 * ده مش عيب شكلي زي ما اتصنّف في التقرير: جدول دوا متناقض = جرعة فايتة أو
 * مضاعفة. التستات دي بتتقاس على **الحالة اللي اتشافت في اللقطة**.
 */
class PharmacyScheduleTest {

    // ── الحالة الحرفية من اللقطة ──────────────────────────────────────────────

    @Test
    fun `the exact screenshot case cannot show two schedules`() {
        // اللي كان متسجل: نص حر بيقول كل ١٢ ساعة، وحقول مهيكلة بـ٣ مواعيد.
        val item = ZadPharmacyItem(
            name = "دوا",
            dosage = "كل ١٢ ساعة",
            dailyDoseCount = 3,
            doseTimes = "08:00,16:00,00:00",
        )
        val note = PharmacyDoseText.sanitizeDosageNote(item.dosage)

        // الملاحظة مالهاش حق تحمل توقيت. التوقيت من doseTimes وبس.
        assertNull("dosage اللي كله تكرار مالوش يتعرض خالص", note)
        assertEquals(3, item.doseTimesList().size)
    }

    @Test
    fun `frequency never survives into the note`() {
        val cases = listOf(
            "قرص كل ٨ ساعات",
            "كل 12 ساعة",
            "٣ مرات يومياً",
            "3 مرات في اليوم",
            "مرتين يومياً",
            "مرة واحدة يومياً",
            "every 8 hours",
            "twice daily",
            "3 times a day",
        )
        // التأكيد على النص نفسه مش على containsFrequency — لو الاتنين اتكسروا في نفس
        // الاتجاه، تست بيسأل containsFrequency بيعدّي وهو فاضي.
        val frequencyWords = listOf("ساعة", "ساعات", "مرات", "مرتين", "يومياً", "hours", "times", "daily")
        for (case in cases) {
            val note = PharmacyDoseText.sanitizeDosageNote(case).orEmpty()
            for (word in frequencyWords) {
                assertFalse(
                    "التكرار عدّى للملاحظة من: \"$case\" ← \"$note\" (لسه فيها \"$word\")",
                    note.contains(word, ignoreCase = true),
                )
            }
        }
    }

    // ── اللي لازم يفضل: التركيز والتعليمات ────────────────────────────────────

    @Test
    fun `strength and instructions are kept intact`() {
        assertEquals("500mg بعد الأكل", PharmacyDoseText.sanitizeDosageNote("500mg بعد الأكل"))
        assertEquals("قبل النوم", PharmacyDoseText.sanitizeDosageNote("قبل النوم"))
        assertEquals("10ml على الريق", PharmacyDoseText.sanitizeDosageNote("10ml على الريق"))
    }

    @Test
    fun `mixed note keeps the instruction and drops only the frequency`() {
        // "قرص كل ٨ ساعات بعد الأكل" — التعليمة معلومة حقيقية، التكرار مكرر.
        val note = PharmacyDoseText.sanitizeDosageNote("قرص كل ٨ ساعات بعد الأكل")
        assertTrue("التعليمة اتشالت غلط: \"$note\"", note!!.contains("بعد الأكل"))
        assertFalse("التكرار فضل في: \"$note\"", note.contains("ساعات"))
        assertFalse("التكرار فضل في: \"$note\"", note.contains("كل ٨"))
    }

    // ── الحارس اللي بيمنع الكتابة من الأساس ───────────────────────────────────

    @Test
    fun `containsFrequency flags what it should and spares what it should not`() {
        assertTrue(PharmacyDoseText.containsFrequency("كل ١٢ ساعة"))
        assertTrue(PharmacyDoseText.containsFrequency("مرتين يومياً"))
        assertTrue(PharmacyDoseText.containsFrequency("every 8 hours"))

        assertFalse(PharmacyDoseText.containsFrequency("500mg"))
        assertFalse(PharmacyDoseText.containsFrequency("بعد الأكل"))
        assertFalse(PharmacyDoseText.containsFrequency(null))
        assertFalse(PharmacyDoseText.containsFrequency("   "))
    }

    @Test
    fun `blank and null notes stay absent rather than becoming empty rows`() {
        assertNull(PharmacyDoseText.sanitizeDosageNote(null))
        assertNull(PharmacyDoseText.sanitizeDosageNote("   "))
        // فاصلة يتيمة بعد شيل التكرار مالهاش تتعرض كملاحظة
        assertNull(PharmacyDoseText.sanitizeDosageNote("، كل ٨ ساعات"))
    }
}
