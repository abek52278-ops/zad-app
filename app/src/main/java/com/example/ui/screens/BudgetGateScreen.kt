package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Market
import com.example.data.MarketPrefs
import com.example.ui.theme.*

/**
 * مرحلة ٠ج (docs/agent/PLAN_2026_08_06_rebuild.md) — بوابة إجبارية قبل ما MainScreen يعرض
 * أي شاشة مالية. من غيرها كل شاشة كانت بتقرر لوحدها إزاي تتعامل مع سقف مش معروف (بعضها
 * كان بيعرض صفر كأنه رقم حقيقي)؛ دلوقتي القرار واحد، في نقطة دخول واحدة.
 *
 * مفيش زرار "تخطي" — هنا عمداً بقرار المستخدم (راجع PLAN_2026_08_06_rebuild.md).
 *
 * بقت خطوة واحدة بتجمع البلد والعملة والسقف مع بعض، مش سقف بس. السبب إن دي أول (وأضمن)
 * نقطة بعد تسجيل الدخول: شاشة اختيار السوق بتتعرض **قبل** التسجيل، فرفع اختيار المستخدم
 * للسيرفر ساعتها بيحصل من غير جلسة وبيضيع — وده اللي كان سايب zad_users.currency فاضية
 * فالبوت يفضل يسأل عن العملة كل مرة رغم إن المستخدم جاوب. عرض البلد هنا بيخلّي المستخدم
 * يشوف اللي زاد فاهمه ويصححه قبل ما أي رقم يتحسب بيه.
 */
@Composable
fun BudgetGateScreen(onComplete: (budget: Double, market: Market) -> Unit) {
    val context = LocalContext.current
    var market by remember { mutableStateOf(MarketPrefs.getMarket(context)) }
    var amountText by remember { mutableStateOf("") }
    var showMarketPicker by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val canContinue = amount != null && amount > 0

    Box(
        modifier = Modifier.fillMaxSize().background(background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.budget_gate_headline),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.budget_gate_body),
                style = Typography.bodyMedium,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // البلد والعملة — معروضين مش مسؤولين عنهم في شاشة تانية، عشان الخطوة تفضل واحدة.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.budget_gate_market_label),
                            style = Typography.labelSmall,
                            color = onSurfaceVariant
                        )
                        Text(
                            "${market.displayNameAr} — ${market.currencyCode}",
                            style = Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = onSurface
                        )
                    }
                    TextButton(onClick = { showMarketPicker = true }) {
                        Text(stringResource(R.string.budget_gate_change_market))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                label = { Text(stringResource(R.string.budget_gate_amount_label)) },
                suffix = { Text(market.currencySymbol, style = Typography.labelMedium) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { amount?.let { onComplete(it, market) } },
                enabled = canContinue,
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.budget_gate_start_action))
            }
        }
    }

    if (showMarketPicker) {
        AlertDialog(
            onDismissRequest = { showMarketPicker = false },
            title = {
                Text(
                    stringResource(R.string.budget_gate_market_label),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                com.example.ui.components.MarketPickerGrid(
                    selected = market,
                    onSelect = {
                        market = it
                        showMarketPicker = false
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showMarketPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
