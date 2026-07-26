package com.example.data

import android.content.Context
import android.util.Log

private const val TAG_BANK = "SaBankParser"

/** نوع العملية المالية — التحديد الدقيق بدل التخمين */
enum class TxType(val isExpense: Boolean, val arabicLabel: String) {
    PURCHASE(true, "شراء"),
    WITHDRAWAL(true, "سحب نقدي"),
    TRANSFER_OUT(true, "حوالة صادرة"),
    BILL_PAYMENT(true, "سداد فاتورة"),
    INSTALLMENT(true, "قسط"),
    SUBSCRIPTION(true, "اشتراك"),
    FEE(true, "رسوم"),
    TRANSFER_IN(false, "حوالة واردة"),
    DEPOSIT(false, "إيداع"),
    SALARY(false, "راتب"),
    REFUND(false, "استرداد")
}

data class ParsedBankTx(
    val amount: Double,
    val isExpense: Boolean,
    val title: String,
    val category: String,
    val bankName: String,
    val merchantName: String?,
    val rawText: String,
    val txType: TxType = if (isExpense) TxType.PURCHASE else TxType.DEPOSIT,
    // 1.0 للمسار الكوتلاني القديم (قرار ثنائي: اتفهمت أو null) — الأقل من كده جاي من
    // bank_rules.json بس (BankRulesEngine)، فيه تدرّج ثقة حقيقي حسب دقة القاعدة
    val confidence: Float = 1.0f,
    // مرجع العملية البنكي لو القاعدة لقته (bank_rules.json بس دلوقتي) — بصمة أقوى بكتير
    // من مبلغ+تاجر لـ TxDeduplicator، لأنه رقم فريد فعلي للعملية مش تخمين
    val externalRef: String? = null
)

/**
 * محرك تحليل رسائل البنوك السعودية — النسخة الاحترافية
 *
 * المبادئ:
 * 1. الرفض أفضل من التخمين — رسالة غير مفهومة تروح للـ AI fallback بدل ما تتسجل غلط
 * 2. فلترة الضجيج أولاً: OTP / عمليات مرفوضة / عروض ترويجية / استعلام رصيد → تتجاهل نهائياً
 * 3. المبلغ المُسمّى (بمبلغ X) له الأولوية، والرصيد (الرصيد: X) يُستبعد تماماً
 * 4. اتجاه العملية يتحدد بكلمات صريحة — مفيش "افتراضي مصروف"
 */
object SaBankParser {

    // ─── 1) فلاتر الضجيج — رسائل تُتجاهل نهائياً ─────────────────

    private val otpKeywords = listOf(
        "رمز التحقق", "رمز تحقق", "كود التحقق", "الرمز السري", "رمز الدخول",
        "رمز التفعيل", "كلمة المرور", "لا تشارك", "لا تشاركه", "otp", "verification code",
        "one-time", "one time password", "do not share", "password", "الرقم السري المؤقت",
        "doğrulama kodu", "tek kullanımlık şifre", "kimseyle paylaşmayın"
    )

    private val declinedKeywords = listOf(
        "فشل", "فشلت", "رفض", "مرفوضة", "مرفوض", "لم تتم", "لم تنجح",
        "غير ناجحة", "تعذر", "رصيد غير كاف", "insufficient", "declined",
        "failed", "unsuccessful", "rejected", "تم الإلغاء", "ملغاة",
        "başarısız", "reddedildi", "yetersiz bakiye", "işlem gerçekleşmedi"
    )

    private val expiredKeywords = listOf(
        "انتهت صلاحية", "انتهت صلاحيتها", "منتهية الصلاحية", "بطاقة منتهية",
        "expired", "has expired", "card expired",
        "süresi doldu", "kartın süresi dolmuş"
    )

    private val promoKeywords = listOf(
        "عرض خاص", "عروض", "خصم يصل", "استمتع", "اشترك الآن", "حمل التطبيق",
        "سارع", "لفترة محدودة", "كاش باك يصل", "% off", "promo", "offer ends",
        "özel teklif", "kampanya", "şimdi abone ol", "uygulamayı indir"
    )

