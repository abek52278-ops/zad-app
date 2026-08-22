package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * "Hey Zad" — استماع دائم خفيف لكلمة السحر.
 *
 * كيفية العمل (بدون مكتبات مدفوعة):
 * - SpeechRecognizer دائم بحلقة إعادة تشغيل ذاتية: كل نتيجة تُحلَّل محليًا بحثًا عن
 *   كلمات التنبيه ("hey zad / يا زاد / أزاد"). لو وجدت → نبّه الواجهة (WakeBus) وافتح
 *   شاشة الصوت. لو مفيش → كمّل استماع من جديد.
 * - Foreground service بإشعار هادئ عشان أندرويد مياكلش الخدمة.
 * - ما بيبعتش أي حاجة للشبكة بنفسه — التعرف كله on-device/جوجل STT المجاني، والطلب
 *   الفعلي بيحصل بس لما المستخدم ينطق الطلب بعد الـ wake word في شاشة الصوت العادية.
 */
class HeyZadWakeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        startWakeLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "zad_wake_word"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(channelId, "استماع زاد المستمر", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { putExtra("open_voice", true) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.wake_service_title))
            .setContentText(getString(R.string.wake_service_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(intent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(WAKE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(WAKE_NOTIFICATION_ID, notification)
        }
    }

    private fun startWakeLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        running = true
        armRecognizer()
    }

    private fun armRecognizer() {
        if (!running) return
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase()?.trim() ?: return
                    if (WAKE_PHRASES.any { text.contains(it) }) {
                        // فتح شاشة زاد الصوتية — نفس مسار ضغطة الزر بالظبط
                        val launch = Intent(this@HeyZadWakeService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("open_voice", true)
                        }
                        startActivity(launch)
                        com.example.ui.components.ZadChime.play(com.example.ui.components.ZadChime.Tone.Success)
                    }
                }
                override fun onResults(results: android.os.Bundle?) { rearm() }
                override fun onError(error: Int) { rearm() }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            try { startListening(intent) } catch (_: Exception) { rearm() }
        }
    }

    /** إعادة تسليح بعد كل نتيجة/خطأ — مع مهلة قصيرة عشان ما نلفش الحلقة بسرعة جنونية. */
    private fun rearm() {
        if (!running) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ armRecognizer() }, 400L)
    }

    override fun onDestroy() {
        running = false
        serviceScope.cancel()
        try { recognizer?.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val WAKE_NOTIFICATION_ID = 4711
        const val ACTION_STOP = "com.example.voice.STOP_WAKE"

        /** كل الصيغ المقبولة لكلمة التنبيه (مصري/عربي/إنجليزي). */
        val WAKE_PHRASES = listOf(
            "hey zad", "hey زاد", "hi zad", "يا زاد", "ازيك يا زاد",
            "أزاد", "ازاد", "هي زاد", "زاد؟"
        )

        fun start(context: Context) {
            val intent = Intent(context, HeyZadWakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HeyZadWakeService::class.java).apply { action = ACTION_STOP })
        }
    }
}
