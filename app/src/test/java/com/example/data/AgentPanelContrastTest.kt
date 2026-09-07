package com.example.data

import com.example.ui.theme.ZadExtendedColorsDark
import com.example.ui.theme.ZadExtendedColorsLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * كارت الأيجنت نصه كله `Color.White` ثابت — لوحة ليلية بالتصميم.
 *
 * بداية التدرّج كانت `primaryContainer`، وهو رمز **بيتقلب مع الثيم**: في الدارك
 * بيبقى #1E4534 (سليم)، وفي اللايت #E8F0EC — يعني أبيض على شبه أبيض بتباين
 * **1.16:1**، أقل من عتبة WCAG AA (4.5:1) بحوالي أربع مرات. والطرف التاني كان
 * سداسي ثابت داكن، فنص الكارت كان مقروء في ناحية وغير مقروء في التانية.
 *
 * التست بيقيس التباين من قيم الثيم نفسها في **النسختين**، فلو حد رجّع رمز
 * بيتقلب مكان الرمزين دول البيلد بيقع بدل ما العطل يرجع صامت.
 */
class AgentPanelContrastTest {

    private fun relativeLuminance(color: Long): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private fun contrastWithWhite(color: Long): Double {
        val l = relativeLuminance(color)
        return (1.0 + 0.05) / (l + 0.05)
    }

    private fun argb(c: androidx.compose.ui.graphics.Color): Long =
        (c.value shr 32).toLong() and 0xFFFFFFFFL

    @Test
    fun `white text clears WCAG AA on both gradient stops, in both themes`() {
        val stops = listOf(
            "light-start" to ZadExtendedColorsLight.agentPanelStart,
            "light-end" to ZadExtendedColorsLight.agentPanelEnd,
            "dark-start" to ZadExtendedColorsDark.agentPanelStart,
            "dark-end" to ZadExtendedColorsDark.agentPanelEnd,
        )
        for ((name, color) in stops) {
            val ratio = contrastWithWhite(argb(color) and 0xFFFFFF)
            assertTrue(
                "$name تباينه ${"%.2f".format(ratio)}:1 — تحت عتبة AA (4.5:1)",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `the status pill text clears AA over its own 12 percent container`() {
        // شريحة السلسلة كانت `Color(0xFFFF5722)` نص وحاوية — نفس الدرجة، فالتباين
        // في اللايت كان **2.75:1**. و`coral` لوحده مابيصلحش (3.38:1) لأن المشكلة
        // في التصميم مش في الرمز: نص بنفس درجة حاوية ١٢٪ منخفض التباين حتماً.
        // والنص 11sp bold يعني مش «نص كبير»، فالعتبة 4.5:1 مش 3:1.
        val cases = listOf(
            Triple("light", ZadExtendedColorsLight.onStatusPill, ZadExtendedColorsLight.coral) to 0xFFFFFFFF,
            Triple("dark", ZadExtendedColorsDark.onStatusPill, ZadExtendedColorsDark.coral) to 0xFF191D17,
        )
        for ((triple, surface) in cases) {
            val (name, fg, container) = triple
            val pill = blend(argb(container) and 0xFFFFFF, 0.12, surface and 0xFFFFFF)
            val ratio = contrast(argb(fg) and 0xFFFFFF, pill)
            assertTrue(
                "$name: نص الشريحة تباينه ${"%.2f".format(ratio)}:1 — تحت 4.5:1",
                ratio >= 4.5,
            )
        }
    }

    private fun blend(fg: Long, alpha: Double, bg: Long): Long {
        var out = 0L
        for (shift in listOf(16, 8, 0)) {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            val v = Math.round(f * alpha + b * (1 - alpha))
            out = out or (v shl shift)
        }
        return out
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `the panel is deliberately identical in light and dark`() {
        // مش سهو: الكارت لوحة ليلية، والتقليب مع الثيم هو اللي كسره أصلاً.
        assertEquals(ZadExtendedColorsLight.agentPanelStart, ZadExtendedColorsDark.agentPanelStart)
        assertEquals(ZadExtendedColorsLight.agentPanelEnd, ZadExtendedColorsDark.agentPanelEnd)
    }
}