    /**
     * سبب تسجيل الرسالة في zad_rejected_bank_messages. OTP/DECLINED/EXPIRED/PROMO بترجع من
     * [rejectionReason] (ضجيج اتفلتر قبل أي تحليل). UNPARSED مختلفة: الرسالة عدّت فحص
     * "شكلها بنكية" لكن كل المسارات (JSON/كوتلاني/AI) فشلت تفهمها — الـ Receivers هي اللي
     * بتسجلها كده صراحة (مش من rejectionReason)، عشان تبقى مادة خام لقاعدة جديدة في
     * bank_rules.json لاحقاً.
     */
    enum class RejectReason { OTP, DECLINED, EXPIRED, PROMO, UNPARSED }

    fun rejectionReason(text: String): RejectReason? {
        val t = text.lowercase()
        return when {
            otpKeywords.any { t.contains(it) } -> RejectReason.OTP
            declinedKeywords.any { t.contains(it) } -> RejectReason.DECLINED
            expiredKeywords.any { t.contains(it) } -> RejectReason.EXPIRED
            promoKeywords.any { t.contains(it) } -> RejectReason.PROMO
            else -> null
        }
    }

    /** هل الرسالة ضجيج (OTP / مرفوضة / منتهية / إعلان)؟ — تُستخدم أيضاً من الـ Receivers */
    fun isNoise(text: String): Boolean = rejectionReason(text) != null

    // ─── 2) استخراج المبلغ بدقة ──────────────────────────────────

