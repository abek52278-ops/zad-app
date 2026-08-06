package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.InventoryFlowEngine
import com.example.data.ZadInventory
import com.example.ui.theme.*

/**
 * مرحلة ٣ (docs/agent/PLAN_2026_08_06_rebuild.md) — "المتابعة الدورية" (ConsumptionLearner)
 * كانت إشعار نصي بيوجّه المستخدم يفتح شاشة المخزون ويحدّث الكمية يدوياً. الكارت ده سؤال
 * تفاعلي فوري بأزرار خصم سريعة، على HomeScreen مباشرة — مفيش تنقّل، مفيش شاشة تانية.
 *
 * مرحلة ٥ب-٣ — نفس عائلة الزجاج (`zadGlassBlur` + 24dp) اللي ابتدت في كارت الميزانية،
 * بخلفية كهرمانية (مش أخضر الهيرو ولا أزرق اللوكيشن — تسلسل هرمي بصري واضح بين
 * الكروت التلاتة). الأزرار بقت FilledTonalButton بدل Outlined/Text خام: "−1" تون أخضر
 * هادي (فعل صغير مايستاهلش إنذار)، "خلص" تون أحمر (نهاية دورة المنتج، لازم تبان مختلفة)،
 * "لسه" فضلت TextButton (أقل فعل أهمية — تأجيل بس).
 *
 * أعلى مرشّح واحد بس (الأقرب نفاداً) — سؤالين مع بعض إلحاح، والباقي هيتسأل تلقائياً في
 * الزيارة الجاية طالما لسه مرشّح (مفيش داعي قايمة طويلة هنا).
 */
@Composable
fun InventoryCheckInCard(
    candidate: InventoryFlowEngine.CheckInCandidate,
    onDecrement: (ZadInventory) -> Unit,
    onFinished: (ZadInventory) -> Unit,
    onStillHave: (ZadInventory) -> Unit
) {
    val item = candidate.item
    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    listOf(secondary.copy(alpha = 0.12f), secondaryLight.copy(alpha = 0.08f))
                )
            )
    ) {
        // نفس بقعة الضوء الزجاجية بتاعة باقي الكروت — عائلة بصرية واحدة عبر HomeScreen
        Box(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.TopStart)
                .offset(x = (-26).dp, y = (-26).dp)
                .zadGlassBlur(30.dp)
                .background(Color.White.copy(alpha = 0.30f), CircleShape)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // شارة المنتج العائمة — ظل حقيقي بدل أيقونة مسطّحة، نفس لغة دبوس المكان
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = secondaryDark.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = secondaryDark, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.inventory_checkin_question, item.itemName),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(stringResource(R.string.inventory_checkin_hint), fontSize = 12.sp, color = onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onDecrement(item) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = primary.copy(alpha = 0.14f),
                        contentColor = primary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.inventory_checkin_minus_one))
                }
                FilledTonalButton(
                    onClick = { onFinished(item) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = dangerColor.copy(alpha = 0.14f),
                        contentColor = dangerColor
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.inventory_checkin_finished))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onStillHave(item) }) {
                    Text(stringResource(R.string.inventory_checkin_still_have), color = onSurfaceVariant)
                }
            }
        }
    }
}
