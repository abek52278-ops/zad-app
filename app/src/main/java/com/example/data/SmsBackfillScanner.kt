package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.ZadDatabase
import java.time.Instant

/**
 * مسح رجعي لصندوق SMS عند أول منح لإذن READ_SMS — بدون ده، UnifiedSmsReceiver
 * بيسمع بس رسايل جديدة توصل بعد التثبيت، ورسايل البنك اللي جاية قبل كده (آخر شهر/شهرين)
 * بتفضل متجاهلة تماماً وميزانية المستخدم متتحدثش. مسح لمرة واحدة (مؤشر SharedPreferences)
 * — مش هيتكرر كل فتحة تطبيق. النطاق الزمني مش نهائي، أي رسالة قبله بتتجاهل بأمان.
 *
 * قيد مهم: إشعارات تطبيقات البنوك (مش SMS) مالهاش history — أندرويد ميحتفظش بيها،
 * فالمسح الرجعي ده بيغطي بنوك الـ SMS بس، مش كل مصدر.
 */
object SmsBackfillScanner {
    private const val TAG = "SmsBackfillScanner"
    private const val PREFS_NAME = "zad_prefs"
    private const val KEY_BACKFILL_DONE = "sms_backfill_done_v1"
    const val DEFAULT_SINCE_DAYS = 60

    suspend fun scanIfNeeded(context: Context, sinceDays: Int = DEFAULT_SINCE_DAYS) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BACKFILL_DONE, false)) return
        try {
            scan(context, sinceDays)
        } finally {
            // بيتعلّم خلاص حتى لو فشل جزئياً — مايتكررش كل فتحة تطبيق. لإعادة المسح استخدم rescan().
            prefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
        }
    }

    /** إعادة مسح يدوية (زرار في البروفايل) — بترجع المؤشر تاني عشان تفضل idempotent مع scanIfNeeded */
    suspend fun rescan(context: Context, sinceDays: Int = DEFAULT_SINCE_DAYS): Int {
        val imported = scan(context, sinceDays)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
        return imported
    }

    suspend fun scan(context: Context, sinceDays: Int): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return 0
        val cutoffMillis = System.currentTimeMillis() - sinceDays * 24L * 60 * 60 * 1000
        var importedCount = 0
        val dao = ZadDatabase.getDatabase(context.applicationContext).zadDao()

        // بصمة (مبلغ|مصروف؟|تاريخ SMS الأصلي) ضد المعاملات الموجودة فعلاً — عشان إعادة المسح
        // (rescan) ميكررش نفس الرسايل القديمة تاني كصفوف جديدة بـ id عشوائي مختلف
        val existingFingerprints = try {
            dao.getAllTransactionsOnce().mapTo(mutableSetOf()) { "${"%.2f".format(it.amount)}|${it.isExpense}|${it.createdAt}" }
        } catch (e: Exception) { emptySet() }

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffMillis.toString())

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, "${Telephony.Sms.DATE} ASC")?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIdx) ?: continue
                    val text = cursor.getString(bodyIdx) ?: continue
                    val dateMillis = cursor.getLong(dateIdx)

                    if (SaBankParser.isNoise(text)) continue
                    val parsed = SaBankParser.detectAndParse(sender, "", text) ?: continue

                    val createdAt = Instant.ofEpochMilli(dateMillis).toString()
                    val fingerprint = "${"%.2f".format(parsed.amount)}|${parsed.isExpense}|$createdAt"
                    if (fingerprint in existingFingerprints) continue

                    val transaction = ZadTransaction(
                        title = parsed.title,
                        amount = parsed.amount,
                        isExpense = parsed.isExpense,
                        category = MerchantCategoryOverrides.get(context, parsed.merchantName) ?: parsed.category,
                        createdAt = createdAt,
                        sourceType = "sms_backfill"
                    )
                    dao.insertTransaction(transaction)
                    if (!SupabaseRepo.addTransaction(transaction)) {
                        Log.w(TAG, "Supabase sync failed for backfilled tx — queued for retry")
                        SyncOutbox.enqueueTransaction(context.applicationContext, transaction)
                    }
                    importedCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scan() FAILED: ${e.message}")
        }
        Log.d(TAG, "scan() → imported $importedCount historical transaction(s) from last $sinceDays day(s)")
        return importedCount
    }
}
