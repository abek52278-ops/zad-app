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
import java.time.LocalDate

object RewardedBrainAdManager {
    private const val TAG = "RewardedBrainAdManager"
    private const val AD_UNIT_ID = "ca-app-pub-4433736715872551/5974535887"
    private const val PREF_NAME = "rewarded_brain_unlock"
    private const val KEY_UNLOCK_DATE = "unlock_date"
    private const val KEY_LAST_REWARD_TS = "last_reward_ts"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun initialize(context: Context) {
        MobileAds.initialize(context) { }
        preload(context.applicationContext)
    }

    fun hasUnlockedToday(context: Context): Boolean {
        val today = LocalDate.now().toString()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_UNLOCK_DATE, null) == today
    }

    fun showRewardedBrainUnlock(
        context: Context,
        onRewarded: () -> Unit,
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
            val today = LocalDate.now().toString()
            prefs.edit()
                .putString(KEY_UNLOCK_DATE, today)
                .putLong(KEY_LAST_REWARD_TS, System.currentTimeMillis())
                .apply()
            rewardedAd = null
            preload(context.applicationContext)
            onRewarded()
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
