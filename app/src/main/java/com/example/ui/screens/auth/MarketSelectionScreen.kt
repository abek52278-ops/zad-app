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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.example.ui.components.zadCardShadow
import androidx.compose.ui.graphics.Color
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

    // part of the auth flow, so it shares the splash canvas with login/sign-up/onboarding
    // instead of the flat white it had
    com.example.ui.components.ZadAuthBackground {
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
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = listVisible,
                    enter = ZadTransitions.listItemEnter(index)
                ) {
                // white card with the shared shadow; selection is a green ring, the way
                // the design marks a chosen row everywhere else
                val optionShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .zadCardShadow(optionShape)
                        .clip(optionShape)
                        .background(surface)
                        .then(
                            if (isSelected) Modifier.border(2.dp, primary, optionShape)
                            else Modifier
                        )
                        .pressableScale()
                        .clickable { selected = option.market }
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

            com.example.ui.components.ZadPrimaryButton(
                text = "متابعة",
                onClick = {
                    selected?.let { market ->
                        MarketPrefs.setMarket(context, market)
                        onContinue()
                    }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
        }
    }
}
