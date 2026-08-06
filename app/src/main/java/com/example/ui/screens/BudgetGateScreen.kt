package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.*

/**
 * مرحلة ٠ج (docs/agent/PLAN_2026_08_06_rebuild.md) — بوابة إجبارية قبل ما MainScreen يعرض
 * أي شاشة مالية. من غيرها كل شاشة كانت بتقرر لوحدها إزاي تتعامل مع سقف مش معروف (بعضها
 * كان بيعرض صفر كأنه رقم حقيقي)؛ دلوقتي القرار واحد، في نقطة دخول واحدة.
 *
 * مفيش زرار "تخطي" — هنا عمداً بقرار المستخدم (راجع PLAN_2026_08_06_rebuild.md).
 */
@Composable
fun BudgetGateScreen(onSetBudget: (Double) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(stringResource(R.string.budget_setup_prompt_action))
            }
        }
    }

    if (showDialog) {
        BudgetEditDialog(
            currentBudget = 0.0,
            onDismiss = { showDialog = false },
            onSave = { newBudget ->
                onSetBudget(newBudget)
                showDialog = false
            }
        )
    }
}
