package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "BankRulesEngine"

@Serializable
data class BankRuleJson(
    val id: String,
    val country: String,
    val senders: List<String>,
    val match: String,
    val merchant: String? = null,
    val ref: String? = null,
    val type: String, // "debit" | "credit" | "refund" | "salary"
    val confidence: Float
)

/**
 * قواعد تحليل رسائل البنوك كبيانات (JSON) بدل كود — بنوك/محافظ جديدة (مصر دلوقتي) تتضاف هنا
 * من غير ما نلمس الـ ٢٢ صيغة سعودية/تركية الشغالة في SaBankParser الكوتلانية.
 * SaBankParser.detectAndParse() بيجرب القواعد دي الأول، ولو مفيش match يرجع لمساره القديم.
 *
 * ملاحظة API: الـ regex بيستخدم (?<name>...) للتوثيق بس — قراءة المجموعة بالاسم
 * (Matcher.group(String)) محتاجة API 26+، بينما minSdk هنا 24. عشان كده بنقرأ المجموعات
 * بالترتيب (groupValues[1], [2]) مش بالاسم — شغال على كل API levels زي بعضه.
 */
object BankRulesEngine {

    @Volatile private var cached: List<BankRuleJson>? = null

    private fun loadRules(context: Context): List<BankRuleJson> {
        cached?.let { return it }
        return try {
            val json = context.assets.open("bank_rules.json").bufferedReader().use { it.readText() }
            val rules = Json { ignoreUnknownKeys = true }.decodeFromString<List<BankRuleJson>>(json)
            cached = rules
            rules
        } catch (e: Exception) {
            Log.e(TAG, "bank_rules.json load failed: ${e.message}")
            emptyList()
        }
    }

    // مرحلة ١ — قواعد JSON محمّلة أصلاً حسب بلد القاعدة (activeCountryCode filter تحت)،
    // فبلد القاعدة نفسه دليل عملة موثوق، بدل ما نعيد نفس regex استخراج SaBankParser هنا.
    private fun countryToCurrency(country: String): String? = when (country.uppercase()) {
        "SA" -> "SAR"
        "EG" -> "EGP"
        "TR" -> "TRY"
        "AE" -> "AED"
        "KW" -> "KWD"
        "QA" -> "QAR"
        "BH" -> "BHD"
        "OM" -> "OMR"
        "JO" -> "JOD"
        "LB" -> "LBP"
        "IQ" -> "IQD"
        "SY" -> "SYP"
        "YE" -> "YER"
        "PS" -> "ILS"
        "LY" -> "LYD"
        "SD" -> "SDG"
        "MA" -> "MAD"
        "TN" -> "TND"
        "DZ" -> "DZD"
        else -> null
    }

    private fun typeToTxType(type: String): TxType = when (type) {
        "debit" -> TxType.PURCHASE
        "withdrawal" -> TxType.WITHDRAWAL
        "refund" -> TxType.REFUND
        "salary" -> TxType.SALARY
        "credit" -> TxType.DEPOSIT
        else -> TxType.PURCHASE
    }

    // Task 21 — بتحمّل بس قواعد البلد الفعّال (زائد "ALL" لو فيه قاعدة عامة لأي بلد)، مش
    // كل الملف. كل بنوك مصر دلوقتي "EG"، فمستخدم سعودي (بيروح للمسار الكوتلاني القديم في
    // SaBankParser أصلاً لأنه مفيش قواعد JSON بـ country="SA") ميقدرش يتفهم غلط برسالة
    // مصرية العملة أو العكس — لو ما اتطابقش هنا، الـ AI fallback (analyze_bank_notification)
    // هو خط الدفاع العام لأي بلد/عملة، مش محرك regex ثاني هنا.
    private fun activeCountryCode(): String = MarketPrefs.currentMarket.localeTag.substringAfter("-")

    /** يرجع null لو مفيش rule سندرها يطابق، أو الـ rule طابق بس المبلغ متعرفش يتفسّر */
    fun tryParse(context: Context, source: String, fullText: String): ParsedBankTx? {
        val country = activeCountryCode()
        val rules = loadRules(context).filter { it.country.equals(country, ignoreCase = true) || it.country.equals("ALL", ignoreCase = true) }
        if (rules.isEmpty()) return null

        val normalizedText = SaBankParser.normalizeDigits(fullText)
        val lowerSource = source.lowercase()
        val lowerText = normalizedText.lowercase()

        for (rule in rules) {
            val senderMatches = rule.senders.any { s ->
                val sl = s.lowercase()
                lowerSource.contains(sl) || lowerText.contains(sl)
            }
            if (!senderMatches) continue

            val amountMatch = try {
                Regex(rule.match, RegexOption.IGNORE_CASE).find(normalizedText)
            } catch (e: Exception) {
                Log.e(TAG, "Bad match regex in rule ${rule.id}: ${e.message}")
                null
            } ?: continue

            // المجموعة رقم ١ = amount حسب اتفاقية كتابة القواعد هنا (مجموعة amount أول واحدة دايماً)
            val amountStr = amountMatch.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: continue
            val amount = SaBankParser.normalizeNumber(amountStr)?.takeIf { it > 0 } ?: continue

            val merchant = rule.merchant?.let { pattern ->
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE).find(normalizedText)
                        ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }
                } catch (e: Exception) {
                    null
                }
            }

            val externalRef = rule.ref?.let { pattern ->
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE).find(normalizedText)
                        ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    null
                }
            }

            val txType = typeToTxType(rule.type)
            val category = SaBankParser.classify(normalizedText, txType)
            val bankName = rule.senders.firstOrNull() ?: rule.id

            Log.d(TAG, "JSON rule matched: ${rule.id} (confidence=${rule.confidence})")

            return ParsedBankTx(
                amount = amount,
                isExpense = txType.isExpense,
                title = "$bankName: ${merchant ?: txType.arabicLabel}",
                category = category,
                bankName = bankName,
                merchantName = merchant,
                rawText = fullText.take(160),
                txType = txType,
                confidence = rule.confidence,
                externalRef = externalRef,
                currency = countryToCurrency(rule.country),
                balance = SaBankParser.extractBalance(normalizedText)
            )
        }
        return null
    }
}
