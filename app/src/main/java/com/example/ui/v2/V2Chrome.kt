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
import com.example.ui.theme.ZadV2
import com.example.ui.theme.glassBlur

/** v2 route ids */
object V2Routes {
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

data class V2DrawerEntry(val route: String, val labelRes: Int)

val v2DrawerEntries = listOf(
    V2DrawerEntry(V2Routes.HOME, R.string.screen_title_home),
    V2DrawerEntry(V2Routes.INVENTORY, R.string.nav_inventory),
    V2DrawerEntry(V2Routes.ASSISTANT, R.string.nav_assistant),
    V2DrawerEntry(V2Routes.SUBS, R.string.subscriptions_title),
    V2DrawerEntry(V2Routes.SHOPPING, R.string.nav_shopping),
    V2DrawerEntry(V2Routes.FAMILY, R.string.nav_family),
    V2DrawerEntry(V2Routes.BUDGET, R.string.nav_budget),
    V2DrawerEntry(V2Routes.PHARMACY, R.string.nav_pharmacy),
    V2DrawerEntry(V2Routes.MAINTENANCE, R.string.nav_maintenance),
    V2DrawerEntry(V2Routes.DEALS, R.string.nav_deals),
    V2DrawerEntry(V2Routes.PROFILE, R.string.screen_title_profile),
    V2DrawerEntry(V2Routes.NOTIFICATIONS, R.string.notifications_title),
)

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
fun V2Header(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassBlur(14f)
            .background(Color(0xF2F9FAFB)) // prototype rgba(249,250,251,.92)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x14064E3B))
                    .pressableScale(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .width(16.dp)
                                .height(1.5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(ZadV2.green800)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ZadV2.mint50),
                contentAlignment = Alignment.Center,
            ) {
                Text("🥕", fontSize = 15.sp)
            }
            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = ZadV2.green800,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.weight(1f))
        }
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = ZadV2.ink,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.fadeUpOnAppear(50)
        )
    }
}

// ── Bottom floating pill ─────────────────────────────────────────────────────

@Composable
private fun TabItem(route: String, emoji: String, labelRes: Int, selected: Boolean, onNavigate: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pressableScale { onNavigate(route) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(emoji, fontSize = 17.sp)
        Text(
            stringResource(labelRes),
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) ZadV2.green800 else ZadV2.gray400,
        )
    }
}

@Composable
fun V2BottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 22.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 16.dp, shape = ZadV2.rPill, spotColor = Color(0x240F172A))
            .clip(ZadV2.rPill)
            .glassBlur(16f)
            .background(Color.White.copy(alpha = 0.72f))
            .border(0.5.dp, Color.Black.copy(alpha = 0.06f), ZadV2.rPill),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(V2Routes.HOME, "🏠", R.string.nav_tab_home, currentRoute == V2Routes.HOME, onNavigate)
        TabItem(V2Routes.ASSISTANT, "🧠", R.string.nav_assistant, currentRoute == V2Routes.ASSISTANT, onNavigate)
        
        // Central mic FAB
        Box(
            modifier = Modifier
                .size(46.dp)
                .offset(y = (-16).dp)
                .shadow(elevation = 16.dp, shape = CircleShape, spotColor = Color(0x59064E3B))
                .clip(CircleShape)
                .background(ZadV2.green800)
                .pressableScale(onClick = onOpenVoice),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎙️", fontSize = 18.sp)
        }
        
        TabItem(V2Routes.BUDGET, "🏦", R.string.nav_budget, currentRoute == V2Routes.BUDGET, onNavigate)
        TabItem(V2Routes.PROFILE, "⚙️", R.string.screen_title_profile, currentRoute == V2Routes.PROFILE, onNavigate)
    }
}

// ── Drawer ───────────────────────────────────────────────────────────────────

@Composable
fun V2DrawerContent(
    currentRoute: String?,
    kidsMode: Boolean,
    userName: String?,
    onNavigate: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZadV2.mint50),
                contentAlignment = Alignment.Center,
            ) { Text("🥕", fontSize = 18.sp) }
            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = ZadV2.green800,
            )
        }
        v2DrawerEntries
            .filter { !kidsMode || it.route == V2Routes.HOME || it.route == V2Routes.FAMILY }
            .forEach { entry ->
                val selected = currentRoute == entry.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ZadV2.mint50 else Color.Transparent)
                        .clickable { onNavigate(entry.route) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (selected) ZadV2.green600 else ZadV2.gray400)
                    )
                    Text(
                        stringResource(entry.labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) ZadV2.green800 else ZadV2.slate,
                    )
                }
            }
        if (userName != null) {
            Spacer(Modifier.weight(1f))
            Text(
                userName,
                fontSize = 12.sp,
                color = ZadV2.gray500,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
