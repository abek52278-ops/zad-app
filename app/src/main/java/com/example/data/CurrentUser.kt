package com.example.data

import android.content.Context

/**
 * كاش محلي بسيط لـ id المستخدم الحالي — قراءة SharedPreferences فقط، بلا لمس لـ
 * SupabaseRepo.client. لازم كده عشان BudgetTracker/TxDeduplicator (أدوات تخزين محلي بحتة)
 * ما تبقاش معتمدة على تهيئة عميل الشبكة بتاع Supabase؛ عملياً ده اتأكد لما اختبارات
 * Robolectric فشلت في تهيئة SupabaseRepo.client (Auth plugin محتاج Context حقيقي مش متوفر
 * تحت @Config(manifest = Config.NONE)) — استدعاء SupabaseRepo.client من أدوات تخزين محلي
 * كان تبعية غلط من الأساس، مش بس مشكلة اختبار.
 * يتحدّث من: MainActivity بعد awaitInitialization()، وSupabaseRepo.signIn()/signUp() بعد النجاح.
 */
object CurrentUser {
    private const val PREFS = "zad_prefs"
    private const val KEY = "current_user_id"

    fun cache(context: Context, userId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, userId).apply()
    }

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
