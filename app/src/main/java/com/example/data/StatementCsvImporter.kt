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
        return cleaned.toDoubleOrNull()
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

    suspend fun commitImport(context: Context, rows: List<PreviewRow>): Pair<Int, Int> {
        val dao = ZadDatabase.getDatabase(context.applicationContext).zadDao()
        val existingFingerprints = try {
            dao.getAllTransactionsOnce().mapTo(mutableSetOf()) {
                "${"%.2f".format(it.amount)}|${it.isExpense}|${it.createdAt?.take(10)}"
            }
        } catch (e: Exception) { emptySet() }

        var imported = 0
        var skippedDuplicate = 0
        for (row in rows) {
            if (row.hasError || row.amount == null || row.date == null) continue
            val createdAt = "${row.date}T00:00:00Z"
            val fingerprint = "${"%.2f".format(row.amount)}|${row.isExpense}|${row.date}"
            if (fingerprint in existingFingerprints) { skippedDuplicate++; continue }

            val transaction = ZadTransaction(
                title = row.title,
                amount = row.amount,
                isExpense = row.isExpense,
                category = row.category,
                createdAt = createdAt,
                sourceType = "csv_import"
            )
            dao.insertTransaction(transaction)
            try { SupabaseRepo.addTransaction(transaction) } catch (e: Exception) {
                Log.e(TAG, "Supabase sync failed for imported row: ${e.message}")
            }
            imported++
        }
        Log.d(TAG, "commitImport() → imported=$imported, skippedDuplicate=$skippedDuplicate")
        return imported to skippedDuplicate
    }
}
