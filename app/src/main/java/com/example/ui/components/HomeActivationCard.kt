package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.HomeActivationProgress
import com.example.data.HomeActivationStep
import com.example.ui.theme.Typography
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.outlineVariant
import com.example.ui.theme.primary
import com.example.ui.theme.surface

@Composable
fun HomeActivationCard(
    progress: HomeActivationProgress,
    onSetBalance: () -> Unit,
    onEnableBankReading: () -> Unit,
    onAddInventoryItem: () -> Unit,
    onSetFirstGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // الرحلة اكتملت؟ ماختفيش — نعرض تهنئة + تلميح أول تجربة (صوت/بوت تليجرام)
    // عشان العميل يعرف إزاي يستخدم اللي فعّله، بدل الكارت يختفي وسيبه محتار.
    if (progress.isComplete) {
        com.example.ui.components.ZadListCard(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00BFA6), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.home_activation_title),
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.activation_done_hint),
                    style = Typography.bodySmall,
                    color = onSurfaceVariant,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = surface,
        border = BorderStroke(1.dp, outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_activation_title),
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_activation_subtitle),
                            style = Typography.bodySmall,
                            color = onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            R.string.home_activation_progress,
                            progress.completedCount,
                            progress.totalCount,
                        ),
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.completedCount.toFloat() / progress.totalCount },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = primary,
                    trackColor = outlineVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            ActivationStepRow(
                title = stringResource(R.string.home_activation_balance_title),
                description = stringResource(R.string.home_activation_balance_desc),
                complete = progress.isComplete(HomeActivationStep.SET_BALANCE),
                onClick = onSetBalance,
            )
            ActivationStepRow(
                title = stringResource(R.string.home_activation_bank_title),
                description = stringResource(R.string.home_activation_bank_desc),
                complete = progress.isComplete(HomeActivationStep.ENABLE_BANK_READING),
                onClick = onEnableBankReading,
            )
            ActivationStepRow(
                title = stringResource(R.string.home_activation_inventory_title),
                description = stringResource(R.string.home_activation_inventory_desc),
                complete = progress.isComplete(HomeActivationStep.ADD_FIRST_INVENTORY_ITEM),
                onClick = onAddInventoryItem,
            )
            ActivationStepRow(
                title = stringResource(R.string.home_activation_goal_title),
                description = stringResource(R.string.home_activation_goal_desc),
                complete = progress.isComplete(HomeActivationStep.SET_FIRST_GOAL),
                onClick = onSetFirstGoal,
            )
        }
    }
}

@Composable
private fun ActivationStepRow(
    title: String,
    description: String,
    complete: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !complete, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (complete) primary else onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (complete) onSurfaceVariant else onSurface,
            )
            Text(
                text = if (complete) stringResource(R.string.home_activation_done) else description,
                style = Typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
        if (!complete) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.open_action),
                tint = primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun HomeToolsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.home_tools_hide else R.string.home_tools_show,
                ),
                style = Typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = null,
                tint = primary,
            )
        }
    }
}
