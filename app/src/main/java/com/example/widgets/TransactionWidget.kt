package com.example.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.BudgetMath
import com.example.data.CycleMath
import com.example.data.MarketPrefs
import com.example.data.ZadTransaction
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.zad.REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TransactionWidget::class.java)
            )
            val intent = Intent(context, TransactionWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.transaction_widget)

            // Set click to open app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_logo, openPending)

            // Refresh button
            val refreshIntent = Intent(context, TransactionWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            // Load transactions from Room
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = ZadDatabase.getDatabase(context)
                    val dao = db.zadDao()
                    val transactions = dao.getAllTransactionsOnce()

                    // نفس حساب recalculateRemainingBalance بالظبط (ZadViewModel) — كان بيحسب
                    // صافي كل المعاملات من أول يوم في التطبيق تحت اسم "الرصيد"، بينما الهوم
                    // والبادجت بيوروا "متاح" بحدود دورة الراتب. رقمين تحت نفس الاسم لازم يبقوا
                    // رقم واحد. الويدجت مالوش شبكة، فبيقرا cached_budget/cycle_* من نفس
                    // SharedPreferences اللي loadBudget()/loadCycleSettings() بتكتب فيها.
                    val prefs = context.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
                    val budget = prefs.getFloat("cached_budget", 0f).toDouble()
                    val cycleStartDay = prefs.getInt("cycle_start_day", -1).let { if (it == -1) null else it }
                    val cycleAnchor = prefs.getString("cycle_anchor", "day_of_month") ?: "day_of_month"
                    val market = MarketPrefs.getMarket(context)
                    val asOf = java.time.LocalDate.now()
                    val cycleStart = CycleMath.cycleStart(asOf, cycleStartDay, cycleAnchor, market)
                    val cycleEnd = CycleMath.cycleEnd(asOf, cycleStartDay, cycleAnchor, market)
                    val remaining = BudgetMath.remainingInCycle(budget, transactions, cycleStart, cycleEnd)

                    views.setTextViewText(
                        R.id.widget_balance,
                        remaining?.let { com.example.data.CurrencyFormatter.format(context, it) } ?: "غير محدد"
                    )

                    // Add transaction rows
                    val recentTx = transactions.sortedByDescending { it.createdAt ?: "" }.take(3)
                    val txContainer = R.id.widget_tx_container
                    views.removeAllViews(txContainer)

                    if (recentTx.isEmpty()) {
                        val emptyView = RemoteViews(context.packageName, R.layout.widget_tx_item)
                        emptyView.setTextViewText(R.id.widget_tx_title, "لا توجد معاملات")
                        emptyView.setTextViewText(R.id.widget_tx_amount, "")
                        emptyView.setViewVisibility(R.id.widget_tx_merchant, View.GONE)
                        views.addView(txContainer, emptyView)
                    } else {
                        recentTx.forEach { tx ->
                            val itemView = RemoteViews(context.packageName, R.layout.widget_tx_item)
                            itemView.setTextViewText(R.id.widget_tx_title, tx.title)
                            itemView.setTextViewText(R.id.widget_tx_amount,
                                "${if (tx.isExpense) "-" else "+"}${com.example.data.CurrencyFormatter.format(context, tx.amount)}")
                            itemView.setTextColor(R.id.widget_tx_amount,
                                if (tx.isExpense) Color.parseColor("#E85D5D") else Color.parseColor("#53B175"))

                            if (tx.merchantName != null) {
                                itemView.setTextViewText(R.id.widget_tx_merchant, tx.merchantName)
                                itemView.setViewVisibility(R.id.widget_tx_merchant, View.VISIBLE)
                            } else {
                                itemView.setViewVisibility(R.id.widget_tx_merchant, View.GONE)
                            }

                            views.addView(txContainer, itemView)
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    views.setTextViewText(R.id.widget_balance, "---")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
