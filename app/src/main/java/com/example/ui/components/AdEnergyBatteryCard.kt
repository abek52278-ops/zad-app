package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.primary
import com.example.ui.theme.secondary

@Composable
fun AdEnergyBatteryCard(
    adWatchCount: Int,
    totalRequired: Int = 3,
    onWatchAdClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.ad_energy_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$adWatchCount / $totalRequired",
                    fontWeight = FontWeight.ExtraBold,
                    color = primary,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Battery segments
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            ) {
                for (i in 0 until totalRequired) {
                    val isFilled = i < adWatchCount
                    val animatedColor by animateColorAsState(
                        targetValue = if (isFilled) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        animationSpec = tween(durationMillis = 300),
                        label = "battery_color_$i"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(animatedColor)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.ad_energy_refill_hint, totalRequired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onWatchAdClick,
                    modifier = Modifier
                        .weight(1f)
                        .pressableScale(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.ad_loading_text),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.ad_watch_action, adWatchCount, totalRequired),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onUpgradeClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.pressableScale()
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.upgrade_action),
                        fontSize = 13.sp,
                        color = secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
