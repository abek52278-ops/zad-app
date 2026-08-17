package com.zad.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.SupabaseRepo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * زاد بيتعلم من كلامه — التنبيه بتاع العقل (zad_insights في Supabase) بيوصل هنا، يتوزع
 * حسب surface (home_card/bell/voice)، وحالته (seen/acted/dismissed) بترجع لنفس الجدول
 * عشان العقل يقرأها في الـ snapshot الجاي (dismissed = نهائي، ميرفعهاش تاني — Task 10.4).
 *
 * ملف قائم بذاته عمداً: Room DB منفصلة صغيرة (مش لمسنا ZadDatabase.kt الأساسي)، بيستخدم
 * SupabaseRepo.client الموجود بدل ما يفتح اتصال Supabase تاني.
 */

// ═══════════════════════════════════════════════════════════
// Remote model — نفس أعمدة zad_insights (migrations/0001_zad_brain.sql)
// ═══════════════════════════════════════════════════════════

@Serializable
data class ZadInsightRemote(
    val id: String,
    @SerialName("user_id") val userId: String,
    val kind: String,
    val surface: String,
    val priority: String,
    val title: String,
    val body: String,
    @SerialName("dedupe_key") val dedupeKey: String,
    val status: String,
    @SerialName("action_type") val actionType: String? = null,
    @SerialName("about_item") val aboutItem: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// ═══════════════════════════════════════════════════════════
// Local mirror (Task 10.3: "Mirror zad_insights into Room so the app works offline")
// ═══════════════════════════════════════════════════════════

@Entity(tableName = "zad_insight_mirror")
data class ZadInsightMirror(
    @PrimaryKey val id: String,
    val kind: String,
    val surface: String,
    val priority: String,
    val title: String,
    val body: String,
    val dedupeKey: String,
    val status: String,
    val actionType: String?,
    val aboutItem: String?,
    val createdAt: String?
)

@Dao
interface ZadInsightMirrorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(insights: List<ZadInsightMirror>)

    @Query("SELECT * FROM zad_insight_mirror WHERE surface = :surface AND status != 'dismissed' ORDER BY CASE priority WHEN 'critical' THEN 0 ELSE 1 END, createdAt DESC")
    fun observeBySurface(surface: String): Flow<List<ZadInsightMirror>>

    @Query("SELECT * FROM zad_insight_mirror WHERE status != 'dismissed' ORDER BY CASE priority WHEN 'critical' THEN 0 ELSE 1 END, createdAt DESC")
    fun observeAll(): Flow<List<ZadInsightMirror>>

    @Query("UPDATE zad_insight_mirror SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM zad_insight_mirror WHERE id NOT IN (:keepIds)")
    suspend fun pruneNotIn(keepIds: List<String>)
}

@Database(entities = [ZadInsightMirror::class], version = 1, exportSchema = false)
abstract class ZadInsightDatabase : RoomDatabase() {
    abstract fun insightDao(): ZadInsightMirrorDao

