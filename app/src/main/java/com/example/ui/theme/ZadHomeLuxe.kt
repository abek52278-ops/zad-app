package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ألوان وأشكال الرئيسية الفاخرة (UI_ARCHITECTURE_SPEC.md §2.1/§4.1 rebuild, 2026-09-03).
 * نطاقها HomeScreen وكومبوننتاتها التابعة بس — مش بديل لـ ZadV3/ZadV2 المشترك في
 * باقي التطبيق، عشان إعادة البناء متتسربش تغييرات بصرية على شاشات تانية شغالة.
 */
object ZadLuxe {
    val emerald = Color(0xFF1B4332)
    val ochre = Color(0xFFC68216)
    val terracotta = Color(0xFFD95726)
    val canvasBackground = Color(0xFFF8F9FA)
    val cardWhite = Color(0xFFFFFFFF)
    val hairline = Color(0xFFE0E3DA)

    /** زوايا Squircle ناعمة للكروت الفاخرة — 20.dp حسب المواصفة. */
    val squircle = RoundedCornerShape(20.dp)
}
