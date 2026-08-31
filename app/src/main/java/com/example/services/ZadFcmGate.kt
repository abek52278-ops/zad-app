package com.example.services

import android.util.Log
import com.example.data.SupabaseRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * جسر آمن بين التطبيق وFCM — كلاس FirebaseMessaging بيتحمّل بس لو
 * google-services.json موجود (يعني Firebase فعلاً متفعّل في البيلد).
 * من غير الملف، كل النداءات هنا بتتفشل بهدوء والتطبيق شغال عادي (نفس سلوك
 * البديل المحلي عبر ZadAlertRouter).
 */
object ZadFcmGate {

    private const val TAG = "ZadFcm"

    /** Firebase متفعّل في هذا البيلد؟ (google-services.json موجود وقت الكومبايل) */
    val firebaseAvailable: Boolean by lazy {
        try {
            Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            true
        } catch (_: ClassNotFoundException) {
            Log.i(TAG, "Firebase not in build — FCM disabled, local notifications only")
            false
        }
    }

    /** رفع/تحديث توكن الجهاز في zad_fcm_tokens. آمنة للنداء المتكرر (upsert على token). */
    suspend fun saveToken(token: String) {
        if (!firebaseAvailable || token.isBlank()) return
        try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id ?: run {
                Log.w(TAG, "saveToken skipped: no session yet")
                return
            }
            SupabaseRepo.client.postgrest["zad_fcm_tokens"].upsert(
                buildJsonObject {
                    put("user_id", userId)
                    put("token", token)
                    put("platform", "android")
                }
            ) {
                // upsert على توكن الجهاز نفسه — الجهاز ميتكررش
                @OptIn(io.github.jan.supabase.annotations.SupabaseExperimental::class)
                run {
                    params["on_conflict"] = listOf("token")
                }
            }
            Log.d(TAG, "FCM token saved")
        } catch (e: Exception) {
            Log.e(TAG, "saveToken FAILED: ${e.message}")
        }
    }

    /** بعد تسجيل الدخول — نجيب آخر توكن من Firebase ورفعه. no-op لو Firebase مش متفعّل. */
    suspend fun syncTokenAfterLogin() {
        if (!firebaseAvailable) return
        try {
            val task = com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            val token = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                task.addOnSuccessListener { if (cont.isActive) cont.resume(it, null) }
                task.addOnFailureListener { if (cont.isActive) cont.resume(null, null) }
                task.addOnCanceledListener { if (cont.isActive) cont.resume(null, null) }
            }
            token?.let { saveToken(it) }
        } catch (e: Exception) {
            Log.w(TAG, "syncTokenAfterLogin failed: ${e.message}")
        }
    }
}
