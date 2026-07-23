package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Market
import com.example.data.MarketPrefs
import com.example.ui.theme.*
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadTransitions
import com.example.ui.components.pressableScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState

private data class MarketOption(val market: Market, val flag: String, val subtitle: String)

private val marketOptions = listOf(
    MarketOption(Market.SAUDI_ARABIA, "🇸🇦", "ريال سعودي"),
    MarketOption(Market.EGYPT, "🇪🇬", "جنيه مصري"),
    MarketOption(Market.TURKEY, "🇹🇷", "ليرة تركية")
)

@Composable
fun MarketSelectionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Market?>(null) }
    val listVisible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { listVisible.targetState = true }

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        AppearOnEntry {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Text(
                "وين موطنك؟",
                style = Typography.headlineMedium.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "زاد بيتكلم بلهجتك وبيحسب مصروفك بعملة بلدك",
                style = Typography.bodyLarge,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            marketOptions.forEachIndexed { index, option ->
                val isSelected = selected == option.market
                AnimatedVisibility(
                    visibleState = listVisible,
                    enter = ZadTransitions.listItemEnter(index)
                ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .pressableScale()
                        .clickable { selected = option.market },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) primary.copy(alpha = 0.12f) else surface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) primary else outlineVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option.flag, fontSize = 32.sp)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                option.market.displayNameAr,
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onSurface
                            )
                            Text(option.subtitle, style = Typography.bodyMedium, color = onSurfaceVariant)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primary)
                        }
                    }
                }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    selected?.let { market ->
                        MarketPrefs.setMarket(context, market)
                        onContinue()
                    }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(56.dp).pressableScale(),
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("متابعة", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
}
