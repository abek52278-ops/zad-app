package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAdManager {
    private const val TAG = "InterstitialAdManager"
    // AdMob Interstitial Unit ID
    private const val AD_UNIT_ID = "ca-app-pub-4433736715872551/5974535887"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var navigationCounter = 0
    private const val NAVIGATION_THRESHOLD = 5

    fun initialize(context: Context) {
        MobileAds.initialize(context) {}
        preload(context.applicationContext)
    }

    fun preload(context: Context) {
        if (isLoading || interstitialAd != null) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                    Log.w(TAG, "Interstitial ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Call on screen navigation / tab switch. Every 5 navigations, displays an interstitial ad.
     */
    fun recordNavigation(context: Context, isSubscribed: Boolean = false) {
        if (isSubscribed) return // Paid tiers are 100% ad-free

        navigationCounter++
        if (navigationCounter >= NAVIGATION_THRESHOLD) {
            navigationCounter = 0
            val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
            if (activity != null && interstitialAd != null) {
                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        preload(context.applicationContext)
                    }

                    override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                        interstitialAd = null
                        preload(context.applicationContext)
                    }
                }
                interstitialAd?.show(activity)
            } else {
                preload(context.applicationContext)
            }
        }
    }
}
