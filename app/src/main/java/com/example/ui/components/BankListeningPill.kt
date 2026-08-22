package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * نبض الاستماع — سطر صغير تحت الكارت الأخضر بيقول الحقيقة لحظياً:
 * - أخضر: السيرفس عايش وبيسمع رسايل البنك.
 * - برتقالي (قابل للضغط): النظام قتل السيرفس — ضغطة واحدة تعمل rebind.
 *
 * ده كان الفرق المفقود: العميل مكانش يعرف إن الاستماع وقف غير لما يلاقي
 * الرصيد ثابت أيام. دلوقتي المشكلة بتبان وتتحل من نفس المكان.
 */
@Composable
fun BankListeningPill(
    alive: Boolean,
    lastSeenAt: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (alive) Color(0xFF00BFA6).copy(alpha = 0.12f) else Color(0xFFFF8A3D).copy(alpha = 0.15f)
    val fg = if (alive) Color(0xFF00897B) else Color(0xFFE65100)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = !alive, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Hearing,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (alive) "بسمع رسايل البنك وحدّث رصيدك تلقائياً"
            else "الاستماع لرسايل البنك وقف — اضغط للإصلاح",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = fg,
        )
    }
}
