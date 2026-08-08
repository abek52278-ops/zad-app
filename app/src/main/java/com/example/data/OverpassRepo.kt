package com.example.data

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "OverpassRepo"

/**
 * سوبرماركتس/بقالات قريبة عبر Overpass API (OpenStreetMap) — مجاني تماماً
 * وبدون API key، بديل Google Places المدفوع. مصدر بيانات مجتمعي (OSM)
 * فمش هيكون كامل زي خرائط تجارية، لكن كافي لتحديد أقرب متجر فعلياً.
 */
object OverpassRepo {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun findNearbySupermarkets(lat: Double, lon: Double, radiusMeters: Int = 1000): List<NearbyStore> =
        query(lat, lon, radiusMeters, """node["shop"~"supermarket|convenience|grocery"](around:$radiusMeters,$lat,$lon);""", "findNearbySupermarkets")

    suspend fun findNearbyPharmacies(lat: Double, lon: Double, radiusMeters: Int = 1500): List<NearbyStore> =
        query(lat, lon, radiusMeters, """node["amenity"="pharmacy"](around:$radiusMeters,$lat,$lon);""", "findNearbyPharmacies")

    suspend fun findNearbyOutingSpots(lat: Double, lon: Double, radiusMeters: Int = 2000): List<NearbyStore> =
        query(lat, lon, radiusMeters, """node["amenity"~"restaurant|cafe|fast_food"](around:$radiusMeters,$lat,$lon);""", "findNearbyOutingSpots")

    private suspend fun query(lat: Double, lon: Double, radiusMeters: Int, filterClause: String, callerTag: String): List<NearbyStore> =
        withContext(Dispatchers.IO) {
            try {
                val overpassQuery = """
                    [out:json][timeout:15];
                    (
                      $filterClause
                    );
                    out center 30;
                """.trimIndent()

                val body = FormBody.Builder().add("data", overpassQuery).build()
                val request = Request.Builder()
                    .url("https://overpass-api.de/api/interpreter")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "$callerTag() FAILED: HTTP ${response.code}")
                        return@withContext emptyList()
                    }
                    val json = JSONObject(response.body?.string() ?: return@withContext emptyList())
                    val elements = json.optJSONArray("elements") ?: return@withContext emptyList()

                    val results = mutableListOf<NearbyStore>()
                    for (i in 0 until elements.length()) {
                        val el = elements.getJSONObject(i)
                        val tags = el.optJSONObject("tags")
                        val name = tags?.optString("name")?.takeIf { it.isNotBlank() } ?: continue
                        val storeLat = el.optDouble("lat", Double.NaN)
                        val storeLon = el.optDouble("lon", Double.NaN)
                        if (storeLat.isNaN() || storeLon.isNaN()) continue

                        val distanceResult = FloatArray(1)
                        Location.distanceBetween(lat, lon, storeLat, storeLon, distanceResult)
                        results.add(NearbyStore(name, storeLat, storeLon, distanceResult[0].toInt()))
                    }
                    Log.d(TAG, "$callerTag() → found ${results.size} results")
                    results.sortedBy { it.distanceMeters }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$callerTag() FAILED: ${e.message}")
                emptyList()
            }
        }
}
