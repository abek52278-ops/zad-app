package com.example

import android.app.Application
import android.content.Context
import com.example.ads.RewardedBrainAdManager

/**
 * موجودة عشان حاجة واحدة: تلف الـ base context بتاع التطبيق كله باللغة اللي العميل
 * اختارها.
 *
 * `MainActivity.attachBaseContext` كانت بتعمل ده للشاشات — وبس. أي كود بيقرا نصوص
 * وهو برّه الـ Activity (الـ Workers، الـ BroadcastReceivers، الـ Services) بيستخدم
 * `applicationContext`، وده كان بيتحلّ من **لغة النظام** مش من اختيار العميل. النتيجة
 * اللي العميل شافها: التطبيق بالعربي، والإشعارات بتوصل بالإنجليزي — تليفون لغته
 * إنجليزي، وتسعة أماكن بتبني إشعارات (تذكير الجرعات، ملخص الصباح، تنبيهات الميزانية،
 * التسبيح، الجيوفنس، الشات، المواسم) كلهم بيقروا من `values-en`.
 *
 * الحل هنا مركزي عن قصد: تصليح كل موقع لوحده معناه تسعة أماكن لازم تفتكر تعمل نفس
 * الحاجة، والعاشر اللي هيتكتب بكرة هينساها. لف الـ Application بيخلي
 * `applicationContext` نفسه محلّي، فكل موقع بياخد اللغة الصح من غير ما يعرف حاجة.
 *
 * [MarketPrefs.wrapWithStoredLocale] بيقدّم اختيار العميل الصريح على لغة السوق، ولو
 * مفيش اختيار بيرجع للسوق (مصر ← ar-EG) — مش للغة النظام أبداً. يعني أسوأ حالة هنا
 * لغة السوق، مش إنجليزي.
 */
class ZadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RewardedBrainAdManager.initialize(this)
        // كانت مش متندهة خالص — InterstitialAdManager.preload() عمره ما بيتنده استباقياً،
        // فأول إعلان بيني في الجلسة (بعد ٥ تنقلات) كان بيلاقي interstitialAd=null دايماً
        // ويطلب preload وقتها بس، يعني بيتفوّت — الإعلان الفعلي كان بيبان بعد ١٠ تنقلات
        // مش ٥. preload من هنا بيضمن إعلان جاهز من أول تنقلة.
        com.example.ads.InterstitialAdManager.initialize(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.example.data.MarketPrefs.wrapWithStoredLocale(base))
    }
}
