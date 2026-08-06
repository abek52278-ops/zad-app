package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks

private const val TAG = "LocationHelper"

/**
 * مرحلة ٤ (docs/agent/PLAN_2026_08_06_rebuild.md) — بدل `LocationManager.getLastKnownLocation()`
 * (كاش مفيش ضمان لحداثته، وممكن يرجع null تماماً على جهاز لسه ما فتحش خرائط/GPS قبل
 * كده). `FusedLocationProviderClient.getCurrentLocation()` بيطلب إحداثية فعلية لو مفيش
 * كاش حديث كفاية، بدل ما يستسلم فوراً.
 *
 * بلوكينج عمداً (`Tasks.await`) — نفس نمط `GroceryGeofenceManager.refreshGeofences()`
 * الموجود، لازم يتنادى من `Dispatchers.IO` مش الـ main thread.
 */
object LocationHelper {
    fun getCurrentLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            @Suppress("MissingPermission") // اتشيك فوق (hasFine || hasCoarse)
            Tasks.await(client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token))
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation() FAILED: ${e.message}")
            null
        }
    }
}
