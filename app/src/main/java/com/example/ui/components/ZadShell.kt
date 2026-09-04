package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.*

/**
 * ZadShell — the app chrome from the Claude Design mockup
 * ("ZAD App - Standalone (offline).html"), in one place.
 *
 * The mockup draws chrome once and swaps only the body between screens: a sticky
 * header (menu + brand + screen title), a floating bottom nav pill with a raised
 * camera button, a "more" grid sheet, a camera sheet, and a side drawer. Before
 * this file every screen hand-rolled its own `TopAppBar` — twelve slightly
 * different headers, which is why the app never read as one design.
 *
 * Deliberate deviation from the mockup, the only one: the mockup's AR/EN toggle
 * pill is not reproduced. The mockup ships two hardcoded string tables; this app
 * has 814 Arabic strings and no English resource set, so an EN toggle would swap
 * the app into missing-resource crashes. That slot carries the notification bell
 * instead — same position, same pill shape, backed by a feature that exists.
 */

/*
 * `LocalBottomBarInset` اتشال (٢٠٢٦-٠٩-٠٢) — كان بيتحسب مرتين.
 *
 * MainScreen بيعمل `Box(Modifier.padding(innerPadding))` على محتوى الـNavHost، وSca­ffold
 * بيحط في `innerPadding` ارتفاع الـbottomBar كامل بالفعل. فالمساحة محجوزة خلاص قبل ما
 * أي شاشة تشوف حاجة. وبعدين نفس الرقم كان بيتنشر تاني كـCompositionLocal وكل شاشة
 * بتضيفه من جديد — يعني الأزرار العائمة في BudgetScreen/PharmacyScreen/
 * SubscriptionsScreen/MaintenanceScreen كانت بتطلع فوق بارتفاع شريط كامل زيادة
 * (~١٠٠dp)، وZadIntelligenceScreen/HomeScreen كان تحتيهم فراغ ميت بنفس القدر.
 *
 * المصدر الوحيد للحقيقة دلوقتي هو `innerPadding` بتاع Scaffold — لو ارتفاع
 * ZadBottomNavBar اتغيّر، Scaffold بيقيسه لوحده وكل الـ٢١ شاشة بتتظبط من غير ما
 * تعرف عنه حاجة. الشاشات بتضيف مسافتها الجمالية بس (16.dp) فوق كده.
 */

// ── screen routes the shell knows about ──────────────────────────────────────
object ZadRoutes {
    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val ASSISTANT = "assistant"
    const val SHOPPING = "shopping"
    const val BUDGET = "budget"
    const val SUBS = "subscriptions"
    const val FAMILY = "family"
    const val PHARMACY = "pharmacy"
    const val MAINTENANCE = "maintenance"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val TASBIHA = "tasbiha"
    const val CAMERA = "camera"
    const val STATEMENT = "statement_import"
    const val KNOWLEDGE_MAP = "knowledge_map"
    const val DEALS = "deals"
    const val PREMIUM_PLANS = "premium_plans"
}

/** The mockup's per-screen H1 (`STRINGS.titles`). */
@Composable
fun zadScreenTitle(route: String?): String = stringResource(
    when (route) {
        ZadRoutes.INVENTORY -> R.string.nav_inventory
        ZadRoutes.SHOPPING -> R.string.nav_shopping
        ZadRoutes.BUDGET -> R.string.screen_title_budget
        ZadRoutes.ASSISTANT -> R.string.screen_title_assistant
        ZadRoutes.FAMILY -> R.string.nav_family
        ZadRoutes.PHARMACY -> R.string.screen_title_pharmacy
        ZadRoutes.MAINTENANCE -> R.string.screen_title_maintenance
        ZadRoutes.PROFILE -> R.string.screen_title_profile
        ZadRoutes.NOTIFICATIONS -> R.string.notifications_title
        ZadRoutes.SUBS -> R.string.subscriptions_title
        ZadRoutes.TASBIHA -> R.string.tasbiha_short_label
        ZadRoutes.CAMERA -> R.string.camera_sheet_title
        ZadRoutes.STATEMENT -> R.string.statement_import_title
        ZadRoutes.KNOWLEDGE_MAP -> R.string.knowledge_map_title
        ZadRoutes.PREMIUM_PLANS -> R.string.premium_plans_title
        else -> R.string.screen_title_home
    }
)

