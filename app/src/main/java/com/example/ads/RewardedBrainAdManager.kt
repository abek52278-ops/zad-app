package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedBrainAdManager {
    private const val TAG = "RewardedBrainAdManager"
    const val AD_UNIT_ID = "ca-app-pub-4433736715872551/5974535887"
    const val TOTAL_ADS_REQUIRED = 3

    private const val PREF_NAME = "rewarded_brain_unlock"
    private const val KEY_AD_WATCH_COUNT = "ad_watch_count"
    private const val KEY_SESSION_EXPIRY_TS = "session_expiry_ts"
    private const val KEY_LAST_REWARD_TS = "last_reward_ts"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun initialize(context: Context) {
        MobileAds.initialize(context) { }
        preload(context.applicationContext)
    }

    fun getAdWatchCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AD_WATCH_COUNT, 0)
    }

    fun isSessionUnlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_SESSION_EXPIRY_TS, 0L)
        return System.currentTimeMillis() < expiry
    }

    fun showRewardedEnergyAd(
        context: Context,
        onAdWatched: (newCount: Int, isFullyUnlocked: Boolean) -> Unit,
        onFailed: () -> Unit
    ) {
        val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
        if (activity == null) {
            Log.w(TAG, "No hosting Activity found for rewarded ad")
            onFailed()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not loaded yet; reloading")
            preload(context.applicationContext)
            onFailed()
            return
        }

        ad.show(activity) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val current = prefs.getInt(KEY_AD_WATCH_COUNT, 0)
            val next = current + 1

            val isFullyUnlocked = next >= TOTAL_ADS_REQUIRED
            val editor = prefs.edit()
            if (isFullyUnlocked) {
                editor.putInt(KEY_AD_WATCH_COUNT, 0)
                // 12 hour session
                editor.putLong(KEY_SESSION_EXPIRY_TS, System.currentTimeMillis() + 12 * 3600 * 1000L)
            } else {
                editor.putInt(KEY_AD_WATCH_COUNT, next)
            }
            editor.putLong(KEY_LAST_REWARD_TS, System.currentTimeMillis())
            editor.apply()

            rewardedAd = null
            preload(context.applicationContext)
            onAdWatched(if (isFullyUnlocked) 0 else next, isFullyUnlocked)
        }
    }

    private fun preload(context: Context) {
        if (isLoading) return
        isLoading = true
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            preload(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                        }
                    }
                }
            }
        )
    }

    private fun Context.getSharedPreferences(name: String, mode: Int): SharedPreferences =
        getSharedPreferences(name, mode)
}
