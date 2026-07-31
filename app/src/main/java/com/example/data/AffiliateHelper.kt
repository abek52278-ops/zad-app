package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.BuildConfig

/**
 * مساعد أفلييت أمازون السعودية — يضمن وصول العميل لصفحة المنتج
 * مع تسجيل العمولة (tag) بشكل صحيح.
 *
 * المشاكل اللي بيحلها:
 * 1. ASIN فاضي/غير مضمون → بدل لينك 404، يفتح رابط بحث بالاسم (amazon.sa/s?k=...)
 *    بنفس التاج، مضمون 100% إنه يفتح صفحة حقيقية بدل صفحة مش موجودة
 * 2. تطبيق أمازون بيخطف اللينك ويفتح الرئيسية → نفتح في المتصفح
 *    تحديداً عشان كوكي العمولة تتسجل على صفحة المنتج
 * 3. مفيش متصفح؟ fallback عادي بدل ما التطبيق يقع
 */
object AffiliateHelper {

    private const val TAG = "AffiliateHelper"
    /** من AMAZON_ASSOCIATE_TAG في .env (Secrets Gradle Plugin → BuildConfig) */
    val AFFILIATE_TAG: String get() = BuildConfig.AMAZON_ASSOCIATE_TAG
    private const val BASE = "https://www.amazon.sa"

    /**
     * لينك المنتج المباشر — أو بحث بالاسم.
     *
     * [asinVerified] لازم يكون true عشان نبني لينك /dp/. الفورمات لوحده مش كافي:
     * الخمس منتجات اللي كانت في الكتالوج (B07G9LQJ5M، B07F2Y9Z9T، …) كلها ١٠ حروف
     * وبتبدأ بـ B0 — يعني عدّت الفحص القديم — لكنها متولّدة مش حقيقية، فكل لينك منتج
     * في التطبيق كان بيفتح صفحة 404 على أمازون. مفيش فحص offline يقدر يفرّق بين ASIN
     * حقيقي وواحد متولّد، فالحل إن التحقق البشري (`affiliate_products.asin_verified`)
     * هو اللي يفتح مسار /dp/، وأي حاجة تانية تروح على البحث اللي مستحيل يرجّع 404.
     */
    fun productUrl(asin: String?, fallbackSearchTerm: String? = null, asinVerified: Boolean = false): String {
        val cleanAsin = asin?.trim().orEmpty()
        val wellFormed = cleanAsin.length == 10 && cleanAsin.all { it.isLetterOrDigit() }
        return if (asinVerified && wellFormed) {
            "$BASE/dp/$cleanAsin/?tag=$AFFILIATE_TAG"
        } else {
            val term = Uri.encode(fallbackSearchTerm ?: "")
            if (cleanAsin.isNotEmpty() && !asinVerified) {
                Log.w(TAG, "ASIN '$cleanAsin' is not verified — using search instead of /dp/ to avoid a 404: $term")
            }
            "$BASE/s?k=$term&tag=$AFFILIATE_TAG"
        }
    }

    /**
     * فتح اللينك في المتصفح تحديداً (مش تطبيق أمازون) —
     * ده اللي يضمن فتح صفحة المنتج نفسها وتسجيل العمولة
     */
    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // نحدد المتصفح الافتراضي صراحة عشان أمازون آب ما يخطفش اللينك
            val browserPkg = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
            if (!browserPkg.isNullOrBlank() && browserPkg != "android" && !browserPkg.contains("amazon")) {
                intent.setPackage(browserPkg)
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened affiliate link in browser ($browserPkg): $url")
        } catch (e: Exception) {
            // fallback: أي تطبيق يقدر يفتح اللينك — أحسن من لا شيء
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open affiliate link: ${e2.message}")
            }
        }
    }

    /** الاختصار الكامل: يبني اللينك الصح ويفتحه صح */
    fun openProduct(context: Context, product: AffiliateProduct) {
        open(context, productUrl(product.asin, product.productNameAr, product.asinVerified))
    }
}
