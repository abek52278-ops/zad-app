package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.GroceryGeofenceManager

/**
 * بيعيد تسجيل geofences أقرب سوبرماركتات كل ١٢ ساعة — عشان لو المستخدم سافر/انتقل، النطاقات
 * المسجلة تتحدث بدل ما تفضل ثابتة على أول مكان اتفعّلت فيه الميزة. مفيش حاجة تتعمل لو الميزة
 * مقفولة أو الإذن ناقص (GroceryGeofenceManager.refreshGeofences بترجع true بلا أي أكشن).
 */
class GeofenceRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ok = GroceryGeofenceManager.refreshGeofences(applicationContext)
            if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("GeofenceRefreshWorker", "doWork() FAILED: ${e.message}")
            Result.retry()
        }
    }
}
