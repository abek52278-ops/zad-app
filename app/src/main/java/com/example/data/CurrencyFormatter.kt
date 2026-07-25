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

    /** "1,234" أو "1.234" حسب البلد — بدون فواصل عشرية لو الرقم صحيح */
    fun format(context: Context, amount: Double): String {
        val market = MarketPrefs.getMarket(context)
        val rounded = amount.asMoney()
        val pattern = if (hasVisibleFraction(rounded)) "#,##0.##" else "#,##0"
        val formatted = DecimalFormat(pattern, symbolsFor(market)).format(rounded)
        return "$formatted ${market.currencySymbol}"
    }

    /** بدون رمز العملة — لما تحتاج الرقم لوحده (مثلاً جوه جملة عربية) */
    fun formatNumber(context: Context, amount: Double): String {
        val market = MarketPrefs.getMarket(context)
        val rounded = amount.asMoney()
        val pattern = if (hasVisibleFraction(rounded)) "#,##0.##" else "#,##0"
        return DecimalFormat(pattern, symbolsFor(market)).format(rounded)
    }

    fun symbol(context: Context): String = MarketPrefs.getMarket(context).currencySymbol
    fun currencyCode(context: Context): String = MarketPrefs.getMarket(context).currencyCode
}
