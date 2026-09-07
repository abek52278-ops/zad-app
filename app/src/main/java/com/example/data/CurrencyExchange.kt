package com.example.data

import android.content.Context
import android.util.Log

/**
 * أسعار الصرف — المصدر هو `zad_fx_rates` على السيرفر، والثوابت تحت **بذرة أوفلاين بس**.
 *
 * قبل كده كانت الثوابت هي المصدر الوحيد، والجدول على السيرفر كان نسخة منها اتحطّت مرة
 * في 2026-08-15 ومحدش حدّثها. قياس 2026-09-07 لقى **٨ من ٢٠ عملة منحرفة أكتر من ٥٪**:
 * SYP بـ-٩٩٪ (إعادة تقويم)، TRY بـ+٤٣٪، LYD بـ+٣١٪، SDG بـ-٢٢٪، ILS بـ-١٧٫٥٪.
 *
 * وسبب إن العطل ماكانش باين: الرقم الثابت **صح تماماً للعملات المربوطة بالدولار** —
 * SAR/AED/QAR/JOD/KWD/OMR/BHD كلهم انحراف ٠٫٠٪. الغلط كله في العائمة، وهي عملات
 * مصر وتركيا والمغرب العربي والسودان وسوريا.
 *
 * البذرة اتحدّثت لقيم 2026-09-07 عشان أول تشغيل وأوفلاين مايبقاش على أرقام سنة فاتت،
 * بس هي بتقدم بطبيعتها — [isStale] هي اللي بتقول إن اللي بين إيدينا قديم.
 */
object CurrencyExchange {
    private const val TAG = "CurrencyExchange"
    private const val PREFS = "zad_fx_cache"
    private const val KEY_RATES = "rates"
    private const val KEY_UPDATED_AT = "updated_at"

    /** بعد كده السعر يتعتبر قديم ويتسجّل. أسبوع — العملات العائمة بتتحرك جوّه ده. */
    const val STALE_AFTER_MS: Long = 7L * 24 * 60 * 60 * 1000

    // القيمة = كام دولار أمريكي تساوي وحدة واحدة من العملة. بذرة أوفلاين، مش مصدر حقيقة.
    private val seedUsdRate: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "SAR" to 0.26666667,
        "EGP" to 0.019649473,
        "AED" to 0.27229408,
        "KWD" to 3.2440358,
        "QAR" to 0.27472527,
        "BHD" to 2.6595745,
        "OMR" to 2.6008005,
        "JOD" to 1.4104372,
        "LBP" to 0.000011173184,
        "IQD" to 0.00076265172,
        "SYP" to 0.0082173756,
        "YER" to 0.0042207236,
        "ILS" to 0.33198305,
        "LYD" to 0.15757978,
        "SDG" to 0.0021796741,
        "MAD" to 0.10692106,
        "TND" to 0.34380353,
        "DZD" to 0.007507284,
        "TRY" to 0.020635439,
    )

    @Volatile private var liveRates: Map<String, Double> = emptyMap()
    @Volatile private var liveUpdatedAtMs: Long = 0L

    /** العملات اللي عندنا سعر ليها — الحي لو موجود، وإلا البذرة. */
    private fun rates(): Map<String, Double> = liveRates.ifEmpty { seedUsdRate }

    /** `null` = عملة مش معروفة. **مش** رجوع صامت لـ١:١ زي الأول. */
    fun rateToUsd(currencyCode: String): Double? = rates()[currencyCode.trim().uppercase()]

    /**
     * بيحوّل قيمة من عملة لعملة تانية، و`null` لو أي طرف مش معروف.
     *
     * الرجوع الصامت لـ١:١ اللي كان هنا كان بيخلط عملتين مختلفتين كأنهم واحدة — ليرة
     * سورية بتتجمع مع دولار على إنهم نفس الوحدة. `null` بتجبر نقطة النداء تقرر بصراحة.
     */
    fun convert(amount: Double, fromCode: String, toCode: String): Double? {
        if (fromCode.equals(toCode, ignoreCase = true)) return amount
        val from = rateToUsd(fromCode) ?: return null
        val to = rateToUsd(toCode) ?: return null
        if (to <= 0.0 || from <= 0.0) return null
        return amount * (from / to)
    }

    /** آخر تحديث حي بالمللي ثانية، أو صفر لو لسه على البذرة. */
    fun lastUpdatedAtMs(): Long = liveUpdatedAtMs

    /** بنشتغل على أرقام قديمة؟ البذرة لوحدها بتتعتبر قديمة دايماً. */
    fun isStale(nowMs: Long = System.currentTimeMillis()): Boolean =
        liveRates.isEmpty() || (nowMs - liveUpdatedAtMs) > STALE_AFTER_MS

    // ── الكاش المحلي ─────────────────────────────────────────────────────────────

    /** بيحمّل آخر أسعار متخزّنة. بيتنده بدري عشان أول شاشة ماتشتغلش على البذرة. */
    fun loadCache(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RATES, null) ?: return
        val parsed = parseCached(raw)
        if (parsed.isNotEmpty()) {
            liveRates = parsed
            liveUpdatedAtMs = prefs.getLong(KEY_UPDATED_AT, 0L)
        }
    }

    internal fun parseCached(raw: String): Map<String, Double> = raw.split(';')
        .mapNotNull { entry ->
            val parts = entry.split('=')
            val code = parts.getOrNull(0)?.trim()?.uppercase()
            val rate = parts.getOrNull(1)?.toDoubleOrNull()
            if (code.isNullOrBlank() || rate == null || rate <= 0.0) null else code to rate
        }
        .toMap()

    internal fun encodeForCache(rates: Map<String, Double>): String =
        rates.entries.joinToString(";") { "${it.key}=${it.value}" }

    /**
     * بيجيب من السيرفر ويخزّن. **الكل أو لا شيء**: رد فاضي أو ناقص مايستبدلش اللي عندنا —
     * نفس مبدأ بوابة القبول في `zad-fx-refresh`، وللسبب نفسه: خليط من تاريخين أسوأ من
     * تاريخ واحد قديم.
     */
    suspend fun refreshFromServer(context: Context): Boolean {
        val fetched = SupabaseRepo.getFxRates()
        if (fetched == null || fetched.rates.size < seedUsdRate.size) {
            Log.w(TAG, "fx refresh skipped — got ${fetched?.rates?.size ?: 0} of ${seedUsdRate.size}")
            return false
        }
        liveRates = fetched.rates
        liveUpdatedAtMs = fetched.updatedAtMs
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RATES, encodeForCache(fetched.rates))
            .putLong(KEY_UPDATED_AT, fetched.updatedAtMs)
            .apply()
        if (isStale()) {
            // السيرفر رد، بس اللي رد بيه قديم — الكرون واقف غالباً.
            Log.w(TAG, "fx rates are live but stale: updated ${fetched.updatedAtMs}")
        }
        return true
    }

    /** للتستات — بيرجّع الحالة لبذرة نضيفة. */
    internal fun resetForTest() {
        liveRates = emptyMap()
        liveUpdatedAtMs = 0L
    }

    /** للتستات — بيحقن أسعار حية من غير شبكة. */
    internal fun setLiveForTest(rates: Map<String, Double>, updatedAtMs: Long) {
        liveRates = rates
        liveUpdatedAtMs = updatedAtMs
    }
}