// ── header ───────────────────────────────────────────────────────────────────

/**
 * Mockup header: `rgba(249,250,251,.92)` + 14px backdrop blur, hairline green
 * bottom rule, a 32dp menu square, the carrot tile + "ZAD" wordmark, the trailing
 * pill, then the 26sp screen title on its own line.
 */
@Composable
fun ZadTopHeader(
    title: String,
    modifier: Modifier = Modifier,
    kidsMode: Boolean = false,
    showRelockAction: Boolean = false,
    hasUnreadNotifications: Boolean = false,
    avatarUri: String? = null,
    onOpenDrawer: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onExitKidsMode: () -> Unit = {},
    onRelockKidsMode: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF9FAFB).copy(alpha = 0.92f))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.08f))
                    .clickable { onOpenDrawer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_more), tint = primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE6F4EC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_carrot_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.app_name),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            if (kidsMode) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.kids_mode_badge),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = kidsPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(kidsPrimary.copy(alpha = 0.10f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (kidsMode) {
                Text(
                    stringResource(R.string.kids_mode_exit),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = kidsPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(kidsPrimary.copy(alpha = 0.10f))
                        .clickable { onExitKidsMode() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            if (showRelockAction) {
                Text(
                    stringResource(R.string.kids_mode_relock),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = kidsPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(kidsPrimary.copy(alpha = 0.10f))
                        .clickable { onRelockKidsMode() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            // دايرة الأفاتار — نفس أسلوب ZadDrawerContent (كروب + fallback بحرف الاسم)، عشان
            // الصورة تتحدث في المكانين معاً لحظة ما ترفع (نفس StateFlow في الاتنين).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.12f))
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = avatarUri,
                        contentDescription = stringResource(R.string.tap_to_view_profile),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        placeholder = androidx.compose.ui.res.painterResource(id = R.drawable.avatar),
                        error = androidx.compose.ui.res.painterResource(id = R.drawable.avatar),
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = stringResource(R.string.tap_to_view_profile), tint = primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            // The mockup's trailing pill slot — bell instead of the AR/EN toggle.
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.08f))
                    .clickable { onNotificationsClick() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.notifications_title),
                    tint = primary,
                    modifier = Modifier.size(18.dp).bellShake(enabled = hasUnreadNotifications)
                )
                if (hasUnreadNotifications) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dangerColor)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions()
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(primary.copy(alpha = 0.06f)))
}

// ── bottom navigation ────────────────────────────────────────────────────────

private data class ZadNavItem(val route: String, val icon: ImageVector, val labelRes: Int)

/**
 * Bottom nav redesign — شيفو's vision (new ui ux/Gemini_Generated_Image):
 *
 * Floating glass capsule pill with 4 tabs: Home · Brain · Vault · Settings
 * + a raised central Mic/Voice orb with emerald pulse glow ring.
 *
 * Selected tab: spring-scale 1.12f + ExtraBold label + primary dot indicator.
 * All secondary screens (Inventory, Shopping, Family, Pharmacy…) are still
 * reachable from the existing "More" bottom sheet — nothing is removed.
 *
 * Kids mode collapses to Home + Family tabs only (no mic, no more button).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ZadBottomNavBar(
    currentRoute: String?,
    kidsMode: Boolean,
    onNavigate: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenVoice: () -> Unit = onOpenCamera,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(32.dp)

    // 4 primary tabs (adult mode)
    val adultItems = listOf(
        ZadNavItem(ZadRoutes.HOME,      Icons.Default.Home,       R.string.nav_tab_home),
        ZadNavItem(ZadRoutes.ASSISTANT, Icons.Default.Psychology,  R.string.screen_title_assistant),
        ZadNavItem(ZadRoutes.INVENTORY, Icons.Default.Inventory2,  R.string.nav_inventory),
        ZadNavItem("more",              Icons.Default.GridView,    R.string.nav_more),
    )
    val kidsItems = listOf(
        ZadNavItem(ZadRoutes.HOME,   Icons.Default.Home,          R.string.nav_tab_home),
        ZadNavItem(ZadRoutes.FAMILY, Icons.Default.FamilyRestroom, R.string.nav_family),
    )
    val items = if (kidsMode) kidsItems else adultItems

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 22.dp)
            .fillMaxWidth()
            .height(74.dp),          // slightly taller to accommodate raised mic
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── Glass pill ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .shadow(
                    elevation = 28.dp,
                    shape = pillShape,
                    ambientColor = Color(0xFF0F172A).copy(alpha = 0.10f),
                    spotColor  = Color(0xFF064E3B).copy(alpha = 0.18f)
                )
                .clip(pillShape)
        ) {
            // Backdrop blur layer
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zadGlassBlur(20.dp)
                    .background(Color.White.copy(alpha = 0.80f))
            )
            // Hairline border
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Color.Black.copy(alpha = 0.06f), pillShape)
            )

            // Tab row — splits evenly around the central mic slot
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (kidsMode) {
                    items.forEach { item ->
                        ZadNavTab(
                            icon = item.icon,
                            label = stringResource(item.labelRes),
                            selected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) }
                        )
                    }
                } else {
                    // Left 2 tabs
                    items.take(2).forEach { item ->
                        ZadNavTab(
                            icon = item.icon,
                            label = stringResource(item.labelRes),
                            selected = currentRoute == item.route,
                            onClick = { onNavigate(item.route) }
                        )
                    }
                    // Central mic placeholder (actual button floats above)
                    Spacer(Modifier.width(56.dp))
                    // Right 2 tabs
                    items.drop(2).forEach { item ->
                        val isSelected = if (item.route == "more") {
                            currentRoute !in listOf(ZadRoutes.HOME, ZadRoutes.ASSISTANT, ZadRoutes.INVENTORY)
                        } else {
                            currentRoute == item.route
                        }
                        ZadNavTab(
                            icon = item.icon,
                            label = stringResource(item.labelRes),
                            selected = isSelected,
                            onClick = {
                                if (item.route == "more") {
                                    onOpenMore()
                                } else {
                                    onNavigate(item.route)
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Central raised Mic orb (adult only) ───────────────────────────────
        if (!kidsMode) {
            ZadMicOrbButton(
                onClick = onOpenVoice,
                onLongClick = onOpenCamera,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/** Pulsing emerald mic orb — raised above the pill by ~10dp. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZadMicOrbButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micGlowAlpha"
    )
    val pulseRadius by pulseTransition.animateFloat(
        initialValue = 28f, targetValue = 34f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micGlowRadius"
    )

    Box(
        modifier = modifier.size(68.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing glow ring
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = pulseAlpha),
                            Color.Transparent
                        ),
                        radius = pulseRadius * 2f
                    ),
                    CircleShape
                )
        )
        // Orb button
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = primary.copy(alpha = 0.40f),
                    spotColor   = primary.copy(alpha = 0.55f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B6B4E),  // green700
                            Color(0xFF064E3B),  // green800
                            Color(0xFF052E16)   // green900
                        )
                    )
                )
                .pressableScale()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "زاد — المساعد الصوتي",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** Single nav tab — spring-scales on selection, shows dot indicator + ExtraBold label. */
@Composable
private fun ZadNavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "tabScale"
    )
    val tint   = if (selected) primary else textTertiary
    val weight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = weight,
            color = tint,
            maxLines = 1
        )
        // Selection dot indicator
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) primary else Color.Transparent)
        )
    }
}



