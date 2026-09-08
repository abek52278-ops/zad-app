package com.example.data

/**
 * محرك تقدير أسعار الأدوية والجرعات الحية (Pharmacy Pricing & Dose Estimator).
 * يوفر أسعاراً استرشادية واقعية ومحدثة لقاعدة بيانات الأدوية الشائعة لضمان
 * حساب التكلفة الشهرية والالتزام بالجرعات ديناميكياً حتى قبل إدخال السعر يدوياً أو عند حقن الفواتير.
 */
object PharmacyPricingEstimator {

    private val KNOWN_MEDICINE_PRICES = mapOf(
        "سولبادين" to 25.0,
        "solpadeine" to 25.0,
        "ديكلوفيناك" to 18.0,
        "diclofenac" to 18.0,
        "فولتارين" to 28.0,
        "voltaren" to 28.0,
        "كتافلام" to 22.0,
        "cataflam" to 22.0,
        "بنادول" to 15.0,
        "panadol" to 15.0,
        "باراسيتامول" to 12.0,
        "paracetamol" to 12.0,
        "بروفين" to 19.0,
        "brufen" to 19.0,
        "إيبوبروفين" to 16.0,
        "ibuprofen" to 16.0,
        "أوجمنتين" to 48.0,
        "augmentin" to 48.0,
        "كونكور" to 34.0,
        "concor" to 34.0,
        "جلوكوفاج" to 24.0,
        "glucophage" to 24.0,
        "أوميبريزول" to 26.0,
        "omeprazole" to 26.0,
        "لانسوبرال" to 32.0,
        "lansoprazole" to 32.0,
        "ليبيدور" to 42.0,
        "lipitor" to 42.0,
        "كريستور" to 55.0,
        "crestor" to 55.0,
        "أسبرين" to 12.0,
        "aspirin" to 12.0,
        "أسبوسيد" to 10.0,
        "asprocid" to 10.0,
        "فيتامين د" to 38.0,
        "vitamin d" to 38.0,
        "فيتامين سي" to 20.0,
        "vitamin c" to 20.0,
        "زنك" to 25.0,
        "zinc" to 25.0,
        "أوميجا 3" to 52.0,
        "omega 3" to 52.0,
        "أنتينال" to 16.0,
        "antinal" to 16.0,
        "فلاجيل" to 14.0,
        "flagyl" to 14.0,
        "ستربسلز" to 22.0,
        "strepsils" to 22.0,
        "أوتريفين" to 18.0,
        "otrivin" to 18.0,
        "كلاريتين" to 26.0,
        "claritin" to 26.0,
        "زيرتك" to 24.0,
        "zyrtec" to 24.0,
        "نيكسيوم" to 65.0,
        "nexium" to 65.0,
        "جاناتون" to 35.0,
        "ganaton" to 35.0
    )

    /**
     * استخراج سعر تقديري استرشادي للدواء بناء على الاسم وقاعدة الأسعار الشائعة
     */
    fun estimatePrice(medicineName: String): Double {
        val clean = medicineName.trim().lowercase()
        if (clean.isBlank()) return 18.0

        for ((key, price) in KNOWN_MEDICINE_PRICES) {
            if (clean.contains(key) || key.contains(clean)) {
                return price
            }
        }
        return 20.0
    }
}
