package com.example.data

import java.time.LocalDate

/**
 * الأهداف الجاهزة في خطوة التفعيل ← وسايط `zad_seed_life_goal` (ميجريشن 20260913220000).
 *
 * منطق صافي من غير Android ولا Supabase عشان يتغطى بتست JVM: الدالة في الداتابيز بترفض
 * العنوان الأقل من ٤ حروف أو الأطول من ٢٠٠، والمقياس الأطول من ٢٠٠، والتاريخ في الماضي —
 * فالبناء هنا بيرجّع `null` قبل ما الطلب يتبعت أصلاً، بدل ما العميل يدوس ويترفض.
 *
 * العناوين والمقاييس **بيانات بتتخزن** في agent_goals ويقراها الموديل، فبتفضل عربي (قاعدة
 * i18n في CLAUDE.md: اللي بيتخزن ما يتترجمش). أسامي الأزرار نفسها في strings.xml.
 *
 * `targetValue` = عدد المتابعات الأسبوعية، مش مبلغ: `trg_agent_goal_progress` بيزوّد
 * `current_value` بواحد مع كل مهمة مربوطة بتخلص، فالهدف بيتقاس بالمتابعات اللي خلصت.
 */
enum class LifeGoalPreset { SAVE_MONTHLY, STICK_TO_BUDGET, PAY_OFF_DEBTS, REDUCE_WASTE, CUSTOM }

data class LifeGoalSeed(
    val title: String,
    val metric: String,
    val targetValue: Int,
    val deadline: LocalDate,
)

object LifeGoalSeeds {
    /** ١٢ متابعة أسبوعية = ~٣ شهور — كفاية يبان فيها اتجاه، ومش طويل لدرجة ينسى الهدف. */
    const val WEEKLY_REVIEWS = 12
    const val HORIZON_DAYS = 90L

    private const val MIN_TITLE = 4
    private const val MAX_TEXT = 200
    private const val MAX_AMOUNT = 100_000_000.0

    fun build(
        preset: LifeGoalPreset,
        amount: Double?,
        customTitle: String?,
        today: LocalDate,
    ): LifeGoalSeed? {
        val reviews = "$WEEKLY_REVIEWS متابعة أسبوعية على ٣ شهور"
        val (title, metric) = when (preset) {
            LifeGoalPreset.SAVE_MONTHLY -> {
                if (amount == null || !amount.isFinite() || amount <= 0.0 || amount > MAX_AMOUNT) return null
                val text = amountText(amount)
                "أوفّر $text كل شهر" to "توفير $text شهريًا — $reviews"
            }
            LifeGoalPreset.STICK_TO_BUDGET -> "ألتزم بميزانية الشهر" to "الصرف جوه الميزانية — $reviews"
            LifeGoalPreset.PAY_OFF_DEBTS -> "أسدد ديوني" to "تقليل الديون المتبقية — $reviews"
            LifeGoalPreset.REDUCE_WASTE -> "أقلل هدر المطبخ" to "أصناف أقل بتبوظ قبل ما تتاكل — $reviews"
            LifeGoalPreset.CUSTOM -> {
                val t = customTitle?.trim().orEmpty()
                if (t.length < MIN_TITLE || t.length > MAX_TEXT) return null
                t to reviews
            }
        }
        if (title.length > MAX_TEXT || metric.length > MAX_TEXT) return null
        return LifeGoalSeed(title, metric, WEEKLY_REVIEWS, today.plusDays(HORIZON_DAYS))
    }

    /** ٥٠٠ مش ٥٠٠.٠ — رقم صحيح من غير كسور، وإلا كسرين. */
    internal fun amountText(amount: Double): String =
        if (amount == Math.floor(amount)) amount.toLong().toString()
        else String.format(java.util.Locale.ROOT, "%.2f", amount)
}
