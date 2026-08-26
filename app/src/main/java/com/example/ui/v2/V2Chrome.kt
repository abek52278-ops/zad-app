package com.example.ui.v2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LocaleHelper
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

// ── Drawer destinations (renderDrawer / DRAWER_ITEMS) ────────────────────────

private data class DrawerEntry(val route: String, val labelRes: Int)

private val drawerEntries = listOf(
    DrawerEntry(V3Routes.HOME, R.string.nav_tab_home),
    DrawerEntry(V3Routes.INVENTORY, R.string.nav_inventory),
    DrawerEntry(V3Routes.ASSISTANT, R.string.screen_title_assistant),
    DrawerEntry(V3Routes.SUBS, R.string.subscriptions_title),
    DrawerEntry(V3Routes.SHOPPING, R.string.nav_shopping),
    DrawerEntry(V3Routes.FAMILY, R.string.nav_family),
    DrawerEntry(V3Routes.BUDGET, R.string.screen_title_budget),
    DrawerEntry(V3Routes.PHARMACY, R.string.nav_pharmacy),
    DrawerEntry(V3Routes.MAINTENANCE, R.string.nav_maintenance),
    DrawerEntry(V3Routes.DEALS, R.string.screen_title_deals),
    DrawerEntry(V3Routes.PROFILE, R.string.screen_title_profile),
    DrawerEntry(V3Routes.NOTIFICATIONS, R.string.notifications_title),
)

/**
 * Side Drawer overlay — the prototype's renderDrawer: scrim + sliding panel
 * from the reading direction edge, carrot logo header + destination list.
 */
@Composable
fun V3DrawerOverlay(
    visible: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A).copy(alpha = 0.4f))
                .pressableScale(onClick = onDismiss),
        )
        // Panel (RTL: right edge; LTR: left edge) — 78% width capped at 300dp
        Row {
            if (!LocaleHelper.isArabic()) {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxSize()
                        .shadow(elevation = 30.dp, spotColor = Color(0x2E0F172A))
                        .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                        .background(Color.White),
                ) { DrawerContent(currentRoute, onNavigate, onDismiss) }
            }
            Spacer(Modifier.weight(1f))
            if (LocaleHelper.isArabic()) {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxSize()
                        .shadow(elevation = 30.dp, spotColor = Color(0x2E0F172A))
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(Color.White),
                ) { DrawerContent(currentRoute, onNavigate, onDismiss) }
            }
        }
    }
}

@Composable
private fun DrawerContent(currentRoute: String?, onNavigate: (String) -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZadV3.mint50),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_carrot_logo),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ZadV3.green800,
            )
        }
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            drawerEntries.forEachIndexed { idx, entry ->
                val active = currentRoute == entry.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fadeUpOnAppear(idx * 40L)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ZadV3.green800.copy(alpha = 0.08f) else Color.Transparent)
                        .pressableScale {
                            onDismiss()
                            onNavigate(entry.route)
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (active) ZadV3.green800 else ZadV3.gray400.copy(alpha = 0.5f)),
                    )
                    Text(
                        stringResource(entry.labelRes),
                        fontSize = 14.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (active) ZadV3.green800 else Color(0xFF374151),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * V3Header — prototype renderHeader: hamburger → drawer, logo tile, app name,
 * language toggle pill, big page title below. Sticky glass effect.
 */
@Composable
fun V3Header(
    title: String,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {},
) {
    var drawerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassBlur(14f)
            .background(Color(0xF2F9FAFB))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .border(0.dp, Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Hamburger button (opens side drawer)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ZadV3.green800.copy(alpha = 0.08f))
                    .pressableScale(onClick = { drawerOpen = true }),
                contentAlignment = Alignment.Center,
            ) { HamburgerIcon(tint = ZadV3.green800) }

            // Logo tile with drawn carrot vector
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ZadV3.mint50),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_carrot_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(21.dp),
                )
            }

            Text(
                stringResource(R.string.app_name),
                fontWeight = FontWeight.Bold, fontSize = 19.sp, color = ZadV3.green800,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.weight(1f))

            // Language toggle pill (ar ⇄ en)
            Box(
                modifier = Modifier
                    .clip(ZadV3.rPill)
                    .background(ZadV3.green800.copy(alpha = 0.08f))
                    .pressableScale(onClick = { LocaleHelper.toggleLanguage() })
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    if (LocaleHelper.isArabic()) "EN" else "AR",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.green800,
                )
            }
        }
        Text(
            title, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = ZadV3.ink, letterSpacing = (-0.3).sp,
            modifier = Modifier.fadeUpOnAppear(50),
        )
    }

    if (drawerOpen) {
        V3DrawerOverlay(
            visible = true,
            currentRoute = null,
            onNavigate = onNavigate,
            onDismiss = { drawerOpen = false },
        )
    }
}

// ── Drawn vector-style icons ─────────────────────────────────────────────────

