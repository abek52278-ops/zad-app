package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تغطية نبض الكورة مع الصوت.
 *
 * السياق: `micLevel` كان متحسب من بايتات PCM الخام من زمان، وقارئه الوحيد كان
 * `ZadAudioWavebars`. يعني في شيت الصوت الأعمدة كانت بترقص والكورة جنبها بتتنفس على
 * تايمر ثابت 2400ms مش سامعة حاجة — الحالة كانت بتغيّر اللون بس.
 *
 * الدالة دي هي قلب التوصيل، واتفصلت نقية عشان تتقاس: الفلتر نفسه هو اللي بيحدد
 * إذا كانت الحركة هتتقري "بتتجاوب" ولا "بترتعش"، وده مالوش أي أثر يبان في بناء
 * ولا في لينت.
 */
class OrbAudioLevelTest {

    @Test
    fun `rises faster than it falls — that asymmetry is the whole effect`() {
        val up = smoothOrbLevel(current = 0f, raw = 1f)
        val down = smoothOrbLevel(current = 1f, raw = 0f)
        // الصعود بياخد 45% من المسافة، الهبوط 12% بس
        assertEquals(0.45f, up, 0.0001f)
        assertEquals(0.88f, down, 0.0001f)
        assertTrue("لازم يطلع أسرع ما بينزل", (up - 0f) > (1f - down))
    }

    @Test
    fun `clamps input so a bad rms cannot blow the pulse up`() {
        // rms * 4.0 في المصدر ممكن يعدّي 1 نظرياً قبل الـcoerce؛ الفلتر مابيثقش فيه
        assertEquals(smoothOrbLevel(0f, 1f), smoothOrbLevel(0f, 9f), 0.0001f)
        assertEquals(smoothOrbLevel(0.5f, 0f), smoothOrbLevel(0.5f, -3f), 0.0001f)
    }

    @Test
    fun `settles at the target instead of oscillating around it`() {
        var v = 0f
        repeat(40) { v = smoothOrbLevel(v, 0.7f) }
        assertEquals(0.7f, v, 0.001f)
    }

    @Test
    fun `a silence gap between words does not drop the orb to rest`() {
        var v = 0.8f
        // سكتة قصيرة: كام بافر صامت ورا بعض
        repeat(3) { v = smoothOrbLevel(v, 0f) }
        assertTrue("الكورة لازم تفضل واضحة النبض في السكتة القصيرة، لقينا $v", v > 0.5f)
    }

    @Test
    fun `staying silent does eventually return to rest`() {
        var v = 1f
        repeat(60) { v = smoothOrbLevel(v, 0f) }
        assertTrue("بعد صمت طويل لازم ترجع لسكونها، لقينا $v", v < 0.01f)
    }
}
