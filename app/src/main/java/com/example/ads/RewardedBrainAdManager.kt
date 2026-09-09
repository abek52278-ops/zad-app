package com.example.ads

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.SupabaseRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * RewardedBrainAdManager — مُعطَّل بالكامل بقرار المشروع (UI_ARCHITECTURE_SPEC.md §3.5).
 * سياسة التطبيق: لا إعلانات إطلاقاً (Zero-Ads Policy).
 * - لا يتم تحميل أو عرض أي إعلانات AdMob.
 * - [showRewardedEnergyAd] يمنح المكافأة فورياً دون إعلانات لمنع تعطل أي ميزة معتمدة عليها.
 * - [syncServerState] لا تزال تقرأ حالة الاستحقاق من السيرفر.
 */
object RewardedBrainAdManager {
    private const val TAG = "RewardedBrainAdManager"
    const val TOTAL_ADS_REQUIRED = 3

    private const val PREF_NAME = "rewarded_brain_unlock"
    private const val KEY_AD_WATCH_COUNT = "ad_watch_count"
    private const val KEY_SESSION_EXPIRY_TS = "session_expiry_ts"
    private const val KEY_LAST_REWARD_TS = "last_reward_ts"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** No-op: الإعلانات معطّلة بقرار المشروع */
    fun initialize(context: Context) {
        Log.d(TAG, "Ads disabled by project policy — initialize() is a no-op")
    }

    fun getAdWatchCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AD_WATCH_COUNT, TOTAL_ADS_REQUIRED)
    }

    /** الجلسة مفتوحة دائماً بفضل سياسة إزالة الإعلانات */
    fun isSessionUnlocked(context: Context): Boolean = true

    /** جاهز دائماً لمنح المكافأة الفورية دون إعلانات */
    fun isAdReady(): Boolean = true

    suspend fun syncServerState(context: Context): SupabaseRepo.ZadEntitlementState? {
        val state = try {
            SupabaseRepo.getEntitlementState()
        } catch (error: Throwable) {
            Log.w(TAG, "Server entitlement state unavailable: ${error.message}")
            null
        } ?: return null
        persistServerState(context.applicationContext, state)
        return state
    }

    /**
     * مكافأة فورية وهمية (Instant Reward / No-op):
     * تمنح المكافأة دون عرض أي إعلان تجاري للمستخدم، مع محاولة مزامنة السيرفر.
     */
    fun showRewardedEnergyAd(
        context: Context,
        onAdWatched: (newCount: Int, isFullyUnlocked: Boolean) -> Unit,
        onFailed: () -> Unit
    ) {
        Log.d(TAG, "Zero-ads policy: granting instant simulated reward without showing ads")
        scope.launch {
            val state = try {
                SupabaseRepo.claimRewardedAd()
            } catch (error: Throwable) {
                Log.w(TAG, "Server claim error: ${error.message}")
                null
            }
            if (state != null) {
                persistServerState(context.applicationContext, state)
                onAdWatched(state.adWatchCount, state.brainSessionActive)
            } else {
                onAdWatched(TOTAL_ADS_REQUIRED, true)
            }
        }
    }

    private fun persistServerState(context: Context, state: SupabaseRepo.ZadEntitlementState) {
        val expiry = state.brainSessionExpiresAt?.let { value ->
            runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        } ?: (System.currentTimeMillis() + 86400000L)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_AD_WATCH_COUNT, state.adWatchCount)
            .putLong(KEY_SESSION_EXPIRY_TS, expiry)
            .putLong(KEY_LAST_REWARD_TS, System.currentTimeMillis())
            .apply()
    }

    /** No-op: الإعلانات معطّلة بقرار المشروع */
    fun preload(context: Context) {
        // no-op
    }
}

