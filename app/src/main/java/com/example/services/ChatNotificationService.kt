package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RealtimeChatRepo
import com.example.data.SupabaseRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatNotificationService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        serviceScope.launch {
            val myMember = SupabaseRepo.getMyFamilyMember()
            if (myMember != null) {
                RealtimeChatRepo.subscribeToChat(myMember.familyId).collectLatest { newMsg ->
                    if (newMsg.senderId != myMember.id) {
                        val isSos = newMsg.messageType == "SOS"
                        val title = if (isSos) "\uD83D\uDEA8 نداء طوارئ" else "\uD83D\uDCAC عائلة زاد"
                        val message = if (isSos) "حالة طوارئ من أحد أفراد العائلة!" else newMsg.message
                        showNotification(title, message, isSos)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun showNotification(title: String, message: String, isSos: Boolean = false) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_family_chat", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = if (isSos) "zad_sos_channel" else "zad_chat_channel"
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (isSos) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chatChannel = NotificationChannel(
                "zad_chat_channel", "محادثة العائلة", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "رسائل المحادثة العائلية" }
            manager.createNotificationChannel(chatChannel)
            val sosChannel = NotificationChannel(
                "zad_sos_channel", "نداءات الطوارئ", NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "رسائل الطوارئ العائلية"
                enableVibration(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(sosChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
