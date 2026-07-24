package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.PharmacyReminderScheduler
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** منبهات AlarmManager بتتمسح عند إعادة تشغيل الجهاز — لازم نعيد جدولتها من جديد */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = ZadDatabase.getDatabase(context.applicationContext).zadDao().getAllPharmacyItemsOnce()
                PharmacyReminderScheduler.rescheduleAll(context.applicationContext, items)
                Log.d("BootReceiver", "Rescheduled ${items.count { it.doseTimesList().isNotEmpty() }} pharmacy reminder(s) after boot")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Reschedule after boot FAILED: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
