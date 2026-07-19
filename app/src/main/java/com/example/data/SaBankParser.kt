package com.example.data

import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG_BANK = "SaBankParser"

data class ParsedBankTx(
    val amount: Double,
    val isExpense: Boolean,
    val title: String,
    val category: String,
    val bankName: String,
    val merchantName: String?,
    val rawText: String
)

object SaBankParser {

    private val sarAmountPattern = Regex("""(\d+(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:ر\.س|رس|ريال|SAR)""", RegexOption.IGNORE_CASE)
    private val sarAmountPatternRev = Regex("""(?:ر\.س|رس|ريال|SAR)\s*(\d+(?:,\d{3})*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val numberCleaner = Regex(",")

    private fun extractAmount(text: String): Double? {
        val m1 = sarAmountPattern.find(text)
        val m2 = sarAmountPatternRev.find(text)
        val raw = m1?.groupValues?.get(1) ?: m2?.groupValues?.get(1) ?: return null
        return raw.replace(numberCleaner, "").toDoubleOrNull()
    }

    private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    private fun merchantFromTitle(title: String): String? {
        val keywords = listOf("شراء من", "في", "عند", "خصم من", "payment to", "at", "purchase at")
        for (kw in keywords) {
            val idx = title.indexOf(kw, ignoreCase = true)
            if (idx >= 0) return title.substring(idx + kw.length).trim().take(30)
        }
        return null
    }

    // ─── Al Rajhi ──────────────────────────────────────────────
    fun parseAlRajhi(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = !text.contains("إيداع", ignoreCase = true) &&
                !text.contains("تحويل وار", ignoreCase = true) &&
                !text.contains("مُودَع", ignoreCase = true) &&
                !text.contains("قيد دائن", ignoreCase = true)

        val category = when {
            text.contains("راتب", ignoreCase = true) || text.contains("مرتب", ignoreCase = true) -> "الراتب"
            text.contains("محطة", ignoreCase = true) || text.contains("بنزين", ignoreCase = true) || text.contains("وقود", ignoreCase = true) -> "الوقود"
            text.contains("بقالة", ignoreCase = true) || text.contains("تموين", ignoreCase = true) || text.contains("سوبر", ignoreCase = true) || text.contains("خضار", ignoreCase = true) -> "البقالة"
            text.contains("مطعم", ignoreCase = true) || text.contains("كافيه", ignoreCase = true) || text.contains("وجبات", ignoreCase = true) || text.contains("hungerstation", ignoreCase = true) || text.contains("mrsool", ignoreCase = true) || text.contains("jahez", ignoreCase = true) || text.contains("توصيل", ignoreCase = true) -> "المطاعم"
            text.contains("كهرب", ignoreCase = true) || text.contains("سداد", ignoreCase = true) || text.contains("فواتير", ignoreCase = true) || text.contains("المياه", ignoreCase = true) || text.contains("اتصالات", ignoreCase = true) || text.contains("stc", ignoreCase = true) || text.contains("mobily", ignoreCase = true) || text.contains("zain", ignoreCase = true) -> "الفواتير"
            text.contains("علاج", ignoreCase = true) || text.contains("صيدلية", ignoreCase = true) || text.contains("مستشفى", ignoreCase = true) || text.contains("عيادة", ignoreCase = true) || text.contains("دواء", ignoreCase = true) -> "الرعاية الصحية"
            text.contains("مواصلات", ignoreCase = true) || text.contains("أوبر", ignoreCase = true) || text.contains("كريم", ignoreCase = true) || text.contains("taxi", ignoreCase = true) || text.contains("نقل", ignoreCase = true) || text.contains("طيران", ignoreCase = true) -> "المواصلات"
            text.contains("تعليم", ignoreCase = true) || text.contains("مدرسة", ignoreCase = true) || text.contains("جامعة", ignoreCase = true) || text.contains("دورة", ignoreCase = true) || text.contains("تدريب", ignoreCase = true) -> "التعليم"
            text.contains("تابي", ignoreCase = true) || text.contains("تمارة", ignoreCase = true) || text.contains("قسط", ignoreCase = true) || text.contains("أقساط", ignoreCase = true) -> "الأقساط"
            text.contains("نتفلكس", ignoreCase = true) || text.contains("netflix", ignoreCase = true) || text.contains("شاهد", ignoreCase = true) || text.contains("شهيد", ignoreCase = true) || text.contains("spotify", ignoreCase = true) || text.contains("youtube", ignoreCase = true) -> "الاشتراكات"
            text.contains("stc pay", ignoreCase = true) || text.contains("تحويل", ignoreCase = true) -> "تحويلات"
            else -> "أخرى"
        }

        val merchant = merchantFromTitle("$title $text")

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "الراجحي: ${title.ifBlank { "معاملة" }}",
            category = category,
            bankName = "الراجحي",
            merchantName = merchant,
            rawText = text.take(100)
        )
    }

    // ─── SNB (National Commercial Bank) ────────────────────────
    fun parseSnb(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = !text.contains("إيداع", ignoreCase = true) &&
                !text.contains("دائن", ignoreCase = true) &&
                !text.contains("وارد", ignoreCase = true)

        val category = classifyGeneral(text)

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "الأهلي: ${title.ifBlank { "معاملة" }}",
            category = category,
            bankName = "الأهلي السعودي",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── Riyad Bank ────────────────────────────────────────────
    fun parseRiyad(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = !text.contains("إيداع", ignoreCase = true) &&
                !text.contains("دائن", ignoreCase = true)

        val category = classifyGeneral(text)

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "الرياض: ${title.ifBlank { "معاملة" }}",
            category = category,
            bankName = "بنك الرياض",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── SABB ──────────────────────────────────────────────────
    fun parseSabb(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = !text.contains("credit", ignoreCase = true) &&
                !text.contains("deposit", ignoreCase = true)

        val category = classifyGeneral(text)

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "ساب: ${title.ifBlank { "معاملة" }}",
            category = category,
            bankName = "ساب",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── Alinma ────────────────────────────────────────────────
    fun parseAlinma(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = !text.contains("إيداع", ignoreCase = true) &&
                !text.contains("دائن", ignoreCase = true)

        val category = classifyGeneral(text)

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "الإنماء: ${title.ifBlank { "معاملة" }}",
            category = category,
            bankName = "مصرف الإنماء",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── STC Pay ───────────────────────────────────────────────
    fun parseStcPay(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = text.contains("خصم", ignoreCase = true) ||
                text.contains("دفع", ignoreCase = true) ||
                text.contains("شراء", ignoreCase = true) ||
                text.contains("send", ignoreCase = true) ||
                text.contains("transfer to", ignoreCase = true)

        val category = classifyGeneral(text)

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "stc pay: ${title.ifBlank { "معاملة" }}",
            category = category,
            bankName = "stc pay",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── Tabby ─────────────────────────────────────────────────
    fun parseTabby(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = true

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "تابي: ${title.ifBlank { "قسط" }}",
            category = "الأقساط",
            bankName = "تابي",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── Tamara ────────────────────────────────────────────────
    fun parseTamara(title: String, text: String): ParsedBankTx? {
        val amount = extractAmount(text) ?: return null
        val isExpense = true

        return ParsedBankTx(
            amount = amount,
            isExpense = isExpense,
            title = "تمارة: ${title.ifBlank { "قسط" }}",
            category = "الأقساط",
            bankName = "تمارة",
            merchantName = merchantFromTitle("$title $text"),
            rawText = text.take(100)
        )
    }

    // ─── General Classification ─────────────────────────────────
    private fun classifyGeneral(text: String): String = when {
        text.contains("راتب", ignoreCase = true) || text.contains("مرتب", ignoreCase = true) -> "الراتب"
        text.contains("بقالة", ignoreCase = true) || text.contains("تموين", ignoreCase = true) || text.contains("سوبر", ignoreCase = true) || text.contains("خضار", ignoreCase = true) || text.contains("لحوم", ignoreCase = true) -> "البقالة"
        text.contains("مطعم", ignoreCase = true) || text.contains("كافيه", ignoreCase = true) || text.contains("وجبات", ignoreCase = true) || text.contains("hungerstation", ignoreCase = true) || text.contains("mrsool", ignoreCase = true) || text.contains("jahez", ignoreCase = true) || text.contains("توصيل", ignoreCase = true) || text.contains("طلب", ignoreCase = true) || text.contains("مأكولات", ignoreCase = true) -> "المطاعم"
        text.contains("كهرب", ignoreCase = true) || text.contains("سداد", ignoreCase = true) || text.contains("فواتير", ignoreCase = true) || text.contains("المياه", ignoreCase = true) || text.contains("اتصالات", ignoreCase = true) || text.contains("stc", ignoreCase = true) || text.contains("mobily", ignoreCase = true) || text.contains("zain", ignoreCase = true) -> "الفواتير"
        text.contains("علاج", ignoreCase = true) || text.contains("صيدلية", ignoreCase = true) || text.contains("مستشفى", ignoreCase = true) || text.contains("عيادة", ignoreCase = true) || text.contains("دواء", ignoreCase = true) -> "الرعاية الصحية"
        text.contains("مواصلات", ignoreCase = true) || text.contains("أوبر", ignoreCase = true) || text.contains("كريم", ignoreCase = true) || text.contains("taxi", ignoreCase = true) || text.contains("نقل", ignoreCase = true) || text.contains("طيران", ignoreCase = true) || text.contains("باص", ignoreCase = true) -> "المواصلات"
        text.contains("تعليم", ignoreCase = true) || text.contains("مدرسة", ignoreCase = true) || text.contains("جامعة", ignoreCase = true) || text.contains("دورة", ignoreCase = true) || text.contains("تدريب", ignoreCase = true) || text.contains("منصة", ignoreCase = true) -> "التعليم"
        text.contains("تابي", ignoreCase = true) || text.contains("تمارة", ignoreCase = true) || text.contains("قسط", ignoreCase = true) || text.contains("أقساط", ignoreCase = true) -> "الأقساط"
        text.contains("نتفلكس", ignoreCase = true) || text.contains("netflix", ignoreCase = true) || text.contains("شاهد", ignoreCase = true) || text.contains("شهيد", ignoreCase = true) || text.contains("spotify", ignoreCase = true) || text.contains("youtube", ignoreCase = true) || text.contains("apple music", ignoreCase = true) -> "الاشتراكات"
        text.contains("محطة", ignoreCase = true) || text.contains("بنزين", ignoreCase = true) || text.contains("وقود", ignoreCase = true) || text.contains("ديزل", ignoreCase = true) -> "الوقود"
        text.contains("stc pay", ignoreCase = true) || text.contains("تحويل", ignoreCase = true) -> "تحويلات"
        text.contains("tabby", ignoreCase = true) || text.contains("tamara", ignoreCase = true) -> "الأقساط"
        else -> "أخرى"
    }

    // ─── Package-based auto-detect ──────────────────────────────
    fun detectAndParse(packageName: String, title: String, text: String): ParsedBankTx? {
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("alrajhi") || pkg.contains("rajhi") -> parseAlRajhi(title, text)
            pkg.contains("snb") || pkg.contains("alahli") || pkg.contains("ncba") || (pkg.contains("national") && pkg.contains("commercial")) -> parseSnb(title, text)
            pkg.contains("riyad") || pkg.contains("riyadh") -> parseRiyad(title, text)
            pkg.contains("sabb") -> parseSabb(title, text)
            pkg.contains("alinma") || pkg.contains("enmaa") -> parseAlinma(title, text)
            pkg.contains("stcpay") || pkg.contains("stc pay") -> parseStcPay(title, text)
            pkg.contains("tabby") -> parseTabby(title, text)
            pkg.contains("tamara") -> parseTamara(title, text)
            else -> null
        }
    }
}
