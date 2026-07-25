package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.ZadDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * استيراد كشف حساب CSV — مافيش تنسيق موحّد بين البنوك، فبدل ما نخمّن الأعمدة
 * (خطر حقيقي: تخمين غلط = مبالغ غلط بصمت)، المستخدم بيحدد بنفسه أي عمود هو
 * التاريخ/الوصف/المبلغ من عناوين الملف الفعلية، وبيراجع كل صف قبل الاستيراد
 * النهائي — مفيش commit تلقائي بدون مراجعة بشرية.
 *
 * PDF مش مدعوم عمداً — مفيش مكتبة قراءة PDF في المشروع، وOCR بالـ vision AI
 * غير موثوق لجداول مالية متعددة الصفحات (خطر قراءة أرقام غلط).
 */
object StatementCsvImporter {
    private const val TAG = "StatementCsvImporter"

    data class CsvTable(val headers: List<String>, val rows: List<List<String>>)

    data class ColumnMapping(
        val dateColumnIndex: Int,
        val titleColumnIndex: Int,
        val amountColumnIndex: Int,
        val invertSign: Boolean = false,
        val categoryColumnIndex: Int? = null
    )

    data class PreviewRow(
        val rowIndex: Int,
        val date: String?,
        val title: String,
        val amount: Double?,
        val isExpense: Boolean,
        val category: String,
        val hasError: Boolean
    )

    private val dateFormats = listOf(
        "yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "dd.MM.yyyy"
    ).map { DateTimeFormatter.ofPattern(it) }

    fun readCsv(context: Context, uri: Uri): CsvTable? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val lines = input.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
                if (lines.isEmpty()) return null
                val parsed = lines.map { parseCsvLine(it) }
                CsvTable(headers = parsed.first(), rows = parsed.drop(1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "readCsv() FAILED: ${e.message}")
            null
        }
    }

    /** بارسر CSV بسيط بيراعي الحقول المقتبسة "..." اللي فيها فواصل جوه النص */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { fields.add(current.toString().trim()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString().trim())
        return fields
    }

    private fun parseDate(raw: String): LocalDate? {
        val trimmed = raw.trim()
        for (fmt in dateFormats) {
            try { return LocalDate.parse(trimmed, fmt) } catch (e: Exception) { /* try next */ }
        }
        return null
    }

    private fun parseAmount(raw: String): Double? {
        val cleaned = raw.trim().replace(",", "").replace("SAR", "", ignoreCase = true).replace("ر.س", "").trim()
        return cleaned.toDoubleOrNull()?.asMoney()
    }

    fun buildPreview(context: Context, table: CsvTable, mapping: ColumnMapping): List<PreviewRow> {
        return table.rows.mapIndexed { index, row ->
            fun col(i: Int): String = row.getOrNull(i)?.trim() ?: ""

            val date = parseDate(col(mapping.dateColumnIndex))
            val title = col(mapping.titleColumnIndex).ifBlank { "معاملة مستوردة" }
            var amount = parseAmount(col(mapping.amountColumnIndex))
            if (mapping.invertSign) amount = amount?.let { -it }
            val isExpense = (amount ?: 0.0) < 0

            val mappedCategory = mapping.categoryColumnIndex?.let { col(it).ifBlank { null } }
            val category = mappedCategory
                ?: MerchantCategoryOverrides.get(context, title)
                ?: SaBankParser.classify(title, null)

            PreviewRow(
                rowIndex = index,
                date = date?.toString(),
                title = title,
                amount = amount?.let { kotlin.math.abs(it) },
                isExpense = isExpense,
                category = category,
                hasError = amount == null || date == null
            )
        }
    }

    /**
     * "بلاش" مش كافية بمبلغ+يوم بس — يومين معاملات مختلفتين فعلاً بنفس المبلغ في نفس اليوم
     * وارد جداً (قهوتين مثلاً)، فلازم نراعي الوصف/التاجر كمان قبل ما نقرر إنها نفس العملية.
     * مفيش تاجر منفصل في صف الـ CSV — العمود الوحيد المتاح هو title (وصف كشف الحساب)،
     * فبنقارنه بـ merchantName لو موجود وإلا title نفسه للمعاملة المخزّنة.
     */
    internal fun isGenericTitle(title: String?): Boolean =
        title.isNullOrBlank() || title == "معاملة مستوردة"

    internal fun descriptionsCompatible(existing: String?, imported: String): Boolean {
        if (isGenericTitle(existing) || isGenericTitle(imported)) return true
        val e = existing!!.trim().lowercase()
        val i = imported.trim().lowercase()
        return e.contains(i) || i.contains(e)
    }

    /** المعاملة المستوردة دي نفس [tx] الموجودة فعلاً؟ — مبلغ (سماحية ٠.٠٠٥) + اتجاه + نفس اليوم + وصف متوافق */
    internal fun isDuplicateOfExisting(tx: ZadTransaction, row: PreviewRow): Boolean {
        if (row.amount == null || row.date == null) return false
        return tx.isExpense == row.isExpense &&
            kotlin.math.abs(tx.amount - row.amount) < 0.005 &&
            tx.createdAt?.take(10) == row.date &&
            descriptionsCompatible(tx.merchantName ?: tx.title, row.title)
    }

    suspend fun commitImport(context: Context, rows: List<PreviewRow>): Pair<Int, Int> {
        val dao = ZadDatabase.getDatabase(context.applicationContext).zadDao()
        val existingTransactions = try { dao.getAllTransactionsOnce() } catch (e: Exception) { emptyList() }

        var imported = 0
        var skippedDuplicate = 0
        for (row in rows) {
            if (row.hasError || row.amount == null || row.date == null) continue
            val createdAt = "${row.date}T00:00:00Z"

            val isDuplicate = existingTransactions.any { tx -> isDuplicateOfExisting(tx, row) }
            if (isDuplicate) { skippedDuplicate++; continue }

            val transaction = ZadTransaction(
                title = row.title,
                amount = row.amount,
                isExpense = row.isExpense,
                category = row.category,
                createdAt = createdAt,
                sourceType = "csv_import"
            )
            dao.insertTransaction(transaction)
            if (!SupabaseRepo.addTransaction(transaction)) {
                Log.w(TAG, "Supabase sync failed for imported row — queued for retry")
                SyncOutbox.enqueueTransaction(context.applicationContext, transaction)
            }
            imported++
        }
        Log.d(TAG, "commitImport() → imported=$imported, skippedDuplicate=$skippedDuplicate")
        return imported to skippedDuplicate
    }
}
