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
            GeofenceCategory.SUPERMARKET ->
                dao.getAllShoppingItems().first().filter { !it.isPurchased }.map { it.itemName }
            GeofenceCategory.PHARMACY ->
                // نفس عتبة "قرب يخلص" اللي NearbyDealsScreen بيستخدمها (٥ أيام أو أقل)
                dao.getAllPharmacyItemsOnce().filter { val d = it.daysOfSupplyLeft(); d != null && d <= 5 }.map { it.name }
        }
        if (missingItems.isEmpty()) {
            // مفيش نواقص فعلياً — إشعار بلا فايدة أسوأ من مفيش إشعار (AUDIT.md)
            Log.d(TAG, "handleEnteredGeofences() → near $storeName but no missing items, skipping notification")
            GroceryGeofenceManager.markNotified(context, geofenceId)
            return
        }

        GroceryGeofenceManager.markNotified(context, geofenceId)
        showNotification(context, storeName, missingItems.take(6))
    }

    private fun showNotification(context: Context, storeName: String, missingItems: List<String>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.location_alerts_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.location_alert_notification_title, storeName)
        val body = context.getString(R.string.location_alert_notification_body, missingItems.joinToString("، "))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(storeName.hashCode(), notification)
        Log.d(TAG, "showNotification() → near $storeName, ${missingItems.size} missing items")
    }
}
