package com.example.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

/** بادئة الـ geofence id بتحدد GeofenceBroadcastReceiver يستعلم عن إيه لحظة الدخول: نواقص المؤن ولا الدواء */
enum class GeofenceCategory(val idPrefix: String) {
    SUPERMARKET("grocery_geofence_"),
    PHARMACY("pharmacy_geofence_"),
    MALL("mall_geofence_")
}

/**
 * تنبيهات قرب السوبرماركت/الصيدلية/المول — opt-in منفصل تماماً عن NearbyDealsScreen.
 * ده geofencing حقيقي: بيسجل أقرب متاجر ومولات وصيدليات، ولما المستخدم يدخل نطاق واحد
 * منهم، GeofenceBroadcastReceiver يذكره بنواقص البيت ويحثه على التوفير.
 */
object GroceryGeofenceManager {
    private const val PREFS = "zad_location_alerts_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STORE_NAMES = "geofence_store_names" // JSON: { geofenceId: storeName }
    private const val KEY_LAST_NOTIFIED_PREFIX = "last_notified_"
    private const val MAX_GEOFENCES_PER_CATEGORY = 15
    private const val GEOFENCE_RADIUS_METERS = 120f
    private const val SEARCH_RADIUS_METERS = 3000
    const val NOTIFY_COOLDOWN_MS = 24 * 60 * 60 * 1000L // مرة كل ٢٤ ساعة لنفس المكان

    /** كل فئة بتاخد مصدرها: LocationIQ أولاً، Overpass fallback لو فاضي */
    fun categoryOf(geofenceId: String): GeofenceCategory? =
        GeofenceCategory.entries.firstOrNull { geofenceId.startsWith(it.idPrefix) }

    private suspend fun findStores(category: GeofenceCategory, lat: Double, lon: Double): List<NearbyStore> {
        val fromLocationIq = when (category) {
            GeofenceCategory.SUPERMARKET, GeofenceCategory.MALL -> LocationIqRepo.findNearbySupermarkets(lat, lon, SEARCH_RADIUS_METERS)
            GeofenceCategory.PHARMACY -> LocationIqRepo.findNearbyPharmacies(lat, lon, SEARCH_RADIUS_METERS)
        }
        if (fromLocationIq.isNotEmpty()) return fromLocationIq
        return when (category) {
            GeofenceCategory.SUPERMARKET, GeofenceCategory.MALL -> OverpassRepo.findNearbySupermarkets(lat, lon, SEARCH_RADIUS_METERS)
            GeofenceCategory.PHARMACY -> OverpassRepo.findNearbyPharmacies(lat, lon, SEARCH_RADIUS_METERS)
        }
    }

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

        val location = LocationHelper.getCurrentLocation(context)

        if (location == null) {
            Log.w(TAG, "refreshGeofences() → no last known location")
            return@withContext false
        }

        // نفس التثبيتة بتتخزّن للعقل. المسار ده بيشتغل والتطبيق مقفول، فهو أكتر مصدر
        // بيخلّي `zad_users.last_*` حديثة — أهم من مسار الخروجة اللي بيتنادى وقت الفتح بس.
        SupabaseRepo.updateLastKnownLocation(location.latitude, location.longitude)

        val geofences = mutableListOf<Geofence>()
        val idToName = JSONObject()
        for (category in GeofenceCategory.entries) {
            val stores = findStores(category, location.latitude, location.longitude).take(MAX_GEOFENCES_PER_CATEGORY)
            stores.forEachIndexed { index, store ->
                val id = "${category.idPrefix}$index"
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
        }
        if (geofences.isEmpty()) {
            Log.d(TAG, "refreshGeofences() → no nearby supermarkets/pharmacies found")
            return@withContext true
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
