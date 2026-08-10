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
import androidx.compose.material.icons.filled.PriceCheck
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ZadAiRepository
import com.example.ui.theme.onSurface
import com.example.ui.theme.primary
import com.example.ui.theme.primaryContainer

/**
 * كارت تأكيد صريح لاقتراحات مالية جاية من `agent_turn` (log_transaction،
 * set_monthly_limit، إلخ) — بديل واجهة حقيقي لكتابة "أيوه"/"لا" في الشات النصي.
 * الاتنين بينفذوا نفس المسار سيرفر-سايد (`ZadViewModel.confirmPendingAgentProposals`).
 */
@Composable
fun AgentProposalsCard(
    proposals: List<ZadAiRepository.AgentProposal>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (proposals.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(primaryContainer)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PriceCheck, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(if (proposals.size == 1) R.string.agent_proposal_title_single else R.string.agent_proposal_title_plural),
                style = MaterialTheme.typography.labelMedium,
                color = primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            proposals.forEach { proposal ->
                Text(
                    "• ${proposal.summary}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Text(stringResource(R.string.agent_proposal_confirm_action))
            }
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    }
}