// ── segmented tabs ───────────────────────────────────────────────────────────


/**
 * The mockup's segmented control (`segBtn`): a white pill track with a
 * dark-green pill on the selected segment.
 *
 * Scrolls horizontally when the labels don't fit, which is what lets screens
 * with more than the mockup's three tabs (Family has six) use the same control
 * instead of falling back to a Material `TabRow` whose underline indicator
 * disappears against the white track behind it.
 */
@Composable
fun ZadSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    scrollable: Boolean = tabs.size > 3,
) {
    val trackShape = RoundedCornerShape(999.dp)
    val track = modifier
        .fillMaxWidth()
        .zadCardShadow(trackShape)
        .clip(trackShape)
        .background(com.example.ui.theme.ZadLuxe.cardWhite)
        .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, trackShape)
        .padding(4.dp)

    @Composable
    fun segment(index: Int, title: String, segModifier: Modifier) {
        val isSelected = selectedIndex == index
        Box(
            modifier = segModifier
                .clip(trackShape)
                .background(if (isSelected) com.example.ui.theme.ZadLuxe.emerald else Color.Transparent)
                .clickable { onSelect(index) }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (scrollable) {
        Row(
            modifier = track.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { i, title -> segment(i, title, Modifier) }
        }
    } else {
        Row(modifier = track, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, title -> segment(i, title, Modifier.weight(1f)) }
        }
    }
}

