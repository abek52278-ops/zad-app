package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM — الوعي اللحظي للأيدجنت (استقبال الإشعارات الاستباقية).
 * بيُحمّل من أندرويد بس لما Firebase يكون متفعّل في البيلد (google-services.json موجود)
 * — لوجيك حفظ التوكن في ZadFcmGate. data-only إشعار بيبني إشعار محلي هنا؛
 * notification-only أندرويد بيعرضه بنفسه (FCM v1 payload).
 */
class ZadFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d("ZadFcm", "FCM token refreshed")
        scope.launch { ZadFcmGate.saveToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (message.notification == null && !title.isNullOrBlank()) {
            showAgentNotification(title, body ?: "")
        }
    }

    /** إشعار محلي لإشعارات الأيدجنت data-only — يفتح الشاشة الرئيسية. */
    private fun showAgentNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    AGENT_CHANNEL, "تنبيهات زاد", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "تنبيهات استباقية من مساعد زاد" }
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AGENT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        try {
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS مش متمنحة لسه — الإشعار يتساقط بأدب والرد النصي يفضل شغال
            android.util.Log.w("ZadFcm", "notify denied: ${e.message}")
        }
    }

    companion object {
        const val AGENT_CHANNEL = "zad_agent_channel"
    }
}
