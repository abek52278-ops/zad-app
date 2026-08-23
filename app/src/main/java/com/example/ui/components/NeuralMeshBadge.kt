package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * مؤشر الشبكة العصبية الحي — بيقول للعميل **مين من الوكلاء عالج آخر رسالة**،
 * من إيصال السيرفر الفعلي (zad_brain_runs.specialist) مش ادعاء محلي:
 * - 🟢 متصل: آخر لفة اكتملت بنجاح — اسم الوكيل المتخصص ظاهر.
 * - ⚪ خامل: مفيش لفة متخصصة لسه (general).
 * - 🔵 شغال: اللفة جارية (isTyping).
 *
 * ده نفس مبدأ "receipts only" بتاع المشروع: العميل يشوف البنية الحية لأنها فعلاً حية.
 */
enum class NeuralMeshStatus { IDLE, THINKING, ACTIVE }

@Composable
fun NeuralMeshBadge(
    status: NeuralMeshStatus,
    specialistId: String? = null,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "mesh_badge")
    val pulse by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "mesh_pulse"
    )

    val dotColor = when (status) {
        NeuralMeshStatus.THINKING -> Color(0xFF8B5CF6).copy(alpha = 0.4f + 0.6f * pulse)
        NeuralMeshStatus.ACTIVE -> Color(0xFF00BFA6)
        NeuralMeshStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    val label = when (status) {
        NeuralMeshStatus.THINKING -> "الشبكة العصبية بتشتغل..."
        else -> when (specialistId) {
            "finance" -> "وكيل المال متصل"
            "pantry" -> "وكيل المطبخ والمخزون متصل"
            "pharmacy" -> "وكيل الصيدلية متصل"
            "family" -> "وكيل العائلة متصل"
            else -> "شبكة وكلاء زاد"
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
