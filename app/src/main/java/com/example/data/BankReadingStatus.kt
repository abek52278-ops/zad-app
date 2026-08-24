package com.example.data

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * حالة قراءة رسايل البنك الحقيقية — مفيش toggle بيكذب. الشاشة اللي بتعرض الحالة دي
 * (BankReadingStatusScreen) بتقرا نفس المصدرين اللي أندرويد نفسه بيرجعهم، مش قيمة محفوظة
 * ممكن تفضل true حتى لو المستخدم سحب الصلاحية من إعدادات النظام.
 */
object BankReadingStatus {
    private const val TAG = "BankReadingStatus"
    private const val PREFS = "zad_bank_reading_status"
    private const val KEY_LAST_PARSED_AT = "last_parsed_at"
    private const val KEY_LAST_CONNECTED_AT = "last_connected_at"
    private const val KEY_LAST_SEEN_ANY_AT = "last_seen_any_at"

    fun recordParsed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_PARSED_AT, System.currentTimeMillis()).apply()
    }

    fun lastParsedAt(context: Context): Long? = read(context, KEY_LAST_PARSED_AT)

    /** السيرفس اتربط فعلاً (onListenerConnected). */
    fun recordListenerConnected(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CONNECTED_AT, System.currentTimeMillis()).apply()
    }

    fun lastConnectedAt(context: Context): Long? = read(context, KEY_LAST_CONNECTED_AT)

    /**
     * وصل إشعار — **أي** إشعار، حتى لو مش مالي واتفلتر بعد كده.
     *
     * ده الفرق اللي مكانش موجود ومن غيره التشخيص مستحيل: "الصلاحية مداها المستخدم"
     * (isNotificationListenerEnabled) مش نفس "السيرفس عايش وبيوصله إشعارات". لو الصلاحية
     * مفعّلة والرقم ده فاضي، يبقى السيرفس مش مربوط. لو الرقم ده بيتحدّث و
     * [lastParsedAt] فاضي، يبقى السيرفس شغال والفلترة هي اللي بترمي كل حاجة.
     */
    fun recordSawNotification(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SEEN_ANY_AT, System.currentTimeMillis()).apply()
    }

    fun lastSawNotificationAt(context: Context): Long? = read(context, KEY_LAST_SEEN_ANY_AT)

    private fun read(context: Context, key: String): Long? {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, -1L)
        return if (v > 0) v else null
    }

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * صلاحية ممنوحة **مش** معناها سيرفس شغال.
     *
     * أندرويد بيربط NotificationListenerService لما الصلاحية تتدي، لكنه بيقتله كمان تحت
     * ضغط الذاكرة أو بعد تحديث التطبيق أو إعادة تشغيل الجهاز، وساعات مابيرجعش يربطه أبداً.
     * ومفيش أي حاجة في الواجهة بتفرّق: البانر بيقرا الصلاحية بس، فبيقول "تمام" وهو أعمى
     * من أسابيع.
     *
     * `requestRebind` هي الطريقة الرسمية لطلب الربط تاني، وكانت غايبة تماماً من التطبيق.
     * ده اللي خلّى `zad_notification_ingest_events` يفضل فاضي — صفر صف من يوم ما اتعمل —
     * رغم إن العميل مدّى الصلاحية: مفيش إشعار وصل السيرفس أصلاً عشان يتبعت.
     *
     * بتتنادى عند فتح التطبيق. آمنة لو السيرفس مربوط بالفعل (بتبقى no-op فعلياً)، وبتتجاهل
     * بهدوء لو الصلاحية مش موجودة — الطلب وقتها بيترفض وده متوقع مش خطأ.
     */
    /**
     * صحة الاستماع لحظياً — الفرق بين "السيرفس عايش" و"شغال فعلاً":
     * lastSawNotificationAt بيتحدث مع كل إشعار عدّى على الجهاز (حتى غير المالي)،
     * فهو نبض حي. لو الصلاحية منوحة والسيرفس اتربط لكن مرّ وقت طويل من غير أي
     * إشعار خالص، الأرجح إن النظام قتل السيرفس — والحكم هنا أعدل من الأخضر الكاذب.
     */
    fun isListenerAlive(context: Context): Boolean {
        if (!isNotificationListenerEnabled(context)) return false
        if (lastConnectedAt(context) == null) return false
        val saw = lastSawNotificationAt(context) ?: return false
        // سماحية 10 دقايق: الجهاز ممكن يكون ساكت فعلاً ومفيش أي إشعارات خالص
        return System.currentTimeMillis() - saw < 10 * 60_000L
    }

    fun requestRebindIfPermitted(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (!isNotificationListenerEnabled(context)) return
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, com.example.services.UnifiedBankListener::class.java)
            )
            Log.d(TAG, "requestRebind() sent for UnifiedBankListener")
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind() failed: ${e.message}")
        }
    }

    // ── وضع الاختبار (2026-08-24): تشخيص فجوة الرصد في ثواني ──
    private const val KEY_TEST_AT = "test_ping_at"
    private const val KEY_TEST_RESULT = "test_ping_result"

    /**
     * ابعت إشعار تجريبي من التطبيق نفسه — بيمر على **نفس** مسار المستمع الحقيقي:
     * UnifiedBankListener.onNotificationPosted → فلترة → العقل. النتيجة بتتحفظ
     * و[BroadcastReceiver] اللي في الشاشة بيقراها.
     *
     * ده هو الفرق بين "الصلاحية شكلها تمام" و"الرصد شغال فعلاً": لو الإشعار التجريبي
     * ماوصلش السيرفر خلال دقيقة، المشكلة في السيرفس/الإذن مش في التحليل.
     */
    fun sendTestNotification(context: Context) {
        val builder = androidx.core.app.NotificationCompat.Builder(
            context, "zad_test_channel"
        )
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("اختبار زاد — عملية تجريبية")
            .setContentText("تم خصم 123.45 جنيه من حسابك — اختبار رصد")
            .setAutoCancel(true)
            .addPerson("test:zad-diagnostic")

        val mgr = NotificationManagerCompat.from(context)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            mgr.notify(99001, builder.build())
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TEST_AT, System.currentTimeMillis()).putString(KEY_TEST_RESULT, "sent").apply()
    }

    /** المستمع وصل للإشعار التجريبي (recordTestPing بيتنادى منه). */
    fun markTestReceived(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TEST_RESULT, "received_by_listener").apply()
    }

    fun testSentAt(context: Context): Long? = read(context, KEY_TEST_AT)
    fun testResult(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TEST_RESULT, null)

    /** نتيجة الاختبار كنص قابل للعرض: فين القطع بالظبط؟ */
    fun diagnose(context: Context): String {
        if (!isNotificationListenerEnabled(context)) return "❌ الصلاحية مش ممنوحة — فعّلها من إعدادات الوصول للإشعارات"
        if (lastConnectedAt(context) == null) return "❌ السيرفس مش مربوط — اضغط زر إعادة الربط"
        return when (testResult(context)) {
            null, "sent" -> if (lastSawNotificationAt(context) == null) "❌ السيرفس مربوط بس مش بيوصلوش إشعارات — جرب requestRebind"
                             else "⏳ مستني وصول الإشعار التجريبي للمستمع..."
            "received_by_listener" -> if (lastParsedAt(context) != null) "✅ الرصد شغال بالكامل" else "✅ المستمع مسكه والفلترة شغالة"
            else -> "❓ حالة غير معروفة — جرب تاني"
        }
    }
}
