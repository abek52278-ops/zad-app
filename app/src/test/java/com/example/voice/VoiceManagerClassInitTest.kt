package com.example.voice

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `ZadVoiceManager` لازم يتحمّل من غير ما يكون شاف Context.
 *
 * الفخ ده وقّع التطبيق عند الإقلاع فعلاً، وأول ما بيقع بيقع **قبل أي شاشة**:
 *
 *     FATAL EXCEPTION: main
 *     java.lang.ExceptionInInitializerError at MainActivity.onCreate
 *     Caused by: kotlin.UninitializedPropertyAccessException:
 *         lateinit property appContext has not been initialized
 *         at ZadVoiceManager.naturalVoiceEngine_delegate$lambda$7 (:207)
 *         at ZadVoiceManager.<clinit> (:220)
 *
 * والمفارقة إن `naturalVoiceEngine` كان **متعمَّد** يبقى `by lazy` عشان يستنى
 * `init()`، وفوقه تعليق بيقول كده بالنص. اللي كسره سطر تاني خالص: خاصية على
 * مستوى الـobject بتقرا من المحرك (`humanVoiceAvailable = naturalVoiceEngine…`).
 * الخاصية دي بتتنفّذ في `<clinit>`، فبتفكّ الـlazy وهي لسه بدري — الـlazy
 * بيتأجّل لأول قراءة، وسطر تاني عمل القراءة دي في أسوأ لحظة ممكنة.
 *
 * اتصلح في `ee341cb` (2026-09-04) وبعدها الـsingleton اتعاد بناؤه على
 * `Context?` قابل للـnull مع `init()` صريحة. التست هنا عشان ما يرجعش تالت مرة:
 * أي مُهيّئ جديد على مستوى الـobject يلمس Context هيولّع هنا بدل ما يولّع في إيد
 * العميل. **مافيش نداء لـ`init()` هنا عن قصد** — ده بالظبط اللي بيتقاس.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceManagerClassInitTest {

    @Test
    fun `reading state before init does not blow up the class initializer`() {
        // أول قراءة هي اللي بتجبر <clinit> يتنفّذ. لو أي خاصية لمست Context،
        // السطر ده بيرمي ExceptionInInitializerError — نفس كراش الإقلاع.
        val available = ZadVoiceManager.humanVoiceAvailable.value

        // القيمة الافتراضية متفائلة عن قصد: الواجهة بتقرا الحالة دي قبل ما
        // المحرك يشتغل، وإخفاء الصوت قبل ما نعرف إنه مش متاح أسوأ من العكس.
        assertTrue("humanVoiceAvailable قبل init لازم تبقى true", available)
    }

    @Test
    fun `every exposed flow is readable before init`() {
        // الواجهة بتقرا الحالات دي من HomeScreen قبل أي تهيئة، فكلها لازم
        // تبقى آمنة من غير Context — مش humanVoiceAvailable بس.
        ZadVoiceManager.voiceState.value
        ZadVoiceManager.isSpeaking.value
        ZadVoiceManager.isListening.value
        ZadVoiceManager.soundLevel.value
    }
}
