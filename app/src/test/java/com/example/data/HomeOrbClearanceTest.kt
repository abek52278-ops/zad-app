package com.example.data

import androidx.compose.ui.unit.dp
import com.example.ui.screens.HomeOrbMetrics
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * المسكوت العائم مايغطّيش آخر كارت في أي وضع سحب.
 *
 * الأرقام دي كانت نسختين منفصلتين: `Spacer(84.dp)` مكتوبة بالإيد، وصندوق آمن
 * 96dp فوق padding 16dp. الـ84 بتغطّي المسكوت وهو مرتاح (80dp) وبس — والسحب
 * بيوصله 112dp، فبيقعد على الكارت بـ28dp ومابيرجعش.
 *
 * التست بيكتب هندسة التخطيط تاني بشكل مستقل بدل ما يعيد استخدام `maxReach`،
 * عشان تست بيقارن رقم بنفسه بيعدّي وهو فاضي.
 */
class HomeOrbClearanceTest {

    @Test
    fun `reserved space covers the orb even when dragged fully up`() {
        // من التخطيط: الصندوق الآمن بيقعد فوق padding من تحت، والمسكوت بيتسحب
        // لحد قمة الصندوق (maxOffset = safeZone - orbSize).
        val orbTopWhenDraggedFully =
            HomeOrbMetrics.bottomPadding + HomeOrbMetrics.orbSize +
                (HomeOrbMetrics.safeZone - HomeOrbMetrics.orbSize)

        assertTrue(
            "المحجوز ${HomeOrbMetrics.reservedBottomSpace} أقل من وصول المسكوت $orbTopWhenDraggedFully",
            HomeOrbMetrics.reservedBottomSpace >= orbTopWhenDraggedFully,
        )
    }

    @Test
    fun `the old hardcoded 84dp would fail this invariant`() {
        // توثيق للحالة اللي اتصلحت: لو رجع حد الرقم الثابت، التست ده بيشرح ليه غلط.
        val orbTopWhenDraggedFully = HomeOrbMetrics.bottomPadding + HomeOrbMetrics.safeZone
        assertTrue("الرقم القديم كان أصغر من الوصول فعلاً", 84.dp < orbTopWhenDraggedFully)
    }
}
