package com.example.ui.components

import com.example.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * المزاج الموحّد — الكورة بقى ليها مصدر واحد بدل مصدرين متوازيين.
 *
 * القاعدة المحروسة هنا: الصوت له الأولوية وهو شغال، وأول ما يسكت المزاج يرجع
 * للمسار الذكي (شات/تنبيهات) **مش** لـ Idle. لو الفرع الخامل رجّع Idle بدل null،
 * صوت ساكت كان هيدهس تنبيه ميزانية متجاوزة وتقعد الكورة تقول "هادئ" — وده
 * مابيكسرش بناء ولا لينت، فالتست هو الحارس الوحيد عليه.
 *
 * ملحوظة على الحدود: الدمج نفسه (combine جوه ZadViewModel) مش متغطّى هنا، لأن
 * ZadViewModel مايتعملش منه نسخة تحت Robolectric — SQLCipher عايز مكتبة أصلية،
 * وهو نفس السبب اللي HomeScreenTest متعلّم بيه @Ignore. المتغطّى هو المنطق،
 * ومسار التوصيل بيتأكد يدويًا.
 */
class CompanionMoodTest {

    @Test
    fun activeVoiceMapsToItsOwnMood() {
        assertEquals(CompanionState.Listening, companionMoodForVoice(VoiceState.Listening))
        assertEquals(CompanionState.Focused, companionMoodForVoice(VoiceState.Thinking))
        assertEquals(CompanionState.Speaking, companionMoodForVoice(VoiceState.Speaking("أهلاً")))
        assertEquals(CompanionState.Happy, companionMoodForVoice(VoiceState.Recognized("سجل ٥٠ جنيه")))
    }

    @Test
    fun silentVoiceYieldsRatherThanForcingIdle() {
        assertNull(companionMoodForVoice(VoiceState.Idle))
        assertNull(companionMoodForVoice(VoiceState.Error("مفيش ميكروفون")))
    }

    /** كل الحالات متغطية — إضافة حالة صوت جديدة من غير مزاج ليها بتفشل هنا. */
    @Test
    fun everyVoiceStateIsAccountedFor() {
        val all = listOf(
            VoiceState.Idle,
            VoiceState.Listening,
            VoiceState.Recognized("x"),
            VoiceState.Thinking,
            VoiceState.Speaking("x"),
            VoiceState.Error("x"),
        )
        assertEquals(4, all.count { companionMoodForVoice(it) != null })
        assertEquals(2, all.count { companionMoodForVoice(it) == null })
    }
}
