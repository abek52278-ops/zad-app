package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    ZadListCard(containerColor = catFoodBg, contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = catFoodIcon, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.inventory_checkin_question, item.itemName),
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(stringResource(R.string.inventory_checkin_hint), fontSize = 12.sp, color = onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecrement(item) }) {
                    Text(stringResource(R.string.inventory_checkin_minus_one))
                }
                OutlinedButton(onClick = { onFinished(item) }) {
                    Text(stringResource(R.string.inventory_checkin_finished), color = dangerColor)
                }
                TextButton(onClick = { onStillHave(item) }) {
                    Text(stringResource(R.string.inventory_checkin_still_have), color = onSurfaceVariant)
                }
            }
        }
    }
}
