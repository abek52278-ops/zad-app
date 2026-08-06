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
import kotlinx.coroutines.launch
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

@Composable
fun MarketSelectionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Market?>(null) }

    // part of the auth flow, so it shares the splash canvas with login/sign-up/onboarding
    // instead of the flat white it had
    com.example.ui.components.ZadAuthBackground {
        AppearOnEntry {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
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
            Spacer(Modifier.height(28.dp))

            // مرحلة ٢ — ١٩ سوق بدل ٣، فبقى مكوّن مشترك (شبكة + بحث) بدل كارت قايمة لكل
            // واحد؛ نفس المكوّن بالظبط مستخدم في ProfileSubScreens's "البلد والعملة".
            com.example.ui.components.MarketPickerGrid(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))

            com.example.ui.components.ZadPrimaryButton(
                text = "متابعة",
                onClick = {
                    selected?.let { market ->
                        MarketPrefs.setMarket(context, market)
                        // العملة/البلد بتترفع للسيرفر فوراً — العقل والبوت بيلاقوها بدل
                        // الافتراض الخاطئ "ر.س" (مشكلة "قالي مفيش ولا ريال وأنا بالمصري")
                        scope.launch { com.example.data.SupabaseRepo.syncMarketProfile(market) }
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
