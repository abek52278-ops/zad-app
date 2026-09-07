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
import com.example.data.SupabaseRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object RewardedBrainAdManager {
    private const val TAG = "RewardedBrainAdManager"
    // وحدة جوجل التجريبية الرسمية للمكافآت أثناء التطوير. الوحدة الحقيقية
    // (ca-app-pub-4433736715872551/5974535887) اتشالت مؤقتاً 2026-09-07: الاختبار
    // على وحدة إنتاج بيسجّل انطباعات غير صالحة، وده سبب معروف لإغلاق حسابات
    // AdMob. ترجع عند بناء نسخة الإنتاج.
    const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TOTAL_ADS_REQUIRED = 3

    private const val PREF_NAME = "rewarded_brain_unlock"
    private const val KEY_AD_WATCH_COUNT = "ad_watch_count"
    private const val KEY_SESSION_EXPIRY_TS = "session_expiry_ts"
    private const val KEY_LAST_REWARD_TS = "last_reward_ts"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val isShowing = AtomicBoolean(false)
    private var retryCount = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    fun isAdReady(): Boolean = rewardedAd != null

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

    fun showRewardedEnergyAd(
        context: Context,
        onAdWatched: (newCount: Int, isFullyUnlocked: Boolean) -> Unit,
        onFailed: () -> Unit
    ) {
        // حارس ضد النقر المزدوج — زرار الإعلان كان ممكن يفتح عرضين ويتلخبط العد.
        if (!isShowing.compareAndSet(false, true)) {
            Log.w(TAG, "Ad already showing; ignoring extra click")
            return
        }

        var settled = false
        fun settleOnce(success: Boolean, newCount: Int = -1, unlocked: Boolean = false) {
            if (settled) return
            settled = true
            isShowing.set(false)
            if (success) onAdWatched(newCount, unlocked) else onFailed()
        }

        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not loaded yet; reloading")
            retryCount = 0
            isLoading = false
            preload(context.applicationContext)
            settleOnce(false)
            return
        }

        try {
            val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
            if (activity == null) {
                Log.w(TAG, "No hosting Activity found for rewarded ad")
                settleOnce(false)
                return
            }

            val ad = rewardedAd
            if (ad == null) {
                Log.w(TAG, "Rewarded ad not loaded yet; reloading")
                preload(context.applicationContext)
                settleOnce(false)
                return
            }

            // الإعلان اتقفل (بعد مكافأة أو بدونها) — لازم الـ UI يعرف في الحالتين،
            // وإلا زرار "شاهد الإعلان" يفضل معطّل للأبد (isShowingAd=true عالق).
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preload(context.applicationContext)
                    // لو المكافأة معملتش settle (المستخدم قفل بدري بدون مكافأة) → فشل نظيف
                    // عشان الزرار يرجع يشتغل فوراً.
                    settleOnce(false)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    preload(context.applicationContext)
                    settleOnce(false)
                }
            }

            ad.show(activity) {
                // المستخدم كسب المكافأة فعلاً — احسبها عندنا عند السيرفر، ولو السيرفر
                // مش متاح احسبها محلياً عشان العميل اللي شاف الإعلان ميتسرقش حقّه
                // (كان فشل السيرفر بيمسح العد كله ويرجّع onFailed بعد ما الإعلان اتنوى).
                rewardedAd = null
                preload(context.applicationContext)
                scope.launch {
                    val state = try {
                        SupabaseRepo.claimRewardedAd()
                    } catch (error: Throwable) {
                        Log.e(TAG, "Reward grant unavailable: ${error.message}")
                        null
                    }
                    if (state == null) {
                        // المنح من السيرفر بس. كان هنا fallback بيزوّد العد في
                        // SharedPreferences ويفتح الجلسة لما السيرفر يقع — يعني رصيد
                        // بيتمنح **من غير أي تحقق**، وقابل للتلاعب بتعديل الملف على
                        // جهاز مفتوح. النية كانت إن العميل شاف الإعلان فعلاً وميضيعش
                        // حقه، بس التكلفة إن أي حد يقدر يشحن نفسه بلا حد.
                        //
                        // البديل: نفشل بوضوح ونسيب المزامنة الجاية تصلّح. المنحة
                        // الحقيقية محفوظة عند السيرفر (zad_ad_grants)، فلو المنح نجح
                        // والرد ضاع، syncServerState هيرجّعها.
                        Log.w(TAG, "Reward grant failed — no local credit granted")
                        settleOnce(false)
                        return@launch
                    }
                    persistServerState(context.applicationContext, state)
                    settleOnce(true, state.adWatchCount, state.brainSessionActive)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showRewardedEnergyAd threw: ${e.message}")
            settleOnce(false)
        }
    }

    private fun persistServerState(context: Context, state: SupabaseRepo.ZadEntitlementState) {
        val expiry = state.brainSessionExpiresAt?.let { value ->
            runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        } ?: 0L
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_AD_WATCH_COUNT, state.adWatchCount)
            .putLong(KEY_SESSION_EXPIRY_TS, expiry)
            .putLong(KEY_LAST_REWARD_TS, System.currentTimeMillis())
            .apply()
    }

    fun preload(context: Context) {
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
                    // إعادة محاولة تلقائية بحد أقصى للتعامل مع بطء الشبكة في البداية
                    if (retryCount < 3) {
                        val delayMs = (1L shl retryCount) * 2000L
                        retryCount++
                        scope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            preload(context)
                        }
                    }
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    retryCount = 0
                    rewardedAd = ad
                }
            }
        )
    }

    private fun Context.getSharedPreferences(name: String, mode: Int): SharedPreferences =
        getSharedPreferences(name, mode)
}
