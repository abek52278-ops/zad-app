package com.example.ads

import android.content.Context
import android.util.Log

/**
 * AdGateManager — مُعطَّل بالكامل بقرار المشروع (UI_ARCHITECTURE_SPEC.md §3.5).
 * سياسة التطبيق: لا إعلانات إطلاقاً (Zero-Ads Policy).
 * بوابة الوسائط مفتوحة دائماً دون الحاجة لمشاهدة أي إعلانات.
 */
object AdGateManager {
    private const val TAG = "AdGateManager"

    /** بوابة الوسائط مفتوحة دائماً بموجب سياسة صفر إعلانات */
    fun isMediaPassActive(context: Context): Boolean = true

    fun mediaPassRemainingMs(context: Context): Long = 86400000L

    /** منح فوري وهمي دون عرض إعلانات */
    suspend fun claimMediaPassWithAd(context: Context): Long? {
        Log.d(TAG, "Zero-ads policy: media pass auto-granted without ads")
        return System.currentTimeMillis() + 86400000L
    }

    /** تصفير الحالة المحلية */
    fun resetLocal(context: Context) {
        // no-op
    }
}