@Composable
private fun HamburgerIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp, 12.dp)) {
        val stroke = 1.8.dp.toPx()
        val yPositions = listOf(stroke / 2, size.height / 2, size.height - stroke / 2)
        yPositions.forEach { y ->
            drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
private fun HomeTabIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp, 18.dp)) {
        val s = 1.8.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s, size.height * 9f / 20f)
            lineTo(size.width / 2, 1f)
            lineTo(size.width - s, size.height * 9f / 20f)
            lineTo(size.width - s, size.height - s)
            lineTo(size.width * 13f / 22f, size.height - s)
            lineTo(size.width * 13f / 22f, size.height - 6.dp.toPx())
            lineTo(size.width * 9f / 22f, size.height - 6.dp.toPx())
            lineTo(size.width * 9f / 22f, size.height - s)
            lineTo(s, size.height - s)
            close()
        }
        drawPath(path, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun InventoryTabIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(19.dp, 18.dp)) {
        val s = 1.8.dp.toPx()
        drawRoundRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(s / 2, 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - s, size.height - 6.dp.toPx() - s / 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s / 2, 6.dp.toPx())
            lineTo(4.dp.toPx(), 1f)
            lineTo(size.width - 4.dp.toPx(), 1f)
            lineTo(size.width - s / 2, 6.dp.toPx())
        }
        drawPath(path, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun AssistantTabIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(19.dp, 18.dp)) {
        val s = 1.8.dp.toPx()
        val r = 2.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s, r); lineTo(size.width - s, r)
            quadraticBezierTo(size.width - s / 2, r, size.width - s / 2, r + r)
            lineTo(size.width - s / 2, size.height - 4.dp.toPx())
            lineTo(size.width - 5.dp.toPx(), size.height - s)
            lineTo(s, size.height - s)
            quadraticBezierTo(s / 2, size.height - s, s / 2, size.height - 4.dp.toPx())
            close()
        }
        drawPath(path, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun CameraFABIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        val s = 1.8f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawRoundRect(color = Color.White, topLeft = androidx.compose.ui.geometry.Offset(s, 7f), size = androidx.compose.ui.geometry.Size(size.width - 2 * s, size.height - 7f - s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = stroke)
        drawCircle(color = Color.White, radius = 3.5f, center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 + 1.5f), style = stroke)
        val lens = androidx.compose.ui.graphics.Path().apply {
            moveTo(8f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(16f, 7f)
        }
        drawPath(lens, color = Color.White, style = stroke)
    }
}

@Composable
private fun MoreDotsIcon() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Box(Modifier.size(3.4.dp).clip(CircleShape).background(ZadV3.gray400))
        }
    }
}

// ── Bottom capsule navigation ────────────────────────────────────────────────

private data class MoreEntry(val route: String, val labelRes: Int, val bg: Color, val fg: Color)

private val moreEntries = listOf(
    MoreEntry(V3Routes.SHOPPING, R.string.nav_shopping, Color(0xFFFCEEE3), Color(0xFFC2703D)),
    MoreEntry(V3Routes.FAMILY, R.string.nav_family, Color(0xFFF1EAFB), Color(0xFF7C3AED)),
    MoreEntry(V3Routes.BUDGET, R.string.screen_title_budget, Color(0xFFE8F1FC), Color(0xFF2563EB)),
    MoreEntry(V3Routes.SUBS, R.string.subscriptions_title, Color(0xFFE8F1FC), Color(0xFF2563EB)),
    MoreEntry(V3Routes.PHARMACY, R.string.nav_pharmacy, Color(0xFFFCE8ED), Color(0xFFDC5B4B)),
    MoreEntry(V3Routes.MAINTENANCE, R.string.nav_maintenance, Color(0xFFFDF3E1), Color(0xFFB45309)),
    MoreEntry(V3Routes.DEALS, R.string.screen_title_deals, Color(0xFFE3F5EC), Color(0xFF0B6B4E)),
    MoreEntry(V3Routes.PROFILE, R.string.screen_title_profile, Color(0xFFEEF0F3), Color(0xFF374151)),
)

/** Bottom sheet grid of MORE_ITEMS — staggered fadeUp cells, 2 columns */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3MoreSheet(
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            moreEntries.chunked(2).forEachIndexed { rowIdx, rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowEntries.forEachIndexed { colIdx, entry ->
                        val idx = rowIdx * 2 + colIdx
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fadeUpOnAppear(idx * 40L)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF9FAFB))
                                .pressableScale {
                                    onDismiss()
                                    onNavigate(entry.route)
                                }
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(entry.bg),
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(entry.fg.copy(alpha = 0.85f))) }
                            Text(stringResource(entry.labelRes), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun V3BottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 24.dp, shape = ZadV3.rPill, spotColor = Color(0x240F172A))
            .clip(ZadV3.rPill)
            .glassBlur(16f)
            .background(Color.White.copy(alpha = 0.72f))
            .border(0.5.dp, Color.Black.copy(alpha = 0.06f), ZadV3.rPill),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(
                selected = currentRoute == V3Routes.HOME,
                label = stringResource(R.string.nav_tab_home),
                icon = { HomeTabIcon(it) },
                onClick = { onNavigate(V3Routes.HOME) },
            )
            TabItem(
                selected = currentRoute == V3Routes.INVENTORY,
                label = stringResource(R.string.nav_inventory),
                icon = { InventoryTabIcon(it) },
                onClick = { onNavigate(V3Routes.INVENTORY) },
            )

            // Central raised dark-green camera/AI FAB → voice
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .offset(y = (-16).dp)
                    .shadow(elevation = 16.dp, shape = CircleShape, spotColor = Color(0x59064E3B))
                    .clip(CircleShape)
                    .background(ZadV3.green800)
                    .pressableScale(onClick = onOpenVoice),
                contentAlignment = Alignment.Center,
            ) { CameraFABIcon() }

            TabItem(
                selected = currentRoute == V3Routes.ASSISTANT,
                label = stringResource(R.string.screen_title_assistant),
                icon = { AssistantTabIcon(it) },
                onClick = { onNavigate(V3Routes.ASSISTANT) },
            )
            TabItem(
                selected = false,
                label = stringResource(R.string.nav_more),
                icon = { MoreDotsIcon() },
                onClick = { moreOpen = true },
            )
        }
    }

    if (moreOpen) {
        V3MoreSheet(onNavigate = onNavigate, onDismiss = { moreOpen = false })
    }
}

@Composable
private fun TabItem(
    selected: Boolean,
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    val tint = if (selected) ZadV3.green800 else ZadV3.gray400
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pressableScale(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        icon(tint)
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = tint,
        )
    }
}
