package com.example.data

import android.content.Context
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * تنسيق مركزي للمبالغ حسب البلد المختار — يحل محل كل "${amount} ر.س" المكتوبة يدوياً.
 * بيفرض أرقام غربية (0-9) دايماً بدل ما يعتمد على locale.getInstance() اللي ممكن يرجع
 * أرقام هندية شرقية لبعض إعدادات ar-SA على أندرويد — نفس شكل الأرقام اللي المستخدمين متعودين عليه.
 */
object CurrencyFormatter {

    private val westernDigits = DecimalFormatSymbols(Locale.US)
    private val europeanDigits = DecimalFormatSymbols(Locale("tr", "TR")).apply {
        // تركيا: فاصل الآلاف نقطة، العشري فاصلة — لكن أرقام غربية زي الأصل
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private fun symbolsFor(market: Market) = if (market.useEuropeanNumberFormat) europeanDigits else westernDigits

    // بعد .asMoney() الفرق المتبقي بين رقم ومقربه لأقرب صحيح إما صفر فعلي أو ضجيج تمثيل
    // binary floating point (~1e-10) — مش 0.005 (سماحية "نفس العملية" مختلفة تماماً عن
    // "هل الرقم ده صحيح؟"). لو استخدمنا 0.005 هنا، مبلغ زي 99.997 كان هيتعرض "100" غلط.
    private fun hasVisibleFraction(amount: Double): Boolean =
        kotlin.math.abs(amount - Math.round(amount)) > 1e-6

    /**
     * "1,234" أو "1.234" حسب البلد — بدون فواصل عشرية لو الرقم صحيح.
     * بتقرأ MarketPrefs.currentMarket (Compose state) مباشرة بدل getMarket(context) —
     * ده اللي بيخلي أي Text() بتنادي format() تتحدث فوراً لما المستخدم يبدّل السوق، من غير
     * ما تحتاج سبب تاني للـ recomposition. context لسه محتاج له باقي الدوال في الملف
     * (تنسيقات تانية بتاخده)، فسايبينه في الـ signature عشان الـ ١٧٩ نداء الحالي متتغيرش.
     */
    fun format(context: Context, amount: Double): String {
        val market = MarketPrefs.currentMarket
        val rounded = amount.asMoney()
        val pattern = if (hasVisibleFraction(rounded)) "#,##0.##" else "#,##0"
        val formatted = DecimalFormat(pattern, symbolsFor(market)).format(rounded)
        return "$formatted ${market.currencySymbol}"
    }

    /** بدون رمز العملة — لما تحتاج الرقم لوحده (مثلاً جوه جملة عربية) */
    fun formatNumber(context: Context, amount: Double): String {
        val market = MarketPrefs.currentMarket
        val rounded = amount.asMoney()
        val pattern = if (hasVisibleFraction(rounded)) "#,##0.##" else "#,##0"
        return DecimalFormat(pattern, symbolsFor(market)).format(rounded)
    }

    fun symbol(context: Context): String = MarketPrefs.currentMarket.currencySymbol
    fun currencyCode(context: Context): String = MarketPrefs.currentMarket.currencyCode

    // مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — رمز/كود العملة لمعاملة بعينها، مش
    // للـ Market الحالي. الأعمدة الثلاثة اللي زاد بيدعمها فعلياً — SAR/EGP/TRY، نفس مجموعة
    // Market enum. رمز مش معروف (كود من مصدر خارجي زي بنك أجنبي) بيتعرض زي ما هو بدل ما
    // يتحول لرمز يوهم إنه اتعرف.
    private fun symbolForCode(code: String): String = when (code.uppercase()) {
        "SAR" -> "ر.س"
        "EGP" -> "ج.م"
        "TRY" -> "₺"
        "AED" -> "د.إ"
        "KWD" -> "د.ك"
        "QAR" -> "ر.ق"
        "BHD" -> "د.ب"
        "OMR" -> "ر.ع"
        "JOD" -> "د.أ"
        "LBP" -> "ل.ل"
        "IQD" -> "د.ع"
        "SYP" -> "ل.س"
        "YER" -> "ر.ي"
        "ILS" -> "₪"
        "LYD" -> "د.ل"
        "SDG" -> "ج.س"
        "MAD" -> "د.م"
        "TND" -> "د.ت"
        "DZD" -> "د.ج"
        else -> code
    }

    /**
     * تنسيق معاملة بعملتها الفعلية (tx.currency)، مش عملة الـ Market الحالي. معاملة
     * اتسجلت وأنت مسافر (رسالة بنك مصري وسوقك المختار سعودي، مثلاً) كانت بتتعرض "ر.س"
     * رغم إن الرسالة نفسها بتقول EGP بالظبط — نفس بق مرحلة ٠ب بالظبط، بس للعملة مش للسقف.
     * tx.currency == null (صف قديم أو رسالة من غير رمز عملة صريح) بيرجع لسلوك
     * format(context, amount) القديم تماماً — عملة الـ Market الحالي.
     */
    fun format(context: Context, tx: ZadTransaction): String {
        val txCurrency = tx.currency
        if (txCurrency == null) return format(context, tx.amount)

        val market = MarketPrefs.getMarket(context)
        val useEuropean = txCurrency.equals("TRY", ignoreCase = true)
        val rounded = tx.amount.asMoney()
        val pattern = if (hasVisibleFraction(rounded)) "#,##0.##" else "#,##0"
        val formatted = DecimalFormat(pattern, if (useEuropean) europeanDigits else westernDigits).format(rounded)
        // عملة المعاملة بتتوافق مع عملة الـ Market الحالي؟ استخدم رمزه المألوف، وإلا اعرض
        // كود ISO زي ما هو — تجنّب رمز مضلل لعملة السوق ده عمره ما شافها.
        val symbol = if (txCurrency.equals(market.currencyCode, ignoreCase = true)) market.currencySymbol else symbolForCode(txCurrency)
        return "$formatted $symbol"
    }
}
