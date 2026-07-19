package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

object BudgetTracker {

    private const val TAG = "BudgetTracker"
    private const val PREFS_NAME = "zad_prefs"
    private const val KEY_REMAINING = "remaining_balance"
    private const val KEY_BUDGET = "cached_budget"
    private const val CHANNEL_ID = "zad_budget_alerts"

    fun getRemaining(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val budget = prefs.getFloat(KEY_BUDGET, 3500.0f).toDouble()
        return prefs.getFloat(KEY_REMAINING, budget.toFloat()).toDouble()
    }

    fun deductExpense(context: Context, amount: Double, title: String, category: String = "") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val budget = prefs.getFloat(KEY_BUDGET, 3500.0f).toDouble()
        
        val savedMonth = prefs.getInt("budget_month", -1)
        val currentMonth = java.time.LocalDate.now().monthValue
        
        var current = prefs.getFloat(KEY_REMAINING, budget.toFloat()).toDouble()
        
        if (savedMonth != -1 && savedMonth != currentMonth) {
            current = budget
            prefs.edit().putInt("budget_month", currentMonth).apply()
        }
        
        val newRemaining = current - amount
        prefs.edit().putFloat(KEY_REMAINING, newRemaining.toFloat()).apply()
        Log.d(TAG, "deductExpense: $amount | $current → $newRemaining")

        // Alert if budget is low
        val spentPct = ((budget - newRemaining) / budget * 100).toInt()
        if (newRemaining <= 0) {
            sendAlert(context, "⛔ الميزانية منتهية!", "تم استنفاذ الميزانية بالكامل. راجع مصاريفك.")
        } else if (spentPct >= 90) {
            sendAlert(context, "⚠️ الميزانية أوشكت على الانتهاء", "استخدمت $spentPct% من ميزانيتك. المتبقي: ${String.format("%,.0f", newRemaining)} ر.س")
        } else if (spentPct >= 75) {
            sendAlert(context, "💡 تذكير بالميزانية", "صرفت $spentPct% من ميزانيتك. المتبقي: ${String.format("%,.0f", newRemaining)} ر.س")
        }
    }

    fun addIncome(context: Context, amount: Double, title: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val budget = prefs.getFloat(KEY_BUDGET, 3500.0f).toDouble()
        
        val savedMonth = prefs.getInt("budget_month", -1)
        val currentMonth = java.time.LocalDate.now().monthValue
        
        var current = prefs.getFloat(KEY_REMAINING, budget.toFloat()).toDouble()
        
        if (savedMonth != -1 && savedMonth != currentMonth) {
            current = budget
            prefs.edit().putInt("budget_month", currentMonth).apply()
        }
        
        val newRemaining = current + amount
        prefs.edit().putFloat(KEY_REMAINING, newRemaining.toFloat()).apply()
        Log.d(TAG, "addIncome: $amount | $current → $newRemaining")
        sendAlert(context, "💰 تمت إضافة إيداع", "$title: +${String.format("%,.0f", amount)} ر.س — الرصيد المتبقي: ${String.format("%,.0f", newRemaining)} ر.س")
    }

    private fun sendAlert(context: Context, title: String, message: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "تنبيهات الميزانية", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "sendAlert failed: ${e.message}")
        }
    }
}