    companion object {
        @Volatile private var INSTANCE: ZadInsightDatabase? = null

        fun getDatabase(context: Context): ZadInsightDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, ZadInsightDatabase::class.java, "zad_insight_mirror.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// AlertStateStore (Task 10.5): كام تنبيه صوتي طلع النهاردة، وآخر مرة اتصفّر فيها العداد.
// حالة الرفض (dismissed) بترجع فعلياً لـ Supabase مش هنا — هنا بس عداد الصوت اليومي.
// ═══════════════════════════════════════════════════════════

private val Context.alertDataStore by preferencesDataStore(name = "zad_alert_state")

object AlertStateStore {
    private val KEY_DAILY_COUNT = intPreferencesKey("voice_alert_count")
    private val KEY_LAST_RESET = stringPreferencesKey("last_reset_date")
    private const val MAX_VOICE_ALERTS_PER_DAY = 3

    private suspend fun resetIfNewDay(context: Context) {
        val today = LocalDate.now().toString()
        val prefs = context.alertDataStore.data.first()
        if (prefs[KEY_LAST_RESET] != today) {
            context.alertDataStore.edit {
                it[KEY_DAILY_COUNT] = 0
                it[KEY_LAST_RESET] = today
            }
        }
    }

    suspend fun canSpeakToday(context: Context): Boolean {
        resetIfNewDay(context)
        val count = context.alertDataStore.data.first()[KEY_DAILY_COUNT] ?: 0
        return count < MAX_VOICE_ALERTS_PER_DAY
    }

    suspend fun recordSpoken(context: Context) {
        resetIfNewDay(context)
        context.alertDataStore.edit { it[KEY_DAILY_COUNT] = (it[KEY_DAILY_COUNT] ?: 0) + 1 }
    }
}

// ═══════════════════════════════════════════════════════════
// Router — sync من Supabase، توزيع حسب surface، وكتابة الحالة رجوع
// ═══════════════════════════════════════════════════════════

object ZadAlertRouter {
    // v2 مش تجميل. صوت الـ NotificationChannel **مايتغيّرش بعد إنشائها** — أندرويد
    // بيتجاهل أي setSound على قناة موجودة، عشان المستخدم يفضل هو صاحب القرار في
    // إعداداته. القناة القديمة اتعملت على أجهزة الناس بالصوت الافتراضي، فتغيير الصوت
    // عليها كان هيبقى كود ميت. معرّف جديد + حذف القديم هو الطريقة الوحيدة.
    private const val CHANNEL_ID = "zad_brain_alerts_v2"
    private const val LEGACY_CHANNEL_ID = "zad_brain_alerts"

    /** يسحب pending insights من Supabase، يحدّث المرآة المحلية. ينفع يتنادى في background أو on-resume. */
    suspend fun sync(context: Context, userId: String) {
        val dao = ZadInsightDatabase.getDatabase(context).insightDao()
        try {
            val remote = SupabaseRepo.client.postgrest["zad_insights"].select {
                filter { eq("user_id", userId); neq("status", "dismissed") }
            }.decodeList<ZadInsightRemote>()

            dao.upsertAll(remote.map {
                ZadInsightMirror(it.id, it.kind, it.surface, it.priority, it.title, it.body, it.dedupeKey, it.status, it.actionType, it.aboutItem, it.createdAt)
            })

            // كان الشرط هنا `priority == "critical"`. الـenum بتاع emit_insight فيه قيمتين
            // بس — "normal" و"critical" — والافتراضي "normal"، والأسئلة كلها متثبتة على
            // "normal" في الكود نفسه. النتيجة: تمن رؤى في أسبوعين، كلهم "normal"، وولا
            // إشعار واحد وصل التليفون من العقل خالص. المسار كله كان كود ميت.
            //
            // دلوقتي أي رؤية لسه pending بتنزل إشعار، و"critical" بقت بتحدد **الصوت** بس —
            // وده اللي AlertStateStore والسقف اليومي كانوا موجودين عشانه من الأصل.
            // بيتحدد "seen" بعد التوجيه، وده اللي بيمنع التكرار: الـsync الجاي مش هيلاقيها
            // pending. الكارت في الصفحة الرئيسية لسه بيظهر عادي — observeBySurface بتستبعد
            // "dismissed" بس، مش "seen".
            // فيه رؤى pending عمرها أسبوع — عمرها ما اتبعتت عشان الشرط القديم. من غير سقف
            // عمر، أول sync بعد التحديث كان هيرمي كوم إشعارات قديمة مرة واحدة. اللي أقدم من
            // يومين بيفضل كارت في الصفحة والجرس (وده مكانه الصح)، بس مابيرنّش.
            val freshCutoff = java.time.Instant.now().minusSeconds(48 * 3600)
            remote.filter { it.status == "pending" }
                .forEach { insight ->
                    // PostgREST بترجّع `+00:00` مش `Z`، وInstant.parse على أندرويد (حتى
                    // مع desugaring) بتترمي على الشكل ده. OffsetDateTime بتاكل الاتنين.
                    val createdAt = insight.createdAt?.let { raw ->
                        try {
                            java.time.OffsetDateTime.parse(raw).toInstant()
                        } catch (e: Exception) {
                            try { java.time.Instant.parse(raw) } catch (e2: Exception) { null }
                        }
                    }
                    val isFresh = createdAt == null || createdAt.isAfter(freshCutoff)
                    if (isFresh) {
                        routeAlert(context, insight.title, insight.body, speak = insight.priority == "critical")
                        updateStatus(context, insight.id, "seen")
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ZadAlertRouter", "sync() failed: ${e.message}")
        }
    }

    /** كروت الصفحة الرئيسية — home_card بس، غير مرفوضة */
    fun homeCardInsights(context: Context): Flow<List<ZadInsightMirror>> =
        ZadInsightDatabase.getDatabase(context).insightDao().observeBySurface("home_card")

    /** مركز التنبيهات الموحد — كل حاجة غير مرفوضة (bell + home_card + voice كلهم بيظهروا هنا كمان) */
    fun bellInsights(context: Context): Flow<List<ZadInsightMirror>> =
        ZadInsightDatabase.getDatabase(context).insightDao().observeAll()

    private suspend fun routeAlert(context: Context, title: String, body: String, speak: Boolean) {
        // الإشعار المرئي أولاً دايماً، الصوت إضافة مش بديل — لو الـ TTS فشل أو اللغة مش متاحة
        // برضه المستخدم شاف الإشعار
        showSystemNotification(context, title, body)
        if (!speak) return
        if (!AlertStateStore.canSpeakToday(context)) {
            android.util.Log.d("ZadAlertRouter", "Voice alert cap reached today — showing visual only")
        } else {
            // العدّاد بيتزوّد هنا، عند النطق فعلاً. كان بيتزوّد جوه showSystemNotification،
            // يعني كل إشعار **مرئي** كان بيستهلك من سقف الصوت اليومي — وبعد تلات إشعارات
            // صامتة كان زاد بيخرس بقية اليوم من غير ما ينطق ولا مرة.
            AlertStateStore.recordSpoken(context)
            // كان الـ TTS instance عمره ما بيتعمله shutdown() — نفس التسريب بالظبط اللي في
            // ZadNotifier.speakArabic، وده المسار اللي بينادى أكتر (PeriodicAnalysisWorker
            // كل ٦ ساعات + كل فتح تطبيق + كل دخول geofence). applicationContext هنا كمان
            // بدل context الخام اللي جاي من الـ caller (ممكن يكون Activity في بعض المسارات).
            var tts: TextToSpeech? = null
            var finished = false
            fun finishOnce() {
                if (finished) return
                finished = true
                try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { /* ignore */ }
            }

            tts = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    finishOnce()
                    return@TextToSpeech
                }
                val arabicAvailable = tts?.isLanguageAvailable(java.util.Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (arabicAvailable < TextToSpeech.LANG_AVAILABLE) {
                    android.util.Log.w("ZadAlertRouter", "Arabic TTS not available on this device — visual notification only")
                    finishOnce()
                    return@TextToSpeech
                }
                tts?.language = java.util.Locale("ar")
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finishOnce() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { finishOnce() }
                })
                tts?.speak("$title. $body", TextToSpeech.QUEUE_FLUSH, null, "zad_brain_alert")
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finishOnce() }, 15_000)
        }
    }

