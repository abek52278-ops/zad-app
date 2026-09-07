package com.example.data

/**
 * جدول الدواء له **مصدر حقيقة واحد**: `dailyDoseCount` + `doseTimes`.
 *
 * كان له اتنين. `dosage` نص حر بيتكتب من نفس الكلام اللي اتستخرجت منه الحقول
 * المهيكلة (برومبت `ZadViewModel` بيطلب الاتنين مع بعض)، فالموديل كان بيكتب
 * "كل ١٢ ساعة" في `dosage` و`daily_dose_count = 3` في نفس الحركة — والكارت
 * بيعرض الاتنين على بعد ٤dp (`PharmacyScreen` سطر ٤٧١ و٤٧٩). العميل بيشوف
 * جدولين متناقضين لدوا واحد.
 *
 * ده مش عيب شكلي. جدول دوا متناقض معناه جرعة فايتة أو جرعة مضاعفة.
 *
 * الحل: `dosage` بيرجع يبقى **ملاحظة** — تركيز وتعليمات ("500mg"، "بعد الأكل") —
 * وأي تكرار مكتوب جواه بيتشال قبل العرض. التوقيت بيتعرض من الحقول المهيكلة وبس.
 *
 * **الأنماط تحت بيانات مطابقة مش نصوص واجهة** (قاعدة i18n في CLAUDE.md): بتتطابق
 * مع كلام العميل والموديل، فترجمتها بتوقّف الكشف.
 */
object PharmacyDoseText {

    private val frequencyPatterns: List<Regex> = listOf(
        // "كل ١٢ ساعة" / "كل 8 ساعات" / "كل يومين" — الرقم عربي أو لاتيني
        Regex("""كل\s*[٠-٩\d]*\s*(ساعات|ساعة|أيام|يومين|يوم|أسابيع|أسبوع)"""),
        // "٣ مرات يومياً" / "3 مرات في اليوم"
        Regex("""[٠-٩\d]+\s*مرا?ت\s*(يومي(?:ا|اً|ًا)?|في\s*اليوم|بالي(?:و|ـو)م)?"""),
        // "مرة واحدة يومياً" / "مرتين يومياً" / "ثلاث مرات في اليوم"
        Regex("""(مرة\s*واحدة|مرتين|ثلاث\s*مرات|أربع\s*مرات)\s*(يومي(?:ا|اً|ًا)?|في\s*اليوم)?"""),
        Regex("""every\s*\d+\s*(hours?|hrs?|days?)""", RegexOption.IGNORE_CASE),
        Regex("""\d+\s*times?\s*(a|per)\s*day""", RegexOption.IGNORE_CASE),
        Regex("""(once|twice|three\s*times)\s*(a\s*day|per\s*day|daily)""", RegexOption.IGNORE_CASE),
    )

    /** فيه تكرار مكتوب في النص؟ — الحارس اللي بيمنع كتابته من الأساس. */
    fun containsFrequency(text: String?): Boolean {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return false
        return frequencyPatterns.any { it.containsMatchIn(value) }
    }

    /**
     * بيرجّع الملاحظة من غير أي تكرار — أو `null` لو مافضلش غير التكرار.
     *
     * `null` مقصودة: "كل ١٢ ساعة" لوحدها مالهاش قيمة كملاحظة، والتوقيت بيتعرض
     * من الحقول المهيكلة أصلاً، فعرض سطر فاضي أو نص مقصوص أسوأ من مفيش سطر.
     */
    fun sanitizeDosageNote(text: String?): String? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        var stripped = value
        for (pattern in frequencyPatterns) stripped = pattern.replace(stripped, " ")
        // فواصل يتيمة بعد الشيل: "قرص، كل ٨ ساعات" بتسيب "قرص،"
        val cleaned = stripped
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('،', ',', '-', '–', '—', '.', '/', ' ')
            .trim()
        return cleaned.ifBlank { null }
    }
}
