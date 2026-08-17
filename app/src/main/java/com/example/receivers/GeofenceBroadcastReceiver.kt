package com.example.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.GeofenceCategory
import com.example.data.GroceryGeofenceManager
import com.example.data.local.ZadDatabase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.zad.agent.ZadAlertRouter
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "GeofenceReceiver"
private const val CHANNEL_ID = "zad_location_alerts"

/**
 * بيستقبل ENTER events من الـ geofences اللي GroceryGeofenceManager.refreshGeofences()
 * سجلها. تنبيه واحد بس لكل محل كل ٢٤ ساعة (GroceryGeofenceManager.shouldNotify) — عشان
 * مايبقاش إزعاج لمستخدم بيعدي جنب نفس السوبرماركت كل يوم.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "onReceive() geofence error code=${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeringIds = event.triggeringGeofences?.map { it.requestId } ?: return
        if (triggeringIds.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleEnteredGeofences(context.applicationContext, triggeringIds)
            } catch (e: Exception) {
                Log.e(TAG, "handleEnteredGeofences() FAILED: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleEnteredGeofences(context: Context, geofenceIds: List<String>) {
        // أول geofence لسه في cooldown بيتفتكر بس — لو المستخدم دخل نطاق ٢ محل مع بعض،
        // إشعار واحد كفاية، مش نبعت كذا إشعار في نفس اللحظة.
        val geofenceId = geofenceIds.firstOrNull { GroceryGeofenceManager.shouldNotify(context, it) } ?: return
        val storeName = GroceryGeofenceManager.storeNameForGeofenceId(context, geofenceId) ?: return
        val category = GroceryGeofenceManager.categoryOf(geofenceId) ?: return

        // استعلام لحظي وقت الدخول فعلياً، مش قايمة مخزّنة وقت تسجيل الـ geofence —
        // عشان الإشعار يعكس النواقص الحقيقية دلوقتي بالظبط (طلب المستخدم صراحة).
        val dao = ZadDatabase.getDatabase(context).zadDao()
        val missingItems = when (category) {
            GeofenceCategory.SUPERMARKET, GeofenceCategory.MALL ->
                dao.getAllShoppingItems().first().filter { !it.isPurchased }.map { it.itemName }
            GeofenceCategory.PHARMACY ->
                // نفس عتبة "قرب يخلص" اللي NearbyDealsScreen بيستخدمها (٥ أيام أو أقل)
                dao.getAllPharmacyItemsOnce().filter { it.isLowStock() }.map { it.name }
        }

        GroceryGeofenceManager.markNotified(context, geofenceId)
        showNotification(context, storeName, category, missingItems.take(6))
        notifyBrain(context, storeName, category, missingItems)
    }

    /**
     * مرحلة ٤ — إشعار عقل زاد بالدخول لنطاق المحل أو المول ليرسل نصيحة ميزانية فورية
     * عبر بوت تليجرام وقنوات التنبيهات الموحدة في الخلفية دائماً.
     */
    private suspend fun notifyBrain(context: Context, storeName: String, category: GeofenceCategory, missingItems: List<String>) {
        try {
            val userId = com.example.data.SupabaseRepo.client.auth.currentUserOrNull()?.id ?: return
            val kind = when (category) {
                GeofenceCategory.PHARMACY -> "صيدلية"
                GeofenceCategory.MALL -> "مول / مركز تسوق"
                GeofenceCategory.SUPERMARKET -> "سوبرماركت"
            }
            val userMessage = "المستخدم دلوقتي في $storeName ($kind). ${if (missingItems.isNotEmpty()) "النواقص المعروفة: " + missingItems.joinToString("، ") else "لا توجد نواقص مسجلة"}. وجّه له نصيحة ميزانية وتوفير فورية مناسبة للمكان."
            com.example.data.SupabaseRepo.callEdgeFunction(
                "zad-brain",
                mapOf("user_id" to userId, "trigger" to "geofence_enter", "user_message" to userMessage)
            )
            ZadAlertRouter.sync(context, userId)
            Log.d(TAG, "notifyBrain() → geofence_enter sent for $storeName")
        } catch (e: Exception) {
            Log.e(TAG, "notifyBrain() FAILED: ${e.message}")
        }
    }

    private fun showNotification(context: Context, storeName: String, category: GeofenceCategory, missingItems: List<String>) {
        val title = when (category) {
            GeofenceCategory.MALL -> "🛍️ أنت في $storeName — وفّر في مصاريفك"
            GeofenceCategory.PHARMACY -> context.getString(R.string.location_alert_notification_title, storeName)
            GeofenceCategory.SUPERMARKET -> context.getString(R.string.location_alert_notification_title, storeName)
        }
        val body = if (missingItems.isNotEmpty()) {
            "تذكير ذكي: ركّز على الأساسيات. نواقص البيت: ${missingItems.joinToString("، ")}"
        } else {
            "تذكير ذكي من زاد: انتبه لميزانية الشهر وفكّر قبل الشراء المفاجئ!"
        }

        com.example.data.ZadNotifier.send(
            context,
            title = title,
            message = body,
            speak = true,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        Log.d(TAG, "showNotification() → near $storeName, ${missingItems.size} missing items")
    }
}