    private fun showSystemNotification(context: Context, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // لو الإذن مترفض، notify() بترجع من غير ما ترمي أي حاجة — الإشعار بيختفي في صمت
        // تام ومفيش أي أثر في اللوج يقول ليه. السطر ده هو الفرق بين "الإشعارات مش بتيجي"
        // و"الإشعارات مش بتيجي لأن الإذن مترفض".
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.w("ZadAlertRouter", "POST_NOTIFICATIONS not granted — \"$title\" will not be shown")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // الافتراضي كان نغمة النظام — نفس صوت أي إشعار من أي تطبيق. زاد بقى له
            // نغمته: خامسة صاعدة بظرف صوتي ناعم (res/raw/zad_alert.wav، مولّدة بنفس
            // حسابات ZadChime عشان اللمسة والإشعار يبقوا نفس الصوت).
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            val channel = NotificationChannel(CHANNEL_ID, "تنبيهات عقل زاد", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(
                android.net.Uri.parse("android.resource://${context.packageName}/${com.example.R.raw.zad_alert}"),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        // هاش العنوان+النص بدل currentTimeMillis().toInt() — ده كان بيفيض ويلف، ونفس
        // الرؤية لو اتوجّهت مرتين كانت بتبقى إشعارين في الشيد بدل ما التاني يستبدل الأول.
        val notifId = (title + body).hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
        manager.notify(notifId, notification)
    }

    /** المستخدم شاف الكارت — يتسجل seen محلياً وعلى Supabase */
    suspend fun markSeen(context: Context, id: String) = updateStatus(context, id, "seen")

    /** المستخدم تفاعل مع الرؤية (جاوب سؤال، ضغط زرار الإجراء) */
    suspend fun markActed(context: Context, id: String) = updateStatus(context, id, "acted")

    /**
     * رفض نهائي — العقل بيقرا dismissed_keys في الـ snapshot الجاي وميرفعش نفس التنبيه تاني
     * (Task 10.4: "dismissed is permanent"). لازم يوصل لـ Supabase فعلياً، مش محلي بس.
     */
    suspend fun markDismissed(context: Context, id: String) = updateStatus(context, id, "dismissed")

    private suspend fun updateStatus(context: Context, id: String, status: String) {
        ZadInsightDatabase.getDatabase(context).insightDao().updateStatus(id, status)
        try {
            SupabaseRepo.client.postgrest["zad_insights"].update(mapOf("status" to status)) {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZadAlertRouter", "updateStatus($status) sync failed: ${e.message}")
        }
    }
}
