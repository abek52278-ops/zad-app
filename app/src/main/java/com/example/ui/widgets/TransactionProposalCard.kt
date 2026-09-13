package com.example.ui.widgets

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.ZadTransactionProposal
import com.example.ui.theme.Typography
import com.example.ui.theme.dangerColor
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.theme.secondary

@Composable
fun TransactionProposalCard(
    proposal: ZadTransactionProposal,
    resolving: Boolean,
    failed: Boolean,
    onDecision: (String) -> Unit
) {
    val currency = proposal.currency?.takeIf { it.isNotBlank() }
        ?: CurrencyFormatter.symbol(androidx.compose.ui.platform.LocalContext.current)
    val source = proposal.merchantName ?: proposal.bankName
    val direction = when {
        proposal.status == "needs_classification" -> stringResource(R.string.bank_proposal_needs_classification)
        proposal.txnKind == "income" -> stringResource(R.string.bank_proposal_expected_income)
        proposal.txnKind == "transfer" -> stringResource(R.string.bank_proposal_expected_transfer)
        else -> stringResource(R.string.bank_proposal_expected_expense)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier
                        .size(34.dp)
                        .background(primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(7.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bank_proposal_amount, proposal.amount, currency),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                    Text(proposal.title, style = Typography.bodyMedium, color = onSurfaceVariant)
                }
                Text(
                    text = direction,
                    style = Typography.labelSmall,
                    color = secondary
                )
            }

            if (!source.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.bank_proposal_source, source),
                    style = Typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.bank_proposal_review_note),
                style = Typography.bodySmall,
                color = onSurfaceVariant
            )

            if (failed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = dangerColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bank_proposal_failed), style = Typography.labelSmall, color = dangerColor)
                }
            }

            if (resolving) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bank_proposal_processing), style = Typography.labelMedium)
                }
            } else if (proposal.asksDuplicateQuestion) {
                Text(
                    text = stringResource(R.string.bank_proposal_duplicate_question),
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecision("duplicate") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.bank_proposal_duplicate_same))
                    }
                    OutlinedButton(onClick = { onDecision("separate") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.bank_proposal_duplicate_separate))
                    }
                }
            } else if (proposal.status == "needs_classification") {
                DirectionButtons(onDecision = onDecision)
                OutlinedButton(onClick = { onDecision("reject") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bank_proposal_reject))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecision("confirm") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.bank_proposal_confirm))
                    }
                    OutlinedButton(onClick = { onDecision("reject") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.bank_proposal_reject))
                    }
                }
                Text(
                    text = stringResource(R.string.bank_proposal_correct_direction),
                    style = Typography.labelSmall,
                    color = onSurfaceVariant
                )
                DirectionButtons(currentKind = proposal.txnKind, onDecision = onDecision)
            }
        }
    }
}

@Composable
private fun DirectionButtons(
    currentKind: String? = null,
    onDecision: (String) -> Unit
) {
    val choices = listOf(
        "expense" to stringResource(R.string.bank_proposal_expense),
        "income" to stringResource(R.string.bank_proposal_income),
        "transfer" to stringResource(R.string.bank_proposal_transfer)
    ).filterNot { it.first == currentKind }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (decision, label) ->
            OutlinedButton(
                onClick = { onDecision(decision) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
            ) {
                Text(label, style = Typography.labelSmall, maxLines = 1)
            }
        }
    }
}
