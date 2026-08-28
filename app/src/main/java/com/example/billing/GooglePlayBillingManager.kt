package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * باقات الاشتراك الشهرية في زاد
 */
enum class ZadSubscriptionPlan(
    val productId: String,
    val titleAr: String,
    val titleEn: String,
    val priceUsd: String,
    val monthlyAiQuota: Int, // -1 means unlimited
    val hasNoAds: Boolean,
    val familyLimit: Int,
    val badgeAr: String,
    val perks: List<String>
) {
    BASIC(
        productId = "zad_sub_basic_monthly",
        titleAr = "الأساسية (Basic)",
        titleEn = "Basic",
        priceUsd = "$9.99",
        monthlyAiQuota = 50,
        hasNoAds = true,
        familyLimit = 1,
        badgeAr = "الأكثر اقتصاداً",
        perks = listOf(
            "تجربة خالية من الإعلانات 100%",
            "50 استشارة وطلب ذكي شهرياً",
            "مسح وتفكيك الفواتير الأساسي",
            "رصد الإشعارات البنكية اللحظي"
        )
    ),
    PLUS(
        productId = "zad_sub_plus_monthly",
        titleAr = "المتقدمة (Plus)",
        titleEn = "Plus",
        priceUsd = "$19.99",
        monthlyAiQuota = 250,
        hasNoAds = true,
        familyLimit = 2,
        badgeAr = "الأكثر شعبية ⭐",
        perks = listOf(
            "كل مزايا الباقة الأساسية",
            "250 استشارة وطلب ذكي شهرياً",
            "تحليلات عقل زاد الاستراتيجية والتنبؤات",
            "مقارنة الأسعار وتنبيهات العروض اللحظية",
            "شجرة المعرفة العصبية التفاعلية 3D"
        )
    ),
    ULTRA(
        productId = "zad_sub_ultra_monthly",
        titleAr = "الفائقة (Ultra)",
        titleEn = "Ultra",
        priceUsd = "$49.99",
        monthlyAiQuota = -1,
        hasNoAds = true,
        familyLimit = 5,
        badgeAr = "VIP العائلة 👑",
        perks = listOf(
            "طلبات ذكاء اصطناعي غير محدودة بالكامل (Unlimited AI)",
            "مشاركة عائلية متزامنة لـ 5 حسابات",
            "تقرير عقل زاد الاستراتيجي المطبوع بختم زاد",
            "المساعد الصوتي البشري المفتوح بلا سقف",
            "دعم فني مباشر VIP ذو أولوية قصوى"
        )
    )
}

sealed class BillingState {
    object Idle : BillingState()
    object Connecting : BillingState()
    object Ready : BillingState()
    data class Purchasing(val productId: String) : BillingState()
    data class Success(val plan: ZadSubscriptionPlan) : BillingState()
    data class Error(val message: String) : BillingState()
}

/**
 * مدير مشتريات Google Play الرسمي لزاد
 */
class GooglePlayBillingManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    private val tag = "GooglePlayBilling"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _activePlan = MutableStateFlow<ZadSubscriptionPlan?>(null)
    val activePlan: StateFlow<ZadSubscriptionPlan?> = _activePlan.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(pendingPurchasesParams)
        .build()

    companion object {
        @Volatile
        private var instance: GooglePlayBillingManager? = null

        fun getInstance(context: Context): GooglePlayBillingManager {
            return instance ?: synchronized(this) {
                instance ?: GooglePlayBillingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        startConnection()
    }

    fun startConnection() {
        _billingState.value = BillingState.Connecting
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(tag, "Google Play Billing setup successful")
                    _billingState.value = BillingState.Ready
                    queryAvailableProducts()
                    queryActivePurchases()
                } else {
                    Log.w(tag, "Billing setup failed: ${billingResult.debugMessage}")
                    _billingState.value = BillingState.Error("فشل الاتصال بـ Google Play: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(tag, "Billing service disconnected, retrying...")
                _billingState.value = BillingState.Connecting
            }
        })
    }

    private fun queryAvailableProducts() {
        val productList = ZadSubscriptionPlan.values().map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { details ->
                    productDetailsMap[details.productId] = details
                    Log.d(tag, "Loaded product: ${details.productId} - ${details.name}")
                }
            } else {
                Log.w(tag, "Failed to query product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchSubscription(activity: Activity, plan: ZadSubscriptionPlan, onComplete: (Boolean, String?) -> Unit) {
        val details = productDetailsMap[plan.productId]
        if (details == null) {
            queryAvailableProducts()
            onComplete(false, "جاري تحضير الباقة من متجر Google Play، يرجى المحاولة بعد لحظات")
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            onComplete(false, "لا يوجد عرض متاح لهذه الباقة حالياً")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _billingState.value = BillingState.Purchasing(plan.productId)
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingState.value = BillingState.Error("تعذر فتح نافذة الدفع: ${result.debugMessage}")
            onComplete(false, result.debugMessage)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(tag, "User canceled Google Play purchase flow")
                _billingState.value = BillingState.Ready
            }
            else -> {
                Log.e(tag, "Purchase flow failed: ${billingResult.debugMessage}")
                _billingState.value = BillingState.Error(billingResult.debugMessage)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            scope.launch {
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val ackResult = billingClient.acknowledgePurchase(ackParams)
                    if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(tag, "Purchase acknowledged successfully: ${purchase.orderId}")
                    }
                }

                val productId = purchase.products.firstOrNull() ?: ""
                val matchedPlan = ZadSubscriptionPlan.values().find { it.productId == productId }
                _activePlan.value = matchedPlan

                // التحقق السيرفر-سايد الحقيقي: verify-purchase بيتصل بـ Google Play
                // Developer API (anti-spoof) وبكتب tier في zad_entitlements — العقل
                // والعرف بيتقروا من هناك. (المسار القديم zad-billing-webhook كان بيسجل
                // من غير تحقق من جوجل وبيرفع tier مش هيتقري — اتشال من مسار الشراء.)
                val verified = verifyWithServer(purchase)

                withContext(Dispatchers.Main) {
                    if (verified) {
                        matchedPlan?.let { _billingState.value = BillingState.Success(it) }
                    } else {
                        _billingState.value = BillingState.Error(
                            "تم الشراء لكن تعذر توثيق الاشتراك من جوجل — هيتم التوثيق تلقائياً عند إعادة فتح التطبيق"
                        )
                    }
                }
            }
        }
    }

    /**
     * تحقق سيرفر-سايد عبر verify-purchase (Google Play Developer API + service account).
     * السيرفر هو مصدر الحقيقة: الـ tier بيتحدد من productId بتاع الاشتراك الفعلي مش
     * من جسم الطلب، والكتابة في zad_entitlements بتحصل بسيرفر بعد موافقة جوجل.
     */
    private suspend fun verifyWithServer(purchase: Purchase): Boolean {
        val productId = purchase.products.firstOrNull() ?: return false
        return try {
            SupabaseRepo.callEdgeFunction(
                "verify-purchase",
                mapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "orderId" to (purchase.orderId ?: ""),
                    "productId" to productId,
                )
            )["valid"] == true
        } catch (e: Exception) {
            Log.e(tag, "verify-purchase failed for ${purchase.orderId}: ${e.message}")
            false
        }
    }

    fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchasesList.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                val productId = active?.products?.firstOrNull()
                _activePlan.value = ZadSubscriptionPlan.values().find { it.productId == productId }
                // استرجاع التوثيق: أي اشتراك نشط بيتوثق تاني كل فتح — بلاش عميل دفع
                // ومش واخد مزايا لو توثيق أول مرة فشل (نت قطع أثناء الشراء مثلاً).
                if (active != null) {
                    scope.launch {
                        if (!active.isAcknowledged) {
                            val ackParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(active.purchaseToken)
                                .build()
                            billingClient.acknowledgePurchase(ackParams)
                        }
                        verifyWithServer(active)
                    }
                }
            }
        }
    }
}
