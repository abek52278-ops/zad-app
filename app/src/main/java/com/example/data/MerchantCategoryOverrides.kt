package com.example.data

import android.content.Context

/**
 * تصحيحات تصنيف يدوية من المستخدم لتاجر معين — لو صحّح مرة، أي معاملة جاية بعدين
 * بنفس التاجر (SMS حي، backfill، أو إشعار بنك) بتاخد نفس التصنيف تلقائياً بدل
 * ما SaBankParser.classify() يخمّن تاني. تخزين محلي بسيط (SharedPreferences)،
 * مفيش AI هنا — تصحيح المستخدم نفسه هو مصدر الحقيقة.
 */
object MerchantCategoryOverrides {
    private const val PREFS = "zad_merchant_overrides"

    fun get(context: Context, merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(normalize(merchant), null)
    }

    fun set(context: Context, merchant: String?, category: String) {
        if (merchant.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(normalize(merchant), category).apply()
    }

    private fun normalize(merchant: String) = merchant.trim().lowercase()
}