    private val arabicIndicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9', '٫' to '.'
    )

    // internal مش private — BankRulesEngine (نفس الموديول) محتاجها عشان يطبّع رقم الـ JSON rule
    // نفس الطريقة قبل ما يطبّق الـ regex بتاعه، بدل ما يكرر نفس منطق تطبيع الأرقام
    internal fun normalizeDigits(text: String): String =
        text.map { arabicIndicDigits[it] ?: it }.joinToString("")

    // البديل الأول (فاصلة آلاف/نقطة عشري) بيغطي السعودية/مصر زي ما هو — البديل التاني
    // (نقطة آلاف/فاصلة عشري) مضاف لتركيا (١.٢٣٤,٥٦) من غير ما يأثر على ترتيب المطابقة القديم
    private const val NUM = """(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""
    // TL/₺/TRY مضافة لدعم تركيا، EGP/ج.م/جنيه لمصر — لسه محتاجين اختبار على SMS حقيقية
    private const val CUR = """(?:ر\.س|رس|ريال|SAR|SR|TL|₺|TRY|EGP|ج\.?م|جنيه)"""

    // مبلغ مُسمّى صراحة — أعلى أولوية
    private val labeledAmount = Regex("""(?:بمبلغ|مبلغ|بقيمة|قيمة|القيمة|amount)\s*:?\s*$CUR?\s*$NUM\s*$CUR?""", RegexOption.IGNORE_CASE)
    // مبلغ ملاصق للعملة
    private val amountThenCur = Regex("""$NUM\s*$CUR""", RegexOption.IGNORE_CASE)
    private val curThenAmount = Regex("""$CUR\s*$NUM""", RegexOption.IGNORE_CASE)
    // أي رقم مرتبط بالرصيد — يُستبعد
    private val balanceContext = Regex("""(?:الرصيد|رصيدك|رصيد|المتاح|المتبقي|balance|available)\s*(?:المتاح|الحالي|:)?\s*$CUR?\s*$NUM""", RegexOption.IGNORE_CASE)

    private fun firstNumberIn(match: MatchResult): Double? =
        match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.let { normalizeNumber(it) }

    /**
     * بيحل غموض الفاصلة/النقطة بين التنسيق الغربي (1,234.56 — فاصلة آلاف) والتركي/الأوروبي
     * (1.234,56 — فاصلة عشرية). لو الاتنين موجودين، آخر واحد على اليمين هو العلامة العشرية.
     * لو فاصلة واحدة بس وبعدها رقمين بالظبط، الأرجح إنها عشرية (تركي) لا آلاف.
     */
    internal fun normalizeNumber(raw: String): Double? {
        val hasComma = raw.contains(',')
        val hasDot = raw.contains('.')
        val normalized = when {
            hasComma && hasDot -> {
                if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) raw.replace(".", "").replace(",", ".")
                else raw.replace(",", "")
            }
            hasComma -> {
                if (raw.substringAfterLast(',').length == 2) raw.replace(",", ".") else raw.replace(",", "")
            }
            else -> raw
        }
        return normalized.toDoubleOrNull()?.asMoney()
    }

    /**
     * استخراج مبلغ العملية (وليس الرصيد):
     * 1. لو فيه "بمبلغ X" → ناخده فوراً
     * 2. غير كده: ناخد أول مبلغ بعملة *مش* جاي في سياق "الرصيد"
     */
    fun extractAmount(rawText: String): Double? {
        val text = normalizeDigits(rawText)

        labeledAmount.find(text)?.let { m ->
            firstNumberIn(m)?.let { if (it > 0) return it }
        }

        // نحدد مواقع أرقام الرصيد عشان نستبعدها
        val balanceRanges = balanceContext.findAll(text).map { it.range }.toList()

        val candidates = (amountThenCur.findAll(text) + curThenAmount.findAll(text))
            .filter { m -> balanceRanges.none { it.first <= m.range.first && m.range.first <= it.last } }
            .mapNotNull { firstNumberIn(it) }
            .filter { it > 0 }
            .toList()

        return candidates.minOrNull() // لو فيه أكتر من رقم غير مستبعد، الأصغر غالباً هو مبلغ العملية والأكبر رصيد
    }

    // ─── 3) تحديد نوع العملية بكلمات صريحة ───────────────────────

    private data class TypeRule(val type: TxType, val keywords: List<String>)

    // الترتيب مهم: الأكثر تحديداً أولاً
    private val typeRules = listOf(
        TypeRule(TxType.REFUND, listOf("استرداد", "مسترد", "عكس عملية", "إرجاع مبلغ", "refund", "reversed", "reversal", "iade", "geri ödeme")),
        TypeRule(TxType.SALARY, listOf("راتب", "مرتب", "salary", "payroll", "maaş")),
        TypeRule(TxType.TRANSFER_IN, listOf("حوالة واردة", "تحويل وارد", "وصلتك حوالة", "استلمت حوالة", "received transfer", "incoming transfer", "gelen havale", "havale aldınız")),
        TypeRule(TxType.DEPOSIT, listOf("إيداع", "ايداع", "أودع", "مودع", "قيد دائن", "دائن", "credited", "deposit", "وارد", "hesabınıza yatırıldı")),
        TypeRule(TxType.TRANSFER_OUT, listOf("حوالة صادرة", "تحويل صادر", "تحويل الى", "تحويل إلى", "حوالة الى", "حوالة إلى", "transfer to", "sent to", "تحويل مبلغ", "gönderilen havale", "havale gönderildi")),
        TypeRule(TxType.WITHDRAWAL, listOf("سحب نقدي", "سحب من الصراف", "صراف آلي", "atm", "سحب مبلغ", "withdrawal", "cash withdrawal", "nakit çekme", "para çekme")),
        TypeRule(TxType.BILL_PAYMENT, listOf("سداد", "فاتورة", "sadad", "bill payment", "دفع فاتورة", "fatura ödemesi", "fatura")),
        TypeRule(TxType.INSTALLMENT, listOf("قسط", "أقساط", "دفعة من", "installment", "تابي", "تمارة", "tabby", "tamara", "taksit")),
        TypeRule(TxType.FEE, listOf("رسوم", "عمولة", "fee", "charges", "vat", "ücret", "komisyon")),
        TypeRule(TxType.PURCHASE, listOf("شراء", "مشتريات", "عملية شراء", "نقاط البيع", "خصم", "دفع", "تم الدفع", "مدين", "قيد مدين", "purchase", "pos", "debited", "payment", "paid", "spent", "مدفوعات", "أبل باي", "apple pay", "mada", "مدى", "satın alma", "harcama", "ödeme", "kartınızdan"))
    )

    /** يحدد نوع العملية — يرجع null لو مفيش كلمة صريحة (يروح AI fallback) */
    fun detectTxType(text: String): TxType? {
        val t = text.lowercase()
        for (rule in typeRules) {
            if (rule.keywords.any { t.contains(it) }) return rule.type
        }
        return null
    }

    // ─── 4) تصنيف الفئة ─────────────────────────────────────────

    private val categoryRules = listOf(
        "الراتب" to listOf("راتب", "مرتب", "salary"),
        "البقالة" to listOf("بقالة", "تموين", "سوبر", "خضار", "لحوم", "هايبر", "بنده", "العثيم", "الدانوب", "لولو", "كارفور", "تميمي", "grocery", "supermarket", "panda", "danube", "carrefour", "lulu"),
        "المطاعم" to listOf("مطعم", "كافيه", "وجبات", "hungerstation", "mrsool", "jahez", "توصيل طعام", "طلبات", "مأكولات", "ستاربكس", "ماكدونالدز", "البيك", "كودو", "هرفي", "دومينوز", "starbucks", "mcdonald", "albaik", "kudu", "herfy", "restaurant", "cafe"),
        "الفواتير" to listOf("كهرب", "فواتير", "المياه", "اتصالات", "mobily", "zain", "موبايلي", "زين", "الكهرباء", "المياه الوطنية", "electricity", "water bill"),
        "الرعاية الصحية" to listOf("علاج", "صيدلية", "مستشفى", "عيادة", "دواء", "النهدي", "الدواء", "nahdi", "pharmacy", "hospital", "clinic"),
        "المواصلات" to listOf("مواصلات", "أوبر", "كريم", "uber", "careem", "taxi", "نقل", "طيران", "باص", "قطار", "flight", "المطار"),
        "التعليم" to listOf("تعليم", "مدرسة", "جامعة", "دورة", "تدريب", "منصة تعليم", "school", "university", "course", "udemy"),
        "الأقساط" to listOf("تابي", "تمارة", "قسط", "أقساط", "tabby", "tamara", "installment", "دفعة من"),
        "الاشتراكات" to listOf("نتفلكس", "netflix", "شاهد", "shahid", "spotify", "youtube premium", "apple music", "اشتراك شهري", "اشتراك سنوي", "subscription", "anghami", "أنغامي", "osn", "prime"),
        "الوقود" to listOf("محطة", "بنزين", "وقود", "ديزل", "ساسكو", "الدريس", "نفط", "petrol", "fuel", "sasco", "aldrees", "naft"),
        "تحويلات" to listOf("حوالة", "تحويل", "transfer", "stc pay")
    )

    fun classify(text: String, txType: TxType?): String {
        val t = text.lowercase()
        // نوع العملية بيرشدنا للفئة مباشرة في حالات معينة
        when (txType) {
            TxType.SALARY -> return "الراتب"
            TxType.INSTALLMENT -> return "الأقساط"
            TxType.BILL_PAYMENT -> return "الفواتير"
            TxType.SUBSCRIPTION -> return "الاشتراكات"
            TxType.TRANSFER_IN, TxType.TRANSFER_OUT -> {
                // نكمل التصنيف — التحويل ممكن يكون له غرض معروف
            }
            else -> {}
        }
        for ((category, keywords) in categoryRules) {
            if (keywords.any { t.contains(it) }) return category
        }
        return if (txType == TxType.TRANSFER_IN || txType == TxType.TRANSFER_OUT) "تحويلات" else "أخرى"
    }

    // ─── 5) استخراج اسم التاجر ───────────────────────────────────

    private val merchantPatterns = listOf(
        Regex("""(?:لدى|شراء من|من متجر|عند|في متجر|at|purchase at|payment to|to)\s+([^\n,،.]{2,35})""", RegexOption.IGNORE_CASE),
        Regex("""(?:مع|لـ|إلى)\s+([^\n,،.\d]{3,30})""")
    )

    fun extractMerchant(text: String): String? {
        for (p in merchantPatterns) {
            p.find(text)?.let { m ->
                val merchant = m.groupValues[1].trim()
                    .replace(Regex("""(?:بمبلغ|مبلغ|رصيد|بطاقة).*"""), "").trim()
                if (merchant.length >= 2) return merchant.take(35)
            }
        }
        return null
    }

    // ─── 6) تعريف البنوك ─────────────────────────────────────────

    private data class BankDef(val name: String, val idKeywords: List<String>)

    private val banks = listOf(
        BankDef("الراجحي", listOf("alrajhi", "rajhi", "الراجحي")),
        BankDef("الأهلي السعودي", listOf("snb", "alahli", "ncba", "الأهلي")),
        BankDef("بنك الرياض", listOf("riyad", "riyadh", "الرياض")),
        BankDef("ساب", listOf("sabb", "ساب")),
        BankDef("مصرف الإنماء", listOf("alinma", "enmaa", "الإنماء", "الانماء")),
        BankDef("البلاد", listOf("albilad", "البلاد")),
        BankDef("الجزيرة", listOf("aljazira", "الجزيرة")),
        BankDef("العربي الوطني", listOf("anb", "العربي")),
        BankDef("stc pay", listOf("stcpay", "stc pay")),
        BankDef("تابي", listOf("tabby", "تابي")),
        BankDef("تمارة", listOf("tamara", "تمارة")),
        BankDef("urpay", listOf("urpay")),
        BankDef("D360", listOf("d360")),
        // بنوك ومحافظ مصر — أسماء عامة معروفة، idKeywords دي أفضل معرفة مش تجربة فعلية على SMS
        // حقيقية من الجهات دي. لو عندك رسالة حقيقية، ابعتها عشان نظبط الكلمات الصح. حذرين من
        // تصادم كلمات مع بنوك السعودية/تركيا الموجودة (مثلاً "qnb" بمفرده محجوز لـ QNB Finansbank
        // التركي تحت، فـ QNB الأهلي المصري بياخد "qnbalahli" بدل الكلمة المجردة).
        BankDef("CIB", listOf("cib", "سي آي بي", "البنك التجاري الدولي")),
        BankDef("QNB الأهلي", listOf("qnbalahli", "qnb alahli", "qnb-alahli", "كيو ان بي الأهلي")),
        BankDef("البنك الأهلي المصري", listOf("nbe", "البنك الأهلي المصري")),
        BankDef("بنك مصر", listOf("banquemisr", "banque misr", "بنك مصر")),
        BankDef("بنك الإسكندرية", listOf("alexbank", "بنك الإسكندرية", "بنك الاسكندرية")),
        BankDef("HSBC مصر", listOf("hsbcegypt", "hsbc egypt", "hsbc")),
        BankDef("فودافون كاش", listOf("vodafonecash", "vodafone cash", "فودافون كاش")),
        BankDef("InstaPay", listOf("instapay")),
        BankDef("فورى", listOf("fawry", "فورى", "فوري")),
        // بنوك تركيا — أسماء عامة معروفة، بس idKeywords دي تخمين بأفضل معرفة مش تجربة فعلية
        // على SMS حقيقية من البنوك دي. لو عندك رسالة حقيقية من بنك تركي، ابعتها عشان نظبط
        // الكلمات الصح (sender ID الفعلي ممكن يكون مختلف تماماً عن اسم البنك).
        BankDef("İş Bankası", listOf("isbank", "iş bankası", "işbank")),
        BankDef("Garanti BBVA", listOf("garanti", "garantibbva")),
        BankDef("Akbank", listOf("akbank")),
        BankDef("Yapı Kredi", listOf("yapikredi", "yapı kredi", "ykb")),
        BankDef("Ziraat Bankası", listOf("ziraat", "ziraatbank")),
        BankDef("Halkbank", listOf("halkbank", "halk bankası")),
        BankDef("VakıfBank", listOf("vakifbank", "vakıfbank")),
        BankDef("QNB Finansbank", listOf("qnb", "finansbank")),
        BankDef("DenizBank", listOf("denizbank", "deniz bank")),
        BankDef("TEB", listOf("teb")),
        BankDef("Papara", listOf("papara"))
    )

    private fun identifyBank(source: String): BankDef? {
        val s = source.lowercase()
        return banks.firstOrNull { b -> b.idKeywords.any { s.contains(it) } }
    }

    // ─── 7) نقطة الدخول الرئيسية ─────────────────────────────────

    /**
     * التحليل الكامل: مصدر (package أو SMS sender) + عنوان + نص
     * يرجع null لو: ضجيج / مفيش مبلغ / مفيش نوع عملية واضح → AI fallback يتصرف
     *
     * [context] اختياري — لو موجود، بيجرب bank_rules.json (BankRulesEngine) الأول قبل المسار
     * الكوتلاني تحت. من غيره (زي كل اختبارات الوحدة الحالية) بيتخطى الـ JSON مباشرة للمسار
     * القديم — نفس السلوك السابق بالظبط، من غير ما نكسر أي اختبار موجود.
     */
    fun detectAndParse(source: String, title: String, text: String, context: Context? = null): ParsedBankTx? {
        val fullText = "$title $text"

        // 1) فلترة الضجيج — أهم خطوة، قبل أي مسار (JSON أو كوتلاني)
        if (isNoise(fullText)) {
            Log.d(TAG_BANK, "Ignored noise message (OTP/declined/promo)")
            return null
        }

        // 1.5) قواعد JSON (بنوك/محافظ جديدة زي مصر) — لو مفيش match بيرجع null ويكمل تحت عادي
        if (context != null) {
            BankRulesEngine.tryParse(context, source, fullText)?.let { return it }
        }

        // 2) المبلغ — بدون مبلغ مفيش عملية
        val amount = extractAmount(fullText) ?: return null

        // 3) نوع العملية — لازم كلمة صريحة، مفيش تخمين
        val txType = detectTxType(fullText) ?: run {
            Log.d(TAG_BANK, "No explicit tx type — deferring to AI fallback")
            return null
        }

        // 4) البنك (لو معروف)
        val bank = identifyBank(source) ?: identifyBank(fullText)
        val bankName = bank?.name ?: "البنك"

        // تابي وتمارة دايماً أقساط
        val finalType = when (bankName) {
            "تابي", "تمارة" -> TxType.INSTALLMENT
            else -> txType
        }

        val category = classify(fullText, finalType)
        val merchant = extractMerchant(fullText)

        val txTitle = buildString {
            append(bankName)
            append(": ")
            append(merchant ?: finalType.arabicLabel)
        }

        return ParsedBankTx(
            amount = amount,
            isExpense = finalType.isExpense,
            title = txTitle,
            category = category,
            bankName = bankName,
            merchantName = merchant,
            rawText = text.take(160),
            txType = finalType
        )
    }

    /**
     * يسجّل رسالة مرفوضة (OTP/عملية مرفوضة/بطاقة منتهية/عرض) في جدول محلي مقفول على ٢٠٠ صف —
     * الدليل على إن الفلتر مش بيبلع عمليات حقيقية غلط. Room-only، بلا مزامنة Supabase.
     */
    suspend fun logRejection(context: android.content.Context, reason: RejectReason, source: String, rawText: String) {
        try {
            val dao = com.example.data.local.ZadDatabase.getDatabase(context).zadDao()
            dao.insertRejectedBankMessage(
                RejectedBankMessage(
                    reason = reason.name,
                    source = source,
                    rawText = rawText.take(300),
                    createdAt = java.time.Instant.now().toString()
                )
            )
            dao.trimRejectedBankMessages()
        } catch (e: Exception) {
            Log.e(TAG_BANK, "logRejection() failed: ${e.message}")
        }
    }
}

/**
 * مانع الخصم المزدوج — نفس العملية بتوصل SMS + إشعار تطبيق البنك
 * البصمة: المبلغ + الاتجاه + (اسم التاجر/العنوان لو متوفر) + نافذة زمنية 10 دقائق
 */
object TxDeduplicator {

    private const val PREFS = "zad_tx_dedup"
    private const val KEY = "recent_fingerprints"
    private const val WINDOW_MS = 10 * 60 * 1000L // 10 دقائق

    // ملف SharedPreferences منفصل لكل مستخدم — قبل كده كان مشترك لأي حساب مسجل دخول على
    // نفس الجهاز، فبصمات مستخدم كانت ممكن تمنع (أو تتخلط مع) معاملة حقيقية لمستخدم تاني على
    // نفس الجهاز. البصمات مؤقتة (نافذة 10 دقايق) فمفيش داعي لـ fallback على بيانات قديمة.
    // CurrentUser (كاش SharedPreferences محلي) مش SupabaseRepo.client مباشرة — عشان أداة
    // تخزين محلي بحتة ما تبقاش معتمدة على تهيئة عميل الشبكة (شافها فشل تحت اختبارات Robolectric).
    private fun userScopedPrefsName(context: Context): String =
        PREFS + (CurrentUser.get(context)?.let { "_$it" } ?: "")

    /**
     * يرجع true لو العملية جديدة (ويسجلها)، false لو مكررة.
     * [disambiguator] (اسم التاجر أو عنوان العملية) بيميّز عمليتين مختلفتين بنفس المبلغ
     * والاتجاه في نفس النافذة الزمنية (زي شرائين بنفس القيمة من محلين مختلفين) —
     * قبل كده كان بيتحسبوا مكررين غلط لأن البصمة كانت مبلغ+اتجاه بس.
     * [externalRef] مرجع البنك الفعلي لو القاعدة لقته (bank_rules.json) — بصمة أقوى بكتير
     * من مبلغ+تاجر لأنه رقم فريد حقيقي للعملية، فبيتفحص الأول وبيتغلّب على أي حاجة تانية.
     * [confidence] بيتخزن مع البصمة بس (مش بيأثر على قرار التكرار هنا) — استهلاكه الفعلي
     * شغل ZadIngest (Task 12) لما يوصل.
     */
    @Synchronized
    fun isNewTransaction(
        context: Context,
        amount: Double,
        isExpense: Boolean,
        disambiguator: String? = null,
        externalRef: String? = null,
        confidence: Float = 1.0f
    ): Boolean {
        val prefs = context.getSharedPreferences(userScopedPrefsName(context), Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // البصمات المحفوظة: "amount|isExpense|timestamp|disambiguatorHash|externalRef|confidence"
        val stored = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        val valid = stored.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 6) {
                val ts = parts[2].toLongOrNull() ?: return@mapNotNull null
                if (now - ts < WINDOW_MS) parts else null
            } else null
        }

        val amountKey = String.format(java.util.Locale.US, "%.2f", amount)
        val expenseKey = isExpense.toString()
        val disambigKey = (disambiguator?.trim()?.lowercase() ?: "").hashCode().toString()
        val refKey = externalRef?.trim()?.uppercase() ?: ""

        // مرجع البنك أقوى إشارة — لو موجود وطابق مرجع مخزّن، دي نفس العملية أكيد بغض النظر
        // عن المبلغ/التاجر (ممكن يكونوا مختلفين شكلياً بس المرجع بيقول نفس العملية)
        val isDuplicateByRef = refKey.isNotBlank() && valid.any { it[4] == refKey }
        val isDuplicateByAmount = valid.any { it[0] == amountKey && it[1] == expenseKey && it[3] == disambigKey }
        val isDuplicate = isDuplicateByRef || isDuplicateByAmount

        if (isDuplicate) {
            Log.d(TAG_BANK, "Duplicate transaction blocked: $amountKey (expense=$expenseKey, byRef=$isDuplicateByRef)")
            return false
        }

        val updated = valid.map { it.joinToString("|") }.toMutableSet()
        updated.add("$amountKey|$expenseKey|$now|$disambigKey|$refKey|$confidence")
        prefs.edit().putStringSet(KEY, updated).apply()
        return true
    }
}
