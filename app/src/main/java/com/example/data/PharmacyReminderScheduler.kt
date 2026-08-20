package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.receivers.PharmacyReminderReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * جدولة تنبيهات مواعيد الجرعات بدقة عبر AlarmManager.setExactAndAllowWhileIdle —
 * WorkManager مش دقيق كفاية لمواعيد محددة بالساعة (أقل فاصل 15 دقيقة ومفيش ضمان
 * وقت ثابت)، فالمواعيد الطبية لازم Exact Alarm الحقيقي بتاع أندرويد.
 *
 * كل جرعة (دواء + وقت) بيبقى لها alarm مستقل، بيتجدد يومياً من نفس الـ receiver
 * وقت ما يطلق (شوف PharmacyReminderReceiver). القائمة الحالية من المفاتيح المجدولة
 * محفوظة في SharedPreferences عشان نقدر نلغي القديم لو دواء اتمسح أو مواعيده اتغيرت.
 */
object PharmacyReminderScheduler {
    private const val TAG = "PharmacyReminderScheduler"
    private const val PREFS_NAME = "zad_prefs"
    private const val KEY_SCHEDULED = "pharmacy_alarm_keys"
    const val ACTION_TRIGGER = "com.example.PHARMACY_REMINDER_TRIGGER"

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * بيعيد مزامنة كل المنبهات مع القائمة الحالية للأدوية — بيلغي القديم اللي مبقاش موجود.
     * Task 17.2.3 — returns item ids that had at least one dose_time entry the scheduler
     * couldn't parse, so the caller can flag it (has_invalid_dose_time) instead of the old
     * behavior of silently dropping that one dose time with no trace anywhere.
     */
    fun rescheduleAll(context: Context, items: List<ZadPharmacyItem>): List<String> {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(context)) {
            Log.w(TAG, "rescheduleAll() → SCHEDULE_EXACT_ALARM not granted, skipping")
            return emptyList()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousKeys = prefs.getStringSet(KEY_SCHEDULED, emptySet()) ?: emptySet()
        val newKeys = mutableSetOf<String>()
        val invalidItemIds = mutableListOf<String>()

        items.forEach { item ->
            item.doseTimesList().forEach { timeStr ->
                val key = keyFor(item.id, timeStr)
                newKeys.add(key)
                val ok = scheduleOne(context, alarmManager, item.id, item.name, timeStr)
                if (!ok) invalidItemIds.add(item.id)
            }
        }

        (previousKeys - newKeys).forEach { staleKey ->
            cancelByKey(context, alarmManager, staleKey)
        }

        prefs.edit().putStringSet(KEY_SCHEDULED, newKeys).apply()
        Log.d(TAG, "rescheduleAll() → scheduled=${newKeys.size}, cancelled=${(previousKeys - newKeys).size}, invalid=${invalidItemIds.size}")
        return invalidItemIds
    }

    /** بيعيد جدولة جرعة واحدة لبكرة نفس الميعاد — بيتنادى من الـ receiver بعد كل تنبيه يطلق */
    fun rescheduleForTomorrow(context: Context, itemId: String, itemName: String, timeStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(context)) return
        scheduleOne(context, alarmManager, itemId, itemName, timeStr, forceNextDay = true)
    }

    fun scheduleSnooze(context: Context, itemId: String, itemName: String, timeStr: String, minutesFromNow: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExact(context)) return
        val triggerAt = System.currentTimeMillis() + minutesFromNow * 60_000L
        val pendingIntent = buildPendingIntent(context, itemId, itemName, timeStr)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            Log.e(TAG, "scheduleSnooze() FAILED: ${e.message}")
        }
    }

    /** Cancel every medicine alarm before an account leaves this device. */
    fun cancelAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().toSet()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        keys.forEach { cancelByKey(context, alarmManager, it) }
        prefs.edit().remove(KEY_SCHEDULED).apply()
        Log.d(TAG, "cancelAll() → cancelled=${keys.size}")
    }

    /** @return false if timeStr couldn't be parsed (nothing was scheduled for it) */
    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        itemId: String,
        itemName: String,
        timeStr: String,
        forceNextDay: Boolean = false
    ): Boolean {
        val time = try {
            LocalTime.parse(if (timeStr.length == 5) timeStr else timeStr.padStart(5, '0'))
        } catch (e: DateTimeParseException) {
            Log.w(TAG, "scheduleOne() → invalid time format: $timeStr")
            return false
        }
        val now = LocalDateTime.now()
        var trigger = LocalDateTime.of(now.toLocalDate(), time)
        if (forceNextDay || !trigger.isAfter(now)) trigger = trigger.plusDays(1)
        val triggerAtMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pendingIntent = buildPendingIntent(context, itemId, itemName, timeStr)
        return try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "scheduleOne() FAILED (permission revoked mid-flight?): ${e.message}")
            true // parsed fine, just couldn't schedule — not a "malformed dose_time" case
        }
    }

    private fun cancelByKey(context: Context, alarmManager: AlarmManager, key: String) {
        val parts = key.split("::")
        if (parts.size != 2) return
        val pendingIntent = buildPendingIntent(context, parts[0], "", parts[1])
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(context: Context, itemId: String, itemName: String, timeStr: String): PendingIntent {
        val intent = Intent(context, PharmacyReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra("item_id", itemId)
            putExtra("item_name", itemName)
            putExtra("dose_time", timeStr)
        }
        return PendingIntent.getBroadcast(
            context, requestCodeFor(itemId, timeStr), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * The shared dedupe key for a dose slot (Task 17.2.2): today's date + the HH:mm from
     * dose_times, in the device's local zone, converted to an instant. Both the fired-alarm
     * notification path and the pharmacy-screen per-time-slot button compute the SAME value
     * for "today's 08:00 dose", so the zad_pharmacy_doses unique index actually catches a
     * double-tap instead of two calls each minting their own near-but-not-identical timestamp.
     */
    fun canonicalScheduledAt(timeStr: String, date: LocalDate = LocalDate.now()): String? {
        val time = try {
            LocalTime.parse(if (timeStr.length == 5) timeStr else timeStr.padStart(5, '0'))
        } catch (e: DateTimeParseException) {
            return null
        }
        return LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toString()
    }

    private fun keyFor(itemId: String, timeStr: String) = "$itemId::$timeStr"
    private fun requestCodeFor(itemId: String, timeStr: String) = keyFor(itemId, timeStr).hashCode()
}
