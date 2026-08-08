package com.example.data

/**
 * جدول أسعار صرف ثابت (مش لايف — التطبيق مالوش feed أسعار صرف أو endpoint لده) لتحويل
 * البادجت/الحدود المحفوظة لما المستخدم يبدّل عملة على حساب فيه أرقام بالفعل (تبديل يدوي من
 * البروفايل، أو قبول اقتراح السفر في TravelBanner). قبل كده كان تبديل العملة بيغيّر
 * الـ label بس ويسيب الرقم زي ما هو — ١٠٠٠ ريال بقت ١٠٠٠ جنيه من غير تحويل.
 */
object CurrencyExchange {
    // القيمة = كام دولار أمريكي تساوي وحدة واحدة من العملة دي (تقريبي، محدّث يدوياً)
    private val usdRate: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "SAR" to 0.2667,
        "EGP" to 0.0204,
        "AED" to 0.2723,
        "KWD" to 3.2500,
        "QAR" to 0.2747,
        "BHD" to 2.6525,
        "OMR" to 2.5974,
        "JOD" to 1.4104,
        "LBP" to 0.0000112,
        "IQD" to 0.000763,
        "SYP" to 0.0000769,
        "YER" to 0.0040,
        "ILS" to 0.2740,
        "LYD" to 0.2058,
        "SDG" to 0.0017,
        "MAD" to 0.1002,
        "TND" to 0.3210,
        "DZD" to 0.0074,
        "TRY" to 0.0295
    )

    private fun rateToUsd(currencyCode: String): Double = usdRate[currencyCode] ?: 1.0

    /** بيحوّل قيمة من عملة لعملة تانية. عملة مش معروفة بترجع نفس القيمة (معدل ١:١). */
    fun convert(amount: Double, fromCode: String, toCode: String): Double {
        if (fromCode == toCode) return amount
        val toRate = rateToUsd(toCode)
        if (toRate == 0.0) return amount
        return amount * (rateToUsd(fromCode) / toRate)
    }
}