// ── "more" sheet ─────────────────────────────────────────────────────────────

private data class ZadMoreEntry(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
    val bg: Color,
    val fg: Color,
)

/** Mockup `MORE_ITEMS`: an 8-cell 2-column grid of tinted icon tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadMoreSheet(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    val entries = listOf(
        ZadMoreEntry(ZadRoutes.SHOPPING, Icons.Default.ShoppingCart, R.string.nav_shopping, Color(0xFFFCEEE3), Color(0xFFC2703D)),
        ZadMoreEntry(ZadRoutes.FAMILY, Icons.Default.FamilyRestroom, R.string.nav_family, Color(0xFFF1EAFB), kidsPrimary),
        ZadMoreEntry(ZadRoutes.BUDGET, Icons.Default.BarChart, R.string.nav_budget, Color(0xFFE8F1FC), tertiary),
        ZadMoreEntry(ZadRoutes.SUBS, Icons.Default.CreditCard, R.string.subscriptions_title, Color(0xFFE8F1FC), tertiary),
        ZadMoreEntry(ZadRoutes.PHARMACY, Icons.Default.LocalPharmacy, R.string.nav_pharmacy, Color(0xFFFCE8ED), dangerColor),
        ZadMoreEntry(ZadRoutes.MAINTENANCE, Icons.Default.Build, R.string.nav_maintenance, Color(0xFFFDF3E1), secondaryDark),
        ZadMoreEntry(ZadRoutes.TASBIHA, Icons.Default.Yard, R.string.tasbiha_short_label, Color(0xFFE6F4EC), primary),
        ZadMoreEntry(ZadRoutes.KNOWLEDGE_MAP, Icons.Default.Hub, R.string.knowledge_map_title, Color(0xFFEAF2FB), Color(0xFF2563EB)),
        ZadMoreEntry(ZadRoutes.PREMIUM_PLANS, Icons.Default.Star, R.string.premium_plans_title, Color(0xFFFFF7ED), Color(0xFFD97706)),
        ZadMoreEntry(ZadRoutes.PROFILE, Icons.Default.Person, R.string.screen_title_profile, Color(0xFFEEF0F3), Color(0xFF374151)),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_more),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .pressableScale()
                                .clickable {
                                    onDismiss()
                                    onNavigate(entry.route)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(entry.bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(entry.icon, contentDescription = null, tint = entry.fg, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                stringResource(entry.labelRes),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── camera sheet ─────────────────────────────────────────────────────────────

/**
 * Mockup `renderCameraSheet`: title, a dark preview plate, then the two scan
 * intents. The mockup's plate is a placeholder; here the buttons carry the real
 * modes into `CameraScreen`, which owns the actual capture + Gemini vision call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadCameraSheet(
    onDismiss: () -> Unit,
    onScanInventory: () -> Unit,
    onScanReceipt: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.camera_sheet_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(com.example.ui.theme.ZadLuxe.squircle)
                    .background(com.example.ui.theme.ZadLuxe.emerald),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZadSheetButton(
                    text = stringResource(R.string.camera_scan_inventory),
                    container = com.example.ui.theme.ZadLuxe.emerald,
                    content = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onScanInventory
                )
                ZadSheetButton(
                    text = stringResource(R.string.camera_scan_receipt),
                    container = com.example.ui.theme.ZadLuxe.emerald.copy(alpha = 0.06f),
                    content = textPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = onScanReceipt
                )
            }
            Text(
                stringResource(R.string.cancel_action),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ZadSheetButton(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .pressableScale()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

// ── drawer ───────────────────────────────────────────────────────────────────

data class ZadDrawerEntry(val route: String, val icon: ImageVector, val labelRes: Int)

/** Mockup `DRAWER_ITEMS`, plus the profile footer row the app already had. */
val zadDrawerEntries = listOf(
    ZadDrawerEntry(ZadRoutes.HOME, Icons.Default.Home, R.string.nav_tab_home),
    ZadDrawerEntry(ZadRoutes.INVENTORY, Icons.Default.Inventory2, R.string.nav_inventory),
    ZadDrawerEntry(ZadRoutes.ASSISTANT, Icons.Default.Psychology, R.string.screen_title_assistant),
    ZadDrawerEntry(ZadRoutes.SUBS, Icons.Default.CreditCard, R.string.nav_subscriptions_installments),
    ZadDrawerEntry(ZadRoutes.SHOPPING, Icons.Default.ShoppingCart, R.string.nav_shopping),
    ZadDrawerEntry(ZadRoutes.FAMILY, Icons.Default.FamilyRestroom, R.string.nav_family),
    ZadDrawerEntry(ZadRoutes.BUDGET, Icons.Default.BarChart, R.string.nav_budget),
    ZadDrawerEntry(ZadRoutes.PHARMACY, Icons.Default.LocalPharmacy, R.string.nav_pharmacy),
    ZadDrawerEntry(ZadRoutes.MAINTENANCE, Icons.Default.Build, R.string.nav_maintenance),
    ZadDrawerEntry(ZadRoutes.DEALS, Icons.Default.LocationOn, R.string.nav_deals),
    ZadDrawerEntry(ZadRoutes.TASBIHA, Icons.Default.Park, R.string.tasbiha_short_label),
    ZadDrawerEntry(ZadRoutes.PROFILE, Icons.Default.Person, R.string.screen_title_profile),
    ZadDrawerEntry(ZadRoutes.NOTIFICATIONS, Icons.Default.Notifications, R.string.notifications_title),
)

