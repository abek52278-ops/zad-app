package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ZadV3
import com.example.ui.theme.glassBlur

/** v3 route ids */
object V3Routes {
    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val ASSISTANT = "assistant"
    const val SUBS = "subs"
    const val SHOPPING = "shopping"
    const val FAMILY = "family"
    const val BUDGET = "budget"
    const val PHARMACY = "pharmacy"
    const val MAINTENANCE = "maintenance"
    const val DEALS = "deals"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val TASBIHA = "tasbiha"
    const val KNOWLEDGE = "knowledge"
}

// Map V3Routes to V2Routes for backward compat with viewModel commands
val V3_TO_V2_ROUTE: Map<String, String> = mapOf(
    V3Routes.HOME to "home",
    V3Routes.INVENTORY to "inventory",
    V3Routes.ASSISTANT to "assistant",
    V3Routes.SUBS to "subs",
    V3Routes.SHOPPING to "shopping",
    V3Routes.FAMILY to "family",
    V3Routes.BUDGET to "budget",
    V3Routes.PHARMACY to "pharmacy",
    V3Routes.MAINTENANCE to "maintenance",
    V3Routes.DEALS to "deals",
    V3Routes.PROFILE to "profile",
    V3Routes.NOTIFICATIONS to "notifications",
    V3Routes.TASBIHA to "tasbiha",
    V3Routes.KNOWLEDGE to "knowledge",
)

/**
 * V3Header — iOS-style nav bar with logo + title + notification bell
 */
@Composable
fun V3Header(
    title: String,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassBlur(14f)
            .background(Color(0xF2F9FAFB))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZadV3.mint50),
                contentAlignment = Alignment.Center,
            ) { Text("🥕", fontSize = 16.sp) }

            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ZadV3.green800,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.weight(1f))

            // Notification bell
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ZadV3.green800.copy(alpha = 0.08f))
                    .pressableScale(onClick = onOpenNotifications),
                contentAlignment = Alignment.Center,
            ) { Text("🔔", fontSize = 15.sp) }

            // Avatar/profile
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ZadV3.green800)
                    .pressableScale(onClick = onOpenProfile),
                contentAlignment = Alignment.Center,
            ) { Text("👤", fontSize = 16.sp) }
        }
        Text(
            title, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = ZadV3.ink, letterSpacing = (-0.3).sp,
            modifier = Modifier.fadeUpOnAppear(50),
        )
    }
}

// ── Bottom capsule navigation ─────────────────────────────────────────────────

private data class V3Tab(val route: String, val icon: String, val labelRes: Int)

private val v3Tabs = listOf(
    V3Tab(V3Routes.HOME, "🏠", R.string.nav_tab_home),
    V3Tab(V3Routes.ASSISTANT, "🧠", R.string.nav_assistant),
    V3Tab(V3Routes.BUDGET, "🏦", R.string.nav_budget),
    V3Tab(V3Routes.PROFILE, "⚙️", R.string.screen_title_profile),
)

@Composable
fun V3BottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .height(70.dp)
            .shadow(elevation = 24.dp, shape = ZadV3.rPill, spotColor = Color(0x240F172A))
            .clip(ZadV3.rPill)
            .glassBlur(20f)
            .background(Color.White.copy(alpha = 0.78f))
            .border(0.5.dp, Color.Black.copy(alpha = 0.06f), ZadV3.rPill),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            v3Tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .pressableScale { onNavigate(tab.route) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(tab.icon, fontSize = 20.sp)
                    Text(
                        stringResource(tab.labelRes),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) ZadV3.green800 else ZadV3.gray400,
                    )
                }
            }

            // Central mic FAB
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .offset(y = (-4).dp)
                    .shadow(elevation = 20.dp, shape = CircleShape, spotColor = Color(0x59064E3B))
                    .clip(CircleShape)
                    .background(ZadV3.green800)
                    .pressableScale(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) { Text("🎙️", fontSize = 20.sp) }
        }
    }
}