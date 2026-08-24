package com.example.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.*
import com.example.data.SaBankParser
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.content.Context

class UnifiedBankListener : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    /**
     * Apps whose notifications are financial by definition — banks, wallets,
     * BNPL, delivery/e-commerce checkouts. A hit here skips the keyword test,
     * because these apps say "تم" with an amount and nothing else often enough
     * that keyword matching alone would drop real transactions.
     *
     * Matching is `contains`, so a bare vendor token ("alrajhi") catches the
     * regional package variants without listing each one.
     */
    private val trackedPackages = listOf(
        // ── السعودية: بنوك ──
        "com.alrajhi.bank", "com.snb", "com.riyadbank",
        "com.sabb", "com.alinma.bank",
        "alrajhi", "snb", "riyad", "sabb", "alinma",
        "albilad", "aljazira", "anb", "saib", "gib", "emiratesnbd",
        // ── السعودية: محافظ ومدفوعات ──
        "com.stcpay", "stcpay", "urpay", "barq", "tweeq", "d360",
        "mada", "sarie", "geidea", "moyasar", "hyperpay", "paytabs",
        // ── اشترِ الآن وادفع لاحقاً ──
        "com.tabby", "com.tamara", "tabby", "tamara", "madfu", "spotii",
        // ── محافظ عالمية ──
        "com.google.android.apps.walletnfcrel", "com.paypal", "paypal",
        "com.samsung.android.spay", "wise", "revolut", "payoneer",
        // ── تجارة وتوصيل (إيصالات الدفع بتوصل كإشعار) ──
        "noon", "amazon", "aliexpress", "shein", "jahez", "hungerstation",
        "talabat", "careem", "uber", "ninja", "mrsool", "chefz",
        // ── تركيا ──
        "isbank", "garanti", "akbank", "yapikredi", "ziraat",
        "halkbank", "vakifbank", "qnbfinansbank", "denizbank", "teb", "papara",
        "ininal", "tosla", "enpara",
        // ── مصر ──
        "com.cib.cbe", "com.qnb.alahli", "com.nbe", "com.banquemisr",
        "com.alexbank", "com.hsbc.egypt", "com.instapay",
        "cib", "qnbalahli", "nbe", "banquemisr", "alexbank", "hsbcegypt",
        "instapay", "fawry", "vodafonecash", "etisalatcash", "orangecash",
        "valu", "halan", "telda", "meeza", "aman", "souhoola",
        "com.fawry", "com.vodafone",
        // ── قطر / البحرين (أسماء تطبيقات — أفضل تخمين، غير مختبرة زي مصر/تركيا فوق) ──
        "qnb", "dohabank", "cbq", "qib", "dukhanbank", "alrayan",
        "nbbonline", "bbkonline", "ahliunited", "alsalambank", "ithmaar", "benefitpay",
        // ── باقي أسواق مرحلة ٢ (الإمارات/الكويت/عُمان/الأردن/لبنان/العراق/سوريا/اليمن/
        // فلسطين/ليبيا/السودان/المغرب/تونس/الجزائر) — أسماء بنوك ومحافظ معروفة عامة،
        // نفس تحذير قطر/البحرين فوق: أفضل معرفة مش تجربة فعلية على إشعار حقيقي من
        // الجهات دي. حتى لو الاسم هنا غلط أو مش دقيق، fallback الكلمة المفتاحية+المبلغ
        // في isFinancialNotification لسه بيغطي أي بنك مش في القايمة دي أصلاً — القايمة
        // دي تسريع بس، مش شرط للالتقاط.
        "adcb", "fab", "mashreq", // الإمارات (إضافة لـ emiratesnbd الموجودة فوق)
        "nbk", "kfh", "gulfbank", "boubyan", "knet", // الكويت
        "bankmuscat", "nbo", "bankdhofar", // عُمان
        "arabbank", "cabjo", "jkb", "jib", // الأردن
        "bankaudi", "blombank", "byblosbank", // لبنان
        "zaincash", "asiahawala", "rafidain", "rasheedbank", // العراق
        "syriatelcash", "mtncash", // سوريا
        "cacbank", "alkuraimi", // اليمن
        "bankofpalestine", "palpay", // فلسطين
        "saharabank", "wahdabank", "jumhouriabank", // ليبيا
        "bankofkhartoum", "faisalbanksudan", // السودان
        "attijari", "banquepopulaire", "cihbank", // المغرب
        "biat", "banquedetunisie", "attijaritn", // تونس
        "cpabank", "bnabank", "baridimob" // الجزائر
    )

    /**
     * SMS/RCS clients. Bank messages arrive here as ordinary notifications, and
     * reading them through this service is what lets the app drop `RECEIVE_SMS`
     * and `READ_SMS` entirely (PRODUCT_PLAN.md §5 / Phase A6) — Play Store
     * treats both as restricted permissions, and a notification listener the
     * user explicitly grants covers the same ground.
     *
     * Messaging apps aren't in `trackedPackages`, so a hit from one only passes
     * through the generic keyword+amount check in `isFinancialNotification`,
     * never an automatic package match.
     */

    /**
     * System/OS surfaces that never carry a transaction but do carry currency-ish
     * strings (Play Store purchase prompts, download progress, media controls).
     *
     * This replaces a blanket `com.google.*` / `com.android.*` exclusion, which
     * was the single reason bank SMS never reached the parser: Google Messages
     * is `com.google.android.apps.messaging` and AOSP SMS is `com.android.mms`,
     * so the prefix rule silently discarded every bank message on the device.
     */
    private val ignoredPackages = listOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.providers",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.nbu.files",
        "com.google.android.youtube",
        "com.google.android.gm",
        "com.whatsapp", "com.instagram.android", "com.facebook",
        "com.twitter", "com.snapchat", "org.telegram"
    )

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * لما المستخدم يفعّل صلاحية الوصول للإشعارات لأول مرة، أندرويد بيوصل onListenerConnected()
     * ومعاه أي إشعار لسه ظاهر في الشريط وقتها (مش تاريخ كامل — أندرويد مالوش history لإشعارات
     * اتشالت قبل كده). ده بيمسك على الأقل إشعارات بنكية جاية النهاردة قبل ما المستخدم يفعّل الصلاحية.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        // علامة إن السيرفس عايش فعلاً — مش إن الصلاحية ممنوحة. الاتنين كانوا بيتخلطوا في
        // الواجهة، وده اللي خلّى بانر "تمام" يظهر لتطبيق أعمى.
        BankReadingStatus.recordListenerConnected(applicationContext)
        try {
            val prefs = applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            val processedKeys = prefs.getStringSet("processed_notification_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
            val dayMs = 24 * 60 * 60 * 1000L

            activeNotifications?.forEach { sbn ->
                val packageName = sbn.packageName
                if (isIgnoredPackage(packageName)) return@forEach
                // بس الإشعارات اللي جاية آخر 24 ساعة — إشعار بنكي قديم فاضل معلّق (بعض البنوك
                // مابتشيلوش) متتحسبش كل مرة السيرفس يعيد الاتصال (زي بعد إعادة تشغيل الجهاز)
                if (System.currentTimeMillis() - sbn.postTime > dayMs) return@forEach
                // مفتاح ثابت لنفس نسخة الإشعار — يمنع إعادة معالجته لو السيرفس اتقفل وفتح تاني
                // والإشعار لسه معلّق (خلاف TxDeduplicator اللي بصمته زمنية 10 دقايق بس)
                if (sbn.key in processedKeys) return@forEach

                val (title, text) = extractContent(sbn)
                if (isFinancialNotification(packageName, title, text)) {
                    Log.d("UnifiedBankListener", "Active notification on connect: $packageName - $title")
                    processedKeys.add(sbn.key)
                    serviceScope.launch { processAndTrackNotification(packageName, title, text) }
                }
            }

            // احتفظ بآخر 300 مفتاح بس عشان الـ SharedPreferences ميكبرش من غير حد
            val trimmed = if (processedKeys.size > 300) processedKeys.toList().takeLast(300).toMutableSet() else processedKeys
            prefs.edit().putStringSet("processed_notification_keys", trimmed).apply()
        } catch (e: Exception) {
            Log.e("UnifiedBankListener", "onListenerConnected() scan failed: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notification ->
            val packageName = notification.packageName
            if (isIgnoredPackage(packageName)) return

            // بيتسجّل قبل أي فلترة عن قصد: من غيره مفيش طريقة تفرّق بين "السيرفس مش شغال"
            // و"السيرفس شغال وكل إشعار اترفض" — والاتنين شكلهم واحد من برّه: مفيش معاملات.
            BankReadingStatus.recordSawNotification(applicationContext)

            val (title, text) = extractContent(notification)

            // إشعار الاختبار التشخيصي — الكشف من marker person مستقل عن اللغة، مش من
            // نص العنوان (اللي بيتغير حسب لغة التطبيق). بيتعلم فوراً عشان شاشة
            // التشخيص تعرف إن السيرفس حي.
            if (notification.extras.getStringArray(android.app.Notification.EXTRA_PEOPLE)
                    ?.contains(com.example.data.BankReadingStatus.TEST_MARKER_PERSON) == true) {
                BankReadingStatus.markTestReceived(applicationContext)
                return
            }

            if (isFinancialNotification(packageName, title, text)) {
                Log.d("UnifiedBankListener", "Financial notification: $packageName - $title")
                serviceScope.launch {
                    processAndTrackNotification(packageName, title, text)
                }
                return
            }

            // ── الالتقاط الشامل (2026-08-24): أي إشعار من تطبيق غير معروف فيه مبلغ نقدي
            // بيتتبعت للعقل برضه. الفلترة المحلية الصارمة كانت ممكن ترمي عمليات حقيقية من
            // بنوك/محافظ مش في القايمة — والعقل (Gemini) أحكم في التمييز بين عملية فعلية
            // وإشعار عرض ترويجي. حد أقصى يومي عشان الكوتة ما تتحرقش على إشعارات زبالة.
            if (hasUnparsedAmount(text) && dailyBroadCatchCount() < BROAD_CATCH_DAILY_CAP) {
                serviceScope.launch {
                    incrementBroadCatchCount()
                    processAndTrackNotification(packageName, title, text)
                }
            }
        }
    }

    /**
     * رقم نقدي في نص الإشعار — أخف بكثير من [SaBankParser.extractAmount] المفصلية،
     * دورها بس بوابة دخول للعقل مش تحليل.
     *
     * الفلترة:
     * - المبالغ الحقيقية عادة فيها فاصل عشري أو فاصلة آلاف ("125.50", "1,250") → تمرّ.
     * - OTP/أكواد التحقق: 4-6 خانات متتالية من غير أي فواصل → تتجاهل.
     * - أرقام مرجعية طويلة (8+ خانات متصلة، غالباً رقم عملية) → تتجاهل حتى لو عدّت
     *   حد المبلغ؛ الرقم المرجعي مش مبلغ.
     */
    private val plainNumberRegex = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val otpLikeRegex = Regex("""(?<![\d.,])\d{4,6}(?![\d.,])""")

    private fun hasUnparsedAmount(text: String): Boolean {
        return plainNumberRegex.findAll(text).any { m ->
            val raw = m.value
            // OTP/كود تحقق: 4-6 خانات معزولة بدون فواصل ولا كسر عشري — مش دليل على عملية.
            if (raw.length in 4..6 && !raw.contains(',') && !raw.contains('.') &&
                otpLikeRegex.find(raw) != null) return@any false
            val amount = raw.replace(",", "").toDoubleOrNull() ?: return@any false
            if (amount < 10.0 || amount > 9_999_999.0) return@any false
            // رقم صحيح طويل متصل بدون فاصلة آلاف ولا كسر = مرجّح رقم مرجعي مش مبلغ
            // (مثال: 20260824). المبالغ بتتكتب "12,500" أو "125.50" في إشعارات البنوك.
            !(raw.length >= 8 && !raw.contains(',') && !raw.contains('.'))
        }
    }

    private fun dailyBroadCatchCount(): Int =
        applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            .getInt("broad_catch_${dayKey()}", 0)

    private fun incrementBroadCatchCount() {
        applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            .edit().putInt("broad_catch_${dayKey()}", dailyBroadCatchCount() + 1).apply()
    }

    private fun dayKey(): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    companion object {
        /** سقف الالتقاط الشامل اليومي — كوتة العقل مش مجانية والإشعارات الترويجية كتير. */
        const val BROAD_CATCH_DAILY_CAP = 40
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun isIgnoredPackage(packageName: String): Boolean =
        packageName == applicationContext.packageName ||
            ignoredPackages.any { packageName == it || packageName.startsWith("$it.") }

    /**
     * Notification body, un-truncated.
     *
     * `android.text` is the collapsed one-line form — a bank SMS shown through a
     * messaging app is almost always cut off there, and the cut usually lands
     * before the amount. `android.bigText` (expanded view) and
     * `android.textLines` (inbox style, one entry per message) carry the full
     * body, so both are preferred when present. `android.subText` is appended
     * because some banks put the account tail there.
     */
    private fun extractContent(sbn: StatusBarNotification): Pair<String, String> {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
            ?: extras.getCharSequence("android.title")?.toString()
            ?: ""
        val big = extras.getCharSequence("android.bigText")?.toString()
        val lines = extras.getCharSequenceArray("android.textLines")
            ?.joinToString("\n") { it.toString() }
            ?.takeIf { it.isNotBlank() }
        val plain = extras.getCharSequence("android.text")?.toString()
        val sub = extras.getCharSequence("android.subText")?.toString()

        val body = listOfNotNull(
            big?.takeIf { it.isNotBlank() } ?: lines ?: plain,
            sub?.takeIf { it.isNotBlank() && it != plain }
        ).joinToString(" ")

        return title to body
    }

    /**
     * Money keywords across the markets the app ships to (مرحلة ٢ — ١٩ سوق عربي + تركيا)،
     * plus the wallet and e-commerce vocabulary that bank-only wording missed ("محفظة",
     * "تم استلام", "طلبك", "refund", "cashback", …).
     *
     * أغلب مفردات البنوك (خصم/شراء/دفع/رصيد/تحويل/مبلغ/بطاقة/سحب/راتب/قسط/فاتورة) فصحى
     * رسمية مستخدمة في رسائل البنوك بكل الدول العربية بغض النظر عن لهجة الكلام اليومي —
     * مش محتاجة نسخة لكل بلد. المضاف هنا فعلياً جديد: رموز/أكواد العملات الـ١٦ الجديدة
     * (AED/KWD/QAR/BHD/OMR/JOD/LBP/IQD/SYP/YER/ILS/LYD/SDG/MAD/TND/DZD)، ومفردات فرنساوية
     * — بنوك المغرب/الجزائر/تونس كتير بتبعت رسائلها بالفرنساوي بدل العربي.
     */
    private val moneyKeywords = listOf(
        // عربي — بنوك (فصحى، مشتركة بين كل الأسواق العربية)
        "ر.س", "رس", "ريال", "SAR", "خصم", "شراء", "دفع", "تم الدفع",
        "رصيد", "إيداع", "تحويل", "مبلغ", "بطاقة", "مشتريات", "سحب",
        "راتب", "مرتب", "مدين", "دائن", "قسط", "فاتورة", "اشتراك",
        // عربي — محافظ وتجارة
        "محفظة", "تم استلام", "تم إرسال", "تم تحويل", "عملية", "معاملة",
        "طلبك", "استرجاع", "استرداد", "كاش باك", "نقاط", "تم الشراء",
        // إنجليزي
        "pay", "paid", "purchase", "amount", "debit", "credit", "transfer",
        "balance", "deposit", "withdraw", "refund", "cashback", "receipt",
        "transaction", "charged", "order total", "salary",
        // تركي
        "TL", "₺", "TRY", "ödeme", "harcama", "bakiye", "kartınızdan",
        "fatura", "maaş", "iade", "havale", "eft", "işlem",
        // مصري
        "EGP", "ج.م", "جنيه",
        // مرحلة ٢ — رموز/أكواد عملات الأسواق الجديدة (خليجي/شامي/عراقي/مغاربي)
        "AED", "د.إ", "KWD", "د.ك", "QAR", "ر.ق", "BHD", "د.ب", "OMR", "ر.ع",
        "JOD", "د.أ", "LBP", "ل.ل", "IQD", "د.ع", "SYP", "ل.س", "YER", "ر.ي",
        "ILS", "₪", "LYD", "د.ل", "SDG", "ج.س", "MAD", "د.م", "TND", "د.ت",
        "DZD", "د.ج", "دينار", "درهم",
        // فرنساوي — بنوك المغرب/الجزائر/تونس غالباً بتبعت رسايلها بالفرنساوي
        "paiement", "achat", "solde", "virement", "retrait", "carte bancaire", "montant", "débit", "crédit"
    )

    /**
     * A notification is worth parsing when it comes from a financial app, or —
     * for everything else, messaging apps included — when it both talks about
     * money and carries a number the parser can actually read.
     *
     * The amount requirement is what makes opening this up to messaging apps
     * safe: a chat that merely says "دفعت" produces no amount and is dropped
     * before it ever reaches `SaBankParser`.
     */
    private fun isFinancialNotification(packageName: String, title: String, text: String): Boolean {
        val content = "$title $text"
        if (content.isBlank()) return false
        val isFinancialApp = trackedPackages.any { packageName.contains(it, ignoreCase = true) }
        if (isFinancialApp) return true

        val hasKeyword = moneyKeywords.any { content.contains(it, ignoreCase = true) }
        if (!hasKeyword) return false
        return SaBankParser.extractAmount(content) != null
    }

    private suspend fun processAndTrackNotification(packageName: String, title: String, text: String) {
        try {
            // A notification must be classified before it can reach the write path.  In
            // particular, failed/pending renewals can mention an amount but are not debits.
            val result = SaBankParser.classifyNotification(packageName, title, text, applicationContext)

            // كل إشعار مالي بيوصل العقل، مش اللي المحلل المحلي حلّه لوحده بس.
            //
            // قبل كده النداء ده كان بيتعمل في حالة COMPLETED_TRANSACTION بس، والباقي كان
            // بيرجع بدري. النتيجة إن السيرفر — اللي عنده الـAI ومسار تأكيد كامل بيحط سؤال
            // حقيقي في zad_insights للرسايل غير الواضحة — ماكانش بيشوف غير الحالات اللي
            // مامحتاجاهوش أصلاً. الرسايل الغامضة، وهي بالظبط اللي محتاجة ذكاء، كانت بتتركن
            // في outbox محلي وتفضل هناك.
            //
            // ودي كمان السبب إن zad_notification_ingest_events فاضي: الجدول بيتكتب أول سطر
            // في المعالج السيرفري، فوجوده فاضي ماكانش بيفرّق بين "المستمع مش شغال" و"المستمع
            // شغال وكل حاجة اترفضت محليًا". دلوقتي بيفرّق.
            //
            // بوابة الكتابة نفسها ما اتغيّرتش: السيرفر لسه مايكتبش معاملة إلا لو العميل قال
            // "completed" والثقة ≥0.9. إرسال الغامض بيخلّيه يتسجّل ويتسأل عنه، مش يتكتب.
            val serverDecisionEarly = if (result.classification != NotificationClassification.COMPLETED_TRANSACTION) {
                sendNotificationToSharedBrain(packageName, title, text, result.classification, result.transaction)
            } else null

            when (result.classification) {
                NotificationClassification.FAILED_OR_PENDING_TRANSACTION,
                NotificationClassification.INFORMATIONAL_ONLY -> {
                    result.rejectionReason?.let { reason ->
                        SaBankParser.logRejection(applicationContext, reason, packageName, "$title $text")
                    }
                    Log.d("UnifiedBankListener", "Notification ignored: ${result.classification}")
                    return
                }
                NotificationClassification.AMBIGUOUS -> {
                    // Never let the AI fallback manufacture a completed transaction from an
                    // unclear message — that rule is unchanged. What changed is who gets asked:
                    // the server now sees this and raises a real confirmation question in
                    // zad_insights ("معاملة بنكية محتاجة تأكيد"), which is the Stage 2 tier the
                    // old comment here was waiting for. The local outbox stays as the fallback
                    // for when that call couldn't be made at all (null = transport failure).
                    SaBankParser.logRejection(applicationContext, SaBankParser.RejectReason.UNPARSED, packageName, "$title $text")
                    if (serverDecisionEarly == null || serverDecisionEarly == "delivery_retry") {
                        SyncOutbox.enqueueUnparsedNotification(applicationContext, packageName, title, text)
                    }
                    Log.d("UnifiedBankListener", "Notification requires confirmation: $title (server=$serverDecisionEarly)")
                    return
                }
                NotificationClassification.COMPLETED_TRANSACTION -> Unit
            }

            val parsed = result.transaction ?: return
            val serverDecision = sendNotificationToSharedBrain(packageName, title, text, result.classification, parsed)
            when (serverDecision) {
                // awaiting_confirmation هي الرد الطبيعي دلوقتي لأي إشعار مقروء: السيرفر بعت
                // سؤال تأكيد (تيليجرام أو رؤى زاد) والمعاملة مش هتتكتب غير لما العميل يقول
                // أيوة. من غير الحالة دي هنا كانت هتقع في else وتتكتب محلياً — يعني نفس
                // العملية تتخصم من الكارت من غير موافقة، وهو بالظبط اللي التغيير ده بيمنعه.
                "logged", "ignored", "awaiting_confirmation" -> {
                    Log.d("UnifiedBankListener", "zad-brain handled notification as $serverDecision")
                    return
                }
                "ambiguous" -> {
                    SyncOutbox.enqueueUnparsedNotification(applicationContext, packageName, title, text)
                    Log.d("UnifiedBankListener", "zad-brain requested confirmation for notification")
                    return
                }
                null -> {
                    // السيرفر مش موصول. الكتابة المحلية هنا كانت بتخصم من الكارت من غير ما
                    // العميل يوافق — نفس الحاجة اللي اتقفلت فوق، بس من باب تاني. الطابور
                    // بيرجّع الإشعار لنفس مسار التأكيد أول ما الشبكة ترجع؛ التأخير أرخص من
                    // رقم اتغيّر لوحده والعميل ما عندوش فكرة ليه.
                    SyncOutbox.enqueueUnparsedNotification(applicationContext, packageName, title, text)
                    Log.w("UnifiedBankListener", "zad-brain notification ingest unavailable — queued for confirmation instead of writing locally")
                    return
                }
                else -> {
                    SyncOutbox.enqueueUnparsedNotification(applicationContext, packageName, title, text)
                    Log.w("UnifiedBankListener", "zad-brain notification ingest returned $serverDecision — queued for confirmation instead of writing locally")
                    return
                }
            }
            // المسار المحلي اللي كان هنا (TxDeduplicator ← BankTransactionApplier.apply ←
            // BalanceAnchor.reconcile ← إشعارات "تم إيداع الراتب"/"تم خصم اشتراك") اتشال
            // كله. كان بيكتب معاملة ويحرّك الرصيد من غير موافقة العميل، وده الحاجة الوحيدة
            // اللي إعادة الهيكلة دي بتمنعها. كل فرع فوق بيرجع: يا إما السيرفر تعامل معاه،
            // يا إما اتحط في الطابور عشان يتسأل عنه لما الشبكة ترجع.
            //
            // إشعارات الراتب/الاشتراك مالهاش لزمة تتعوّض هنا — رسالة التأكيد نفسها على
            // البوت بقت هي الإخطار، وبتيجي قبل ما الرقم يتغيّر مش بعده.
        } catch (e: Exception) {
            Log.e("UnifiedBankListener", "Error processing: ${e.message}")
        }
    }

    /**
     * Server-first notification ingestion. The listener can hear notifications, but the
     * shared brain is the writer of record so Telegram, Android and Supabase all pass through
     * the same validation/audit path. Returning null means transport failure only; semantic
     * decisions from the server are terminal and must not fall back to a second local write.
     */
    private suspend fun sendNotificationToSharedBrain(
        packageName: String,
        title: String,
        text: String,
        classification: NotificationClassification,
        parsed: ParsedBankTx?
    ): String? {
        return try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id ?: return null
            val response = SupabaseRepo.callEdgeFunction(
                "zad-brain",
                mapOf(
                    "action" to "notification_ingest",
                    "user_id" to userId,
                    "source" to "notification_listener",
                    "package_name" to packageName,
                    "title" to title,
                    "text" to text,
                    "client_classification" to classification.name.lowercase(),
                    // بدون تحليل محلي بيتبعت object فاضي عن قصد، مش يتشال: السيرفر بيقرا
                    // `parsed.confidence` ويقارنه بـ0.9، وقيمة ناقصة بتتقرا صفر — يعني
                    // "محتاج تأكيد"، وهو بالظبط التصنيف الصح للحالة دي.
                    "parsed" to (parsed?.let {
                        mapOf(
                            "amount" to it.amount,
                            "is_expense" to it.isExpense,
                            "title" to it.title,
                            "category" to it.category,
                            "merchant_name" to (it.merchantName ?: it.bankName),
                            "bank_name" to it.bankName,
                            "txn_kind" to if (it.txType == TxType.WITHDRAWAL) "transfer" else if (it.isExpense) "expense" else "income",
                            "tx_type" to it.txType.name,
                            "currency" to (it.currency ?: ""),
                            "confidence" to it.confidence.toDouble(),
                            "external_ref" to (it.externalRef ?: "")
                        )
                    } ?: emptyMap<String, Any>())
                ),
                timeoutMs = 20_000L
            )
            response["status"]?.toString()
        } catch (e: Exception) {
            Log.e("UnifiedBankListener", "notification_ingest failed: ${e.message}")
            null
        }
    }

}
