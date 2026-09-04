package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * أسماء مختصرة لتوكنز Cupertino Heritage — **مش بالتة مستقلة**.
 *
 * الملف اتكتب أول مرة للرئيسية لوحدها (UI_ARCHITECTURE_SPEC.md §2.1/§4.1، 2026-09-03)
 * والتعليق القديم هنا كان بيقول "نطاقها HomeScreen بس". ده بقى غلط: §8 من نفس المستند
 * عمّم `ZadLuxe` على التطبيق كله (٧ مراحل، خلصت 2026-09-04)، وهو دلوقتي في ٢٣ ملف —
 * الميزانية والمخزون والصيدلية والبروفايل والاشتراك وغيرهم — زيادة على القيم الافتراضية
 * لـ`ZadListCard` المستخدمة ٦٣ مرة في ١٨ ملف.
 *
 * القيم كانت هيكس خام **مكرر حرفياً** من `Color.kt` (نفس الأرقام بالظبط). التكرار ده
 * معناه إن أي تغيير في البالتة — وأهمه الوضع الداكن — كان هيمشي على مستهلكي
 * `MaterialTheme` بس ويسيب الـ٢٣ ملف دول على ألوان فاتحة ثابتة. دلوقتي كل قيمة بتشاور
 * على توكن Heritage نفسه، فمصدر الحقيقة واحد: `Color.kt`.
 */
object ZadLuxe {
    val emerald = ZadForestEmerald
    val ochre = ZadMustardOchre
    val terracotta = ZadTerracottaRust
    val canvasBackground = ZadIosBackground
    val cardWhite = ZadIosSurface
    val hairline = ZadIosOutline

    /** زوايا Squircle ناعمة للكروت الفاخرة — 20.dp حسب المواصفة. */
    val squircle = RoundedCornerShape(20.dp)
}
