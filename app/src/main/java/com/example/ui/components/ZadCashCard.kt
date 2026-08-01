package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.data.CurrencyFormatter
import com.example.data.HabitChip
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.outlineVariant
import com.example.ui.theme.primary
import com.example.ui.theme.secondary
import com.example.ui.theme.secondaryDark
import com.example.ui.theme.surface

/**
 * Task 19.4 — "الميزة كلها بتنجح أو تفشل هنا" (EPIC_1_4.md). الكارت ده *هو* التذكير:
 * بيظهر لما فيه كاش (سحب ATM لسه ماتصرفش)، ويختفي لوحده لما يوصل صفر. لازم يفضل
 * كده — ممنوع أي إشعار غرضه الوحيد إنه يفكّر المستخدم يسجل الكاش (لو الكارت مش كفاية،
 * الحل مش إزعاج أكتر، الحل إن المستخدم هيسكت كل الإشعارات وميوصلهوش تنبيه الدواء كمان).
 */
@Composable
fun CashCard(
    cashOnHand: Double,
    habitChips: List<HabitChip>,
    onChipTap: (HabitChip) -> Unit,
    onSpentFromCash: (amount: Double, title: String, category: String) -> Unit
) {
    if (cashOnHand <= 0.0) return
    val context = LocalContext.current
    var showSpentDialog by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = cardShape, spotColor = secondary.copy(alpha = 0.35f))
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(secondaryDark, secondary)))
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("كاش معاك", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    CurrencyFormatter.format(context, cashOnHand),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = { showSpentDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = secondaryDark),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("صرفت منهم", fontWeight = FontWeight.Bold)
            }
        }

        if (habitChips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            // خلفية فاتحة عشان الشيبس (لسه بتفترض نص غامق) تتقرأ فوق تدرج الكارت الذهبي
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(vertical = 4.dp)
            ) {
                HabitChipsRow(chips = habitChips, onChipTap = onChipTap, horizontalPadding = 12.dp)
            }
        }
    }

    if (showSpentDialog) {
        SpentFromCashDialog(
            onDismiss = { showSpentDialog = false },
            onSave = { amount, title, category ->
                onSpentFromCash(amount, title, category)
                showSpentDialog = false
            }
        )
    }
}

/** حوار بسيط لتسجيل صرف من الكاش — دايماً مصروف، مفيش toggle دخل/مصروف زي AddTransactionDialog العام */
@Composable
private fun SpentFromCashDialog(onDismiss: () -> Unit, onSave: (Double, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صرفت منهم كام؟", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("صرفتهم على إيه؟ (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = amount.toDoubleOrNull() ?: 0.0
                if (parsed > 0.0) onSave(parsed, title.ifBlank { "صرف كاش" }, "أخرى")
            }) { Text("تسجيل") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * Task 22 — كارت لكل عادة صرف ثابتة، Tap واحد يسجّلها كمصروف كاش فوري.
 *
 * Lived in `TransactionsScreen` until that screen was deleted with the rest of the
 * pre-mockup UI; `CashCard` above is its only consumer, so it moved here rather
 * than dying with its old host.
 */
@Composable
fun HabitChipsRow(
    chips: List<HabitChip>,
    onChipTap: (HabitChip) -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "عادات صرفك",
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = onSurfaceVariant
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chips, key = { it.label + it.category }) { chip ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(surface)
                        .border(1.dp, outlineVariant, RoundedCornerShape(20.dp))
                        .clickable { onChipTap(chip) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            CurrencyFormatter.formatNumber(context, chip.amount),
                            fontSize = 11.sp,
                            color = onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
