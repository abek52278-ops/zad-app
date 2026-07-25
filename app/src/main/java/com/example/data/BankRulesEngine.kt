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

    private fun typeToTxType(type: String): TxType = when (type) {
        "debit" -> TxType.PURCHASE
        "refund" -> TxType.REFUND
        "salary" -> TxType.SALARY
        "credit" -> TxType.DEPOSIT
        else -> TxType.PURCHASE
    }

    /** يرجع null لو مفيش rule سندرها يطابق، أو الـ rule طابق بس المبلغ متعرفش يتفسّر */
    fun tryParse(context: Context, source: String, fullText: String): ParsedBankTx? {
        val rules = loadRules(context)
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
                externalRef = externalRef
            )
        }
        return null
    }
}
