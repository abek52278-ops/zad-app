package com.example.data

import com.example.ui.theme.ZadExtendedColorsDark
import com.example.ui.theme.ZadExtendedColorsLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هوية الذكاء (البنفسجي الرسمي) — المرحلة ٣، قرار المستخدم 2026-09-13.
 *
 * شاشة العقل كانت فيها ٢٣ هيكس بنفسجي من غير تعريف، واتقرر إنها تتعلن هوية للذكاء بدل
 * ما تتشال. التست ده بيحرس حاجتين:
 *
 * ١. **اللايت ماتغيّرش** — القرار كان "اعلنها زي ما هي"، مش "غيّر شكل الشاشة". لو حد
 *    عدّل القيم دي، ده قرار تصميمي جديد لازم يتاخد عن قصد، مش جانبي.
 * ٢. **الدارك مقروء** — قبل التوكنات صندوق السرد كان بيفضل لافندر فاتح وسط شاشة داكنة،
 *    والأبيض على بنفسجي الدارك كان هيبقى 2.64:1.
 */
class AiIdentityContrastTest {

    private fun relativeLuminance(color: Long): Double {
        fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun rgb(c: androidx.compose.ui.graphics.Color): Long =
        ((c.value shr 32).toLong() and 0xFFFFFFFFL) and 0xFFFFFF

    /** سطح الكروت الفعلي: أبيض في اللايت، ZadIosSurfaceDark في الدارك. */
    private val lightSurface = 0xFFFFFFL
    private val darkSurface = rgb(ZadExtendedColorsDark.surfaceContainer)

    private fun assertAtLeast(name: String, ratio: Double, bar: Double) =
        assertTrue("$name تباينه ${"%.2f".format(ratio)}:1 — تحت $bar:1", ratio >= bar)

    @Test
    fun `light theme keeps the exact purple the screen already used`() {
        assertEquals(0x9333EAL, rgb(ZadExtendedColorsLight.aiAccent))
        assertEquals(0x6C63FFL, rgb(ZadExtendedColorsLight.aiAccentEnd))
        assertEquals(0xF5F3FFL, rgb(ZadExtendedColorsLight.aiNarrativeContainer))
        assertEquals(0x4C1D95L, rgb(ZadExtendedColorsLight.onAiNarrative))
    }

    @Test
    fun `accent text reads on the card surface in both themes`() {
        // عناوين وشرائح الشبكة العصبية نص فعلي (labelSmall/titleSmall) — العتبة 4.5.
        assertAtLeast("light aiAccent", contrast(rgb(ZadExtendedColorsLight.aiAccent), lightSurface), 4.5)
        assertAtLeast("dark aiAccent", contrast(rgb(ZadExtendedColorsDark.aiAccent), darkSurface), 4.5)
    }

    @Test
    fun `content on the accent clears AA, and icons clear 3 to 1 across the gradient`() {
        for ((name, c) in listOf("light" to ZadExtendedColorsLight, "dark" to ZadExtendedColorsDark)) {
            // نص الزرار فوق aiAccent.
            assertAtLeast("$name onAiAccent/aiAccent", contrast(rgb(c.onAiAccent), rgb(c.aiAccent)), 4.5)
            // أيقونة الـHub فوق الجراديانت — الطرفين.
            assertAtLeast("$name onAiAccent/aiAccentEnd", contrast(rgb(c.onAiAccent), rgb(c.aiAccentEnd)), 3.0)
        }
    }

    @Test
    fun `AI narrative box text clears AA in both themes`() {
        for ((name, c) in listOf("light" to ZadExtendedColorsLight, "dark" to ZadExtendedColorsDark)) {
            assertAtLeast("$name narrative", contrast(rgb(c.onAiNarrative), rgb(c.aiNarrativeContainer)), 4.5)
        }
    }

    @Test
    fun `the good health-score step is readable as large text`() {
        // كان #84CC16 = 1.98:1 على الأبيض. الرقم 22sp Black = نص كبير، العتبة 3:1.
        assertAtLeast("light scoreGood", contrast(rgb(ZadExtendedColorsLight.scoreGood), lightSurface), 3.0)
        assertAtLeast("dark scoreGood", contrast(rgb(ZadExtendedColorsDark.scoreGood), darkSurface), 3.0)
    }
}
