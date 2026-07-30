package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.DetectedSubscription
import com.example.ui.theme.onSurface
import com.example.ui.theme.primary
import com.example.ui.theme.primaryContainer

/**
 * AUDIT.md: detectSubscriptions() كان بيسجل اشتراك جديد تلقائي بمجرد فتح الشاشة، بلا
 * أي تأكيد من المستخدم (confidence > 0.8 كفاية). دي الكارت اللي بيسدّ الفجوة دي —
 * الكتابة الفعلية (ZadViewModel.confirmDetectedSubscription) بتحصل بس لما المستخدم يضغط.
 */
@Composable
fun DetectedSubscriptionsSection(
    pending: List<DetectedSubscription>,
    onConfirm: (DetectedSubscription) -> Unit,
    onDismiss: (DetectedSubscription) -> Unit
) {
    if (pending.isEmpty()) return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pending.forEach { sub ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(primaryContainer)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.detected_subscription_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${sub.name} — ${CurrencyFormatter.format(context, sub.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConfirm(sub) },
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Text(stringResource(R.string.detected_subscription_confirm))
                    }
                    OutlinedButton(onClick = { onDismiss(sub) }) {
                        Text(stringResource(R.string.detected_subscription_dismiss))
                    }
                }
            }
        }
    }
}
