package com.example.ads

import android.content.Context
import android.util.Log
import com.example.data.SupabaseRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AdGateManager — بوابة "افتح الميزة بإعلان" (خطة الإعلانات الذكية).
 *
 * الفكرة: بدل ما الميزات اللي بتبقى مقفولة تماماً على المجاني (فويس/صور تليجرام،
 * عقل زاد وطلباته)، بتتباع بإعلان مُكافئ واحد — العميل بيشوف إعلان، والميزة تتفتح
 * 24 ساعة. ده أعلى eCPM ممكن: إعلان مكافئ في لحظة نية عالية، ومعدل الإكمال عالي
 * لأن العميل هو اللي طالبها بنفسه (اختيار إيجابي، مش مقاطعة).
 *
 * البوابة السيرفرية: RPC `zad_media_pass_grant` (migration 20260828010000) —
 * بيتحقق من min-gap + daily cap، وبيمنح media_pass_expires_at = +24h.
 * المشتركون المدفوعون بيمرّوا من غير إعلانات أصلاً (already_paid).
 */
object AdGateManager {
    private const val TAG = "AdGateManager"
    private const val PREF_NAME = "zad_media_pass"
    private const val KEY_PASS_EXPIRY_TS = "media_pass_expiry_ts"

    /** هل بوابة الوسائط مفتوحة دلوقتي؟ (محلياً — والحقيقة عند السيرفر) */
    fun isMediaPassActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_PASS_EXPIRY_TS, 0L)
        return System.currentTimeMillis() < expiry
    }

    fun mediaPassRemainingMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_PASS_EXPIRY_TS, 0L)
        return (expiry - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * إتمام مشاهدة إعلان مُكافئ → منح بوابة الوسائط من السيرفر.
     * بيرجع المدة المتبقية بالميلي ثانية لو نجح، أو null لو الرفض.
     */
    suspend fun claimMediaPassWithAd(context: Context): Long? {
        val grant = withContext(Dispatchers.IO) {
            try {
                SupabaseRepo.claimMediaPass()
            } catch (e: Exception) {
                Log.e(TAG, "claimMediaPass FAILED: ${e.message}")
                null
            }
        } ?: return null

        val expiryMs = grant?.let {
            runCatching {
                java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()
            }.getOrNull()
        }
        if (expiryMs != null) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_PASS_EXPIRY_TS, expiryMs)
                .apply()
        }
        return expiryMs
    }

    /** تصفير الحالة المحلية عند تسجيل خروج/تبديل حساب */
    fun resetLocal(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}