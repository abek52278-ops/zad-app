package com.example.ads

import android.content.Context
import android.util.Log

/**
 * InterstitialAdManager — مُعطَّل بالكامل بقرار المشروع (UI_ARCHITECTURE_SPEC.md §3.5).
 * سياسة التطبيق: لا إعلانات إطلاقاً. جميع الدوال no-op للحفاظ على توافق الاستدعاءات.
 */
object InterstitialAdManager {
    private const val TAG = "InterstitialAdManager"

    /** No-op: الإعلانات معطّلة بقرار المشروع */
    fun initialize(context: Context) {
        Log.d(TAG, "Ads disabled by project policy — initialize() is a no-op")
    }

    /** No-op: الإعلانات معطّلة بقرار المشروع */
    fun preload(context: Context) {
        // no-op
    }

    /**
     * No-op: لا إعلانات interstitial بغض النظر عن حالة الاشتراك.
     * الدالة محفوظة للتوافق مع الاستدعاءات الموجودة في MainScreen.kt.
     */
    fun recordNavigation(context: Context, isSubscribed: Boolean = false) {
        // no-op: zero ads policy
    }
}

