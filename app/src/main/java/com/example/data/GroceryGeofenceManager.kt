package com.example.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "GroceryGeofenceManager"

/**
 * تنبيهات قرب السوبرماركت — opt-in منفصل تماماً عن NearbyDealsScreen (بحث يدوي مرة واحدة).
 * ده geofencing حقيقي: بيسجل أقرب ~20 سوبرماركت كـ geofences، ولما المستخدم يدخل نطاق
 * واحد منهم (حتى لو التطبيق مقفول)، GeofenceBroadcastReceiver بيبعت إشعار بالنواقص.
 *
 * مفيش تصنيف "المحل ده غالي/رخيص" هنا — مفيش مصدر بيانات أسعار محلات في المشروع
 * (نفس الصدق اللي NearbyDealsScreen موثقه). النسخة دي بس: نواقصك وأنت قريب من متجر.
 */
object GroceryGeofenceManager {
    private const val PREFS = "zad_location_alerts_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STORE_NAMES = "geofence_store_names" // JSON: { geofenceId: storeName }
    private const val KEY_LAST_NOTIFIED_PREFIX = "last_notified_"
    private const val MAX_GEOFENCES = 20
    private const val GEOFENCE_RADIUS_METERS = 200f
    private const val SEARCH_RADIUS_METERS = 3000
    const val NOTIFY_COOLDOWN_MS = 24 * 60 * 60 * 1000L // مرة كل ٢٤ ساعة لنفس المحل، عشان مايبقاش إزعاج

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) clearGeofences(context)
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true // قبل Android 10، ACCESS_FINE_LOCATION كان كافي للخلفية كمان
        return fine && background
    }

    fun storeNameForGeofenceId(context: Context, geofenceId: String): String? {
        val json = prefs(context).getString(KEY_STORE_NAMES, null) ?: return null
        return try { JSONObject(json).optString(geofenceId).takeIf { it.isNotBlank() } } catch (e: Exception) { null }
    }

    /** كل ٢٤ ساعة بالكتير تنبيه واحد لنفس المحل، حتى لو المستخدم بيعدي جنبه ٥ مرات في اليوم */
    fun shouldNotify(context: Context, geofenceId: String): Boolean {
        val last = prefs(context).getLong(KEY_LAST_NOTIFIED_PREFIX + geofenceId, 0L)
        return System.currentTimeMillis() - last > NOTIFY_COOLDOWN_MS
    }

    fun markNotified(context: Context, geofenceId: String) {
        prefs(context).edit().putLong(KEY_LAST_NOTIFIED_PREFIX + geofenceId, System.currentTimeMillis()).apply()
    }

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.example.receivers.GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun clearGeofences(context: Context) {
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)
        try {
            client.removeGeofences(geofencePendingIntent(context))
        } catch (e: Exception) {
            Log.e(TAG, "clearGeofences() FAILED: ${e.message}")
        }
        prefs(context).edit().remove(KEY_STORE_NAMES).apply()
    }

    /**
     * بتتنادى من GeofenceRefreshWorker (WorkManager دوري) — بترجع Boolean للـ worker يعرف
     * ينجح/يفشل، مش بترمي. لو مش enabled أو الإذن ناقص، بترجع true (مفيش حاجة تتعمل، مش فشل).
     */
    suspend fun refreshGeofences(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled(context)) return@withContext true
        if (!hasBackgroundLocationPermission(context)) {
            Log.w(TAG, "refreshGeofences() → missing background location permission, skipping")
            return@withContext true
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val location = try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) { null }

        if (location == null) {
            Log.w(TAG, "refreshGeofences() → no last known location")
            return@withContext false
        }

        val stores = OverpassRepo.findNearbySupermarkets(location.latitude, location.longitude, SEARCH_RADIUS_METERS)
            .take(MAX_GEOFENCES)
        if (stores.isEmpty()) {
            Log.d(TAG, "refreshGeofences() → no nearby supermarkets found")
            return@withContext true
        }

        val geofences = mutableListOf<Geofence>()
        val idToName = JSONObject()
        stores.forEachIndexed { index, store ->
            val id = "grocery_geofence_$index"
            idToName.put(id, store.name)
            geofences.add(
                Geofence.Builder()
                    .setRequestId(id)
                    .setCircularRegion(store.lat, store.lon, GEOFENCE_RADIUS_METERS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()
            )
        }
        prefs(context).edit().putString(KEY_STORE_NAMES, idToName.toString()).apply()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)
        return@withContext try {
            // Tasks.await() آمن هنا لأننا في Dispatchers.IO مش الـ main thread
            com.google.android.gms.tasks.Tasks.await(client.removeGeofences(geofencePendingIntent(context)))
            @Suppress("MissingPermission") // hasBackgroundLocationPermission() اتشيك فوق
            com.google.android.gms.tasks.Tasks.await(client.addGeofences(request, geofencePendingIntent(context)))
            Log.d(TAG, "refreshGeofences() → registered ${geofences.size} geofences")
            true
        } catch (e: Exception) {
            Log.e(TAG, "refreshGeofences() addGeofences FAILED: ${e.message}")
            false
        }
    }
}
