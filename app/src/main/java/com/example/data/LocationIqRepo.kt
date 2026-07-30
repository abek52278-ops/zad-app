package com.example.data

import android.util.Log
import io.github.jan.supabase.auth.auth

private const val TAG = "LocationIqRepo"

/**
 * سوبرماركتس/صيدليات قريبة عن طريق LocationIQ — نفس شكل NearbyStore اللي OverpassRepo
 * بيرجعه، بس عن طريق nearby_pois على zad-core-intelligence (سيرفر-سايد) مش نداء مباشر
 * من الكلاينت. المفتاح (LOCATIONIQ_API_KEY) سر سيرفر فقط — أبداً في الـ APK، عشان محدش
 * يقدر يفك التطبيق ويستهلك حصة الحساب المجانية.
 *
 * GroceryGeofenceManager بيجرب هنا الأول (تغطية بيانات تجارية أدق من OSM المجتمعي)،
 * ولو فاضي (مفيش مفتاح متظبط، أو فشل الطلب) بيرجع لـ OverpassRepo تلقائي.
 */
object LocationIqRepo {

    suspend fun findNearbySupermarkets(lat: Double, lon: Double, radiusMeters: Int = 3000): List<NearbyStore> =
        query(lat, lon, "supermarket", radiusMeters)

    suspend fun findNearbyPharmacies(lat: Double, lon: Double, radiusMeters: Int = 3000): List<NearbyStore> =
        query(lat, lon, "pharmacy", radiusMeters)

    private suspend fun query(lat: Double, lon: Double, tag: String, radiusMeters: Int): List<NearbyStore> {
        return try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id.orEmpty()
            val response = SupabaseRepo.callEdgeFunction(
                "zad-core-intelligence",
                mapOf(
                    "action" to "nearby_pois",
                    "user_id" to userId,
                    "payload" to mapOf("lat" to lat, "lon" to lon, "tag" to tag, "radius_meters" to radiusMeters)
                )
            )
            val storesRaw = response["stores"] as? List<*> ?: return emptyList()
            storesRaw.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val name = map["name"] as? String ?: return@mapNotNull null
                val storeLat = (map["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val storeLon = (map["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
                val distance = (map["distance_meters"] as? Number)?.toInt() ?: 0
                NearbyStore(name, storeLat, storeLon, distance)
            }.sortedBy { it.distanceMeters }
        } catch (e: Exception) {
            Log.e(TAG, "query(tag=$tag) FAILED: ${e.message}")
            emptyList()
        }
    }
}
