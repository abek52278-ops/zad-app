package com.example.data

/**
 * Task 28 (PRODUCT_PLAN.md) — informative dismissal. Pure function so the note text is
 * unit-testable without touching Supabase; [SupabaseRepo.dismissInsightWithReason] is
 * the only caller. `wrong_data` gets a distinctly higher confidence and its own scope
 * ("data_quality") — it's a free bug report and the whole point of Task 28 is that this
 * signal must not be thrown away with the rest of "dismissed and forgotten".
 */
object DismissalMemory {

    data class Note(val scope: String, val note: String, val confidence: Double)

    /** null لو reason مش من الثلاثة المعروفة (not_relevant | wrong_data | timing) */
    fun noteFor(reason: String, insight: ZadInsight): Note? {
        val subject = insight.aboutItem ?: insight.title
        return when (reason) {
            "not_relevant" -> Note("dismissal", "مش مهتم بتنبيهات زي \"$subject\"", 0.5)
            "wrong_data" -> Note("data_quality", "العميل قال إن \"$subject\" غلط — البيانات المصدر محتاجة مراجعة", 0.7)
            "timing" -> Note("dismissal", "عرف بالفعل عن \"$subject\" وقت الرفض ده", 0.3)
            else -> null
        }
    }
}
