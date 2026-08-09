package com.example.data

import org.json.JSONObject

/**
 * قراءة تعليمات `[[ACTION:{...}]]` اللي زاد بيكتبها في آخر رد الشات.
 *
 * منفصلة عن ZadViewModel عمداً: الجزء ده منطق نصي خالص (استخراج + تحقق) من غير أي
 * تبعية على Application أو Supabase أو حالة المخزون، فينفع يتغطى باختبارات وحدة عادية.
 * الـ ViewModel بيفضل مسؤول عن التنفيذ نفسه — إيه اللي يتكتب فوراً وإيه اللي يستنى تأكيد.
 *
 * بيرجع **كل** الـ actions مش أول واحدة: رسالة زي "سجّل مشتريات الأسبوع: فراخ ولحمة
 * وطماطم ومكرونة" لازم تطلع أربع إضافات. السلوك القديم (`Regex.find`) كان بياخد أول
 * واحدة ويشيل الباقي من النص من غير تنفيذ، فالرد كان بيقرا كإن كله اتسجّل والمخزون
 * فيه صنف واحد بس.
 */
object ChatActionParser {

    private val ACTION_REGEX = Regex("""\[\[ACTION:(\{.*?\})\]\]""", RegexOption.DOT_MATCHES_ALL)

    /** أقصى كمية مقبولة في action واحدة — رقم شارد من الموديل مايبقاش ٩٩٩٩ علبة لبن. */
    private const val MAX_QUANTITY = 999

    enum class Type { CONSUME, ADD, ADD_PHARMACY, PHARMACY_DOSE }

    data class ChatAction(
        val type: Type,
        val itemName: String,
        val amount: Int,
        val unit: String?,
        val category: String?,
        val dosage: String?,
        val dailyDoseCount: Int,
        val doseTimes: String?
    )

    /** [cleanText] = رد زاد من غير أي وسم ACTION، وهو اللي بيتعرض للمستخدم. */
    data class Result(val cleanText: String, val actions: List<ChatAction>)

    fun parse(rawResponse: String): Result {
        val matches = ACTION_REGEX.findAll(rawResponse).toList()
        if (matches.isEmpty()) return Result(rawResponse, emptyList())

        var cleanText = rawResponse
        matches.forEach { cleanText = cleanText.replace(it.value, "") }

        // action مش مفهومة بتتشال من النص وتتجاهل لوحدها — الباقي بيتنفذ عادي، بدل ما
        // رد كامل يضيع بسبب صنف واحد الموديل كتبه غلط.
        val actions = matches.mapNotNull { parseOne(it.groupValues[1]) }
        return Result(cleanText.trim(), actions)
    }

    private fun parseOne(json: String): ChatAction? {
        return try {
            val obj = JSONObject(json)
            val itemName = obj.optString("item").trim()
            if (itemName.isBlank()) return null
            val type = when (obj.optString("type")) {
                "consume" -> Type.CONSUME
                "add" -> Type.ADD
                "add_pharmacy" -> Type.ADD_PHARMACY
                "pharmacy_dose" -> Type.PHARMACY_DOSE
                else -> return null
            }
            ChatAction(
                type = type,
                itemName = itemName,
                amount = obj.optInt("amount", 1).coerceIn(1, MAX_QUANTITY),
                unit = obj.optString("unit").trim().ifBlank { null },
                category = obj.optString("category").trim().ifBlank { null },
                dosage = obj.optString("dosage").trim().ifBlank { null },
                dailyDoseCount = obj.optInt("daily_dose_count", 1).coerceIn(1, 12),
                doseTimes = obj.optString("dose_times").trim().ifBlank { null }
            )
        } catch (e: Exception) {
            null
        }
    }
}
