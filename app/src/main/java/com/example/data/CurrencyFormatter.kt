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

    /** "1,234" أو "1.234" حسب البلد — بدون فواصل عشرية لو الرقم صحيح */
    fun format(context: Context, amount: Double): String {
        val market = MarketPrefs.getMarket(context)
        val hasFraction = amount != Math.floor(amount)
        val pattern = if (hasFraction) "#,##0.##" else "#,##0"
        val formatted = DecimalFormat(pattern, symbolsFor(market)).format(amount)
        return "$formatted ${market.currencySymbol}"
    }

    /** بدون رمز العملة — لما تحتاج الرقم لوحده (مثلاً جوه جملة عربية) */
    fun formatNumber(context: Context, amount: Double): String {
        val market = MarketPrefs.getMarket(context)
        val hasFraction = amount != Math.floor(amount)
        val pattern = if (hasFraction) "#,##0.##" else "#,##0"
        return DecimalFormat(pattern, symbolsFor(market)).format(amount)
    }

    fun symbol(context: Context): String = MarketPrefs.getMarket(context).currencySymbol
    fun currencyCode(context: Context): String = MarketPrefs.getMarket(context).currencyCode
}
