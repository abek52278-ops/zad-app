package com.example.billing

enum class ZadPlanTier(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val priceUsd: Double,
    val priceSar: Double,
    val priceEgp: Double,
    val brainConsultations: Int,
    val visionScans: Int,
    val chatMessages: String,
    val badge: String? = null,
    val isPopular: Boolean = false,
    val features: List<String>
) {
    STARTER(
        id = "starter",
        titleAr = "الباقة الأساسية",
        subtitleAr = "المدخل الاقتصادي بدون إعلانات",
        priceUsd = 9.99,
        priceSar = 37.5,
        priceEgp = 490.0,
        brainConsultations = 50,
        visionScans = 50,
        chatMessages = "50 طلب ذكي شهرياً",
        features = listOf(
            "🚫 بدون إعلانات تماماً",
            "50 استشارة وطلب ذكي شهرياً",
            "مسح وتفكيك الفواتير الأساسي",
            "رصد الإشعارات البنكية اللحظي"
        )
    ),
    PLUS(
        id = "plus",
        titleAr = "باقة النمو (Plus)",
        subtitleAr = "الخيار الذكي — تحليلات عقل زاد",
        priceUsd = 19.99,
        priceSar = 75.0,
        priceEgp = 980.0,
        brainConsultations = 250,
        visionScans = 250,
        chatMessages = "250 طلب ذكي شهرياً",
        badge = "🔥 الأكثر طلباً",
        isPopular = true,
        features = listOf(
            "🚫 بدون إعلانات تماماً",
            "250 استشارة وطلب ذكي شهرياً",
            "تحليلات عقل زad الاستراتيجية والتنبؤات",
            "مقارنة الأسعار وتنبيهات العروض اللحظية",
            "شجرة المعرفة العصبية التفاعلية 3D"
        )
    ),
    PRO(
        id = "pro",
        titleAr = "باقة المحترفين (Ultra VIP)",
        subtitleAr = "لأصحاب الأعمال والعائلات الكبيرة",
        priceUsd = 49.99,
        priceSar = 187.5,
        priceEgp = 2450.0,
        brainConsultations = -1,
        visionScans = -1,
        chatMessages = "غير محدود (Unlimited AI)",
        badge = "👑 VIP العائلة",
        features = listOf(
            "طلبات ذكاء اصطناعي غير محدودة بالكامل",
            "مشاركة عائلية متزامنة لـ 5 حسابات",
            "تقرير عقل زاد الاستراتيجي المطبوع بختم زاد",
            "المساعد الصوتي البشري المفتوح بلا سقف",
            "دعم فني مباشر VIP ذو أولوية قصوى"
        )
    )
}