@Composable
fun ZadDrawerContent(
    currentRoute: String?,
    entries: List<ZadDrawerEntry>,
    userName: String?,
    avatarUri: String?,
    onNavigate: (String) -> Unit,
    onProfileClick: () -> Unit,
    kidsMode: Boolean = false,
    onExitKidsMode: () -> Unit = {},
    showRelockAction: Boolean = false,
    onRelockKidsMode: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE6F4EC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_carrot_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primary)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.06f)))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            entries.forEach { entry ->
                val active = currentRoute == entry.route
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) primary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onNavigate(entry.route) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = if (active) primary else onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(entry.labelRes),
                        fontSize = 14.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (active) primary else textSecondary
                    )
                }
            }
            if (kidsMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onExitKidsMode() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.kids_mode_full_mode),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                }
            }
            if (showRelockAction) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRelockKidsMode() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.kids_mode_relock),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.06f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProfileClick() }
                .navigationBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.res.painterResource(id = R.drawable.avatar),
                        error = androidx.compose.ui.res.painterResource(id = R.drawable.avatar),
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        (userName ?: "Z").take(1).uppercase(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    userName ?: stringResource(R.string.default_user_name),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(stringResource(R.string.tap_to_view_profile), fontSize = 11.5.sp, color = textTertiary)
            }
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
