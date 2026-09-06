package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadInventory
import com.example.data.ZadPharmacyItem
import com.example.data.ZadSubscription
import com.example.data.ZadTransaction
import com.example.ui.theme.*

/**
 * ── 1. شريط الاختصارات ثلاثي الأبعاد المضيء (Luxury 3D Glassmorphic Shortcuts Rail) ──
 * تصميم فندقي ثلاثي الأبعاد مع طبقات إضاءة زجاجية وفيزياء حركة عائمة ولمعان ديناميكي (بدون إيموجيز رخيصة)
 */
data class LuxuryShortcut3D(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val glowColor: Color,
    val onClick: () -> Unit
)

@Composable
fun ZadHorizontalShortcutsRail(
    onNavigateToInventory: () -> Unit,
    onNavigateToShopping: () -> Unit,
    onNavigateToFamily: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToPharmacy: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToTasbiha: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts = remember {
        listOf(
            LuxuryShortcut3D(
                id = "inventory",
                title = "المخزون",
                subtitle = "تأمين الغذاء",
                icon = Icons.Default.Inventory2,
                gradientColors = listOf(Color(0xFF065F46), Color(0xFF059669), Color(0xFF34D399)),
                glowColor = Color(0xFF10B981),
                onClick = onNavigateToInventory
            ),
            LuxuryShortcut3D(
                id = "shopping",
                title = "التسوق",
                subtitle = "قائمة ذكية",
                icon = Icons.Default.ShoppingCart,
                gradientColors = listOf(Color(0xFF9A3412), Color(0xFFEA580C), Color(0xFFFDBA74)),
                glowColor = Color(0xFFF97316),
                onClick = onNavigateToShopping
            ),
            LuxuryShortcut3D(
                id = "family",
                title = "العائلة",
                subtitle = "عقل مشترك",
                icon = Icons.Default.FamilyRestroom,
                gradientColors = listOf(Color(0xFF1E40AF), Color(0xFF3B82F6), Color(0xFF93C5FD)),
                glowColor = Color(0xFF2563EB),
                onClick = onNavigateToFamily
            ),
            LuxuryShortcut3D(
                id = "subs",
                title = "الاشتراكات",
                subtitle = "VIP وفواتير",
                icon = Icons.Default.CreditCard,
                gradientColors = listOf(Color(0xFF5B21B6), Color(0xFF8B5CF6), Color(0xFFDDD6FE)),
                glowColor = Color(0xFF7C3AED),
                onClick = onNavigateToSubscriptions
            ),
            LuxuryShortcut3D(
                id = "pharmacy",
                title = "الصيدلية",
                subtitle = "جرعات الأسرة",
                icon = Icons.Default.LocalPharmacy,
                gradientColors = listOf(Color(0xFF991B1B), Color(0xFFEF4444), Color(0xFFFCA5A5)),
                glowColor = Color(0xFFDC2626),
                onClick = onNavigateToPharmacy
            ),
            LuxuryShortcut3D(
                id = "maint",
                title = "الصيانة",
                subtitle = "الضمان والمنزل",
                icon = Icons.Default.Build,
                gradientColors = listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFFCBD5E1)),
                glowColor = Color(0xFF475569),
                onClick = onNavigateToMaintenance
            ),
            LuxuryShortcut3D(
                id = "tasbiha",
                title = "التسبيح",
                subtitle = "شجرة البركة",
                icon = Icons.Default.Park,
                gradientColors = listOf(Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF6EE7B7)),
                glowColor = Color(0xFF059669),
                onClick = onNavigateToTasbiha
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "shortcuts3DFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobbing"
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    ) {
        items(shortcuts, key = { it.id }) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .pressableScale()
                    .clickable { item.onClick() }
                    .width(72.dp)
            ) {
                // 3D Metallic Glass Capsule with ambient lighting & specular sheen
                Box(
                    modifier = Modifier
                        .offset(y = (floatOffset * 0.7f).dp)
                        .size(58.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = item.glowColor.copy(alpha = 0.55f),
                            ambientColor = item.glowColor.copy(alpha = 0.25f)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = item.gradientColors
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.65f),
                                    Color.White.copy(alpha = 0.12f),
                                    item.glowColor.copy(alpha = 0.3f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Specular light reflection on top edge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // 3D Vector Icon with drop shadow
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .shadow(elevation = 3.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.4f))
                    )
                }

                // Title Label
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Subtitle Badge
                Text(
                    text = item.subtitle,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * ── قاموس إيموجي الأغذية الغني والشامل لجميع أصناف المطبخ والمخزون العربي والخليجي ──
 */
fun resolveFoodEmoji(name: String): String {
    val n = name.trim().lowercase()
    return when {
        n.contains("موز") || n.contains("banana") -> "🍌"
        n.contains("تفاح") || n.contains("apple") -> "🍎"
        n.contains("برتقال") || n.contains("يوسفي") || n.contains("orange") -> "🍊"
        n.contains("فراول") || n.contains("strawberr") -> "🍓"
        n.contains("عنب") || n.contains("grape") -> "🍇"
        n.contains("بطيخ") || n.contains("شمام") || n.contains("melon") -> "🍉"
        n.contains("تمر") || n.contains("بلح") || n.contains("رطب") || n.contains("date") -> "🌴"
        n.contains("ليمون") || n.contains("lemon") -> "🍋"
        n.contains("طماطم") || n.contains("بندورة") || n.contains("tomato") -> "🍅"
        n.contains("بطاطس") || n.contains("بطاطا") || n.contains("potato") -> "🥔"
        n.contains("بصل") || n.contains("onion") -> "🧅"
        n.contains("ثوم") || n.contains("garlic") -> "🧄"
        n.contains("خيار") || n.contains("cucumber") -> "🥒"
        n.contains("جزر") || n.contains("carrot") -> "🥕"
        n.contains("خس") || n.contains("سلطة") || n.contains("جرجير") || n.contains("salad") -> "🥬"
        n.contains("فلفل") || n.contains("شطة") || n.contains("pepper") -> "🫑"
        n.contains("أرز") || n.contains("رز") || n.contains("عيش") || n.contains("rice") -> "🍚"
        n.contains("دجاج") || n.contains("فراخ") || n.contains("شاورما") || n.contains("chicken") -> "🍗"
        n.contains("لحم") || n.contains("كفتة") || n.contains("برجر") || n.contains("ستيك") || n.contains("meat") || n.contains("beef") -> "🥩"
        n.contains("سمك") || n.contains("تونة") || n.contains("جمبري") || n.contains("سالمون") || n.contains("fish") || n.contains("tuna") -> "🐟"
        n.contains("بيض") || n.contains("egg") -> "🥚"
        n.contains("حليب") || n.contains("لبن") || n.contains("milk") -> "🥛"
        n.contains("زبادي") || n.contains("لبنة") || n.contains("روب") || n.contains("yogurt") -> "🥣"
        n.contains("جبن") || n.contains("جبنة") || n.contains("قشطة") || n.contains("cheese") -> "🧀"
        n.contains("زبدة") || n.contains("سمن") || n.contains("butter") -> "🧈"
        n.contains("خبز") || n.contains("توست") || n.contains("صامولي") || n.contains("فينو") || n.contains("فطير") || n.contains("bread") -> "🍞"
        n.contains("مكرونة") || n.contains("معكرونة") || n.contains("باستا") || n.contains("نودلز") || n.contains("اندومي") || n.contains("pasta") || n.contains("noodle") -> "🍝"
        n.contains("زيت") || n.contains("زيتون") || n.contains("oil") || n.contains("olive") -> "🫒"
        n.contains("سكر") || n.contains("sugar") -> "🧂"
        n.contains("ملح") || n.contains("بهار") || n.contains("salt") -> "🧂"
        n.contains("شاي") || n.contains("كرك") || n.contains("tea") -> "🫖"
        n.contains("قهوة") || n.contains("بن") || n.contains("نسكافيه") || n.contains("اسبريسو") || n.contains("coffee") -> "☕"
        n.contains("عصير") || n.contains("juice") -> "🧃"
        n.contains("ماء") || n.contains("مياه") || n.contains("water") -> "💧"
        n.contains("مايونيز") || n.contains("mayo") -> "🥫"
        n.contains("كاتشب") || n.contains("صلصة") || n.contains("طحينة") || n.contains("sauce") -> "🥫"
        n.contains("شيبس") || n.contains("شيبسي") || n.contains("chips") -> "🍟"
        n.contains("شوكولات") || n.contains("نوتيلا") || n.contains("كيك") || n.contains("chocolate") -> "🍫"
        n.contains("بسكويت") || n.contains("كوكيز") || n.contains("cookie") -> "🍪"
        n.contains("صابون") || n.contains("مسحوق") || n.contains("شامبو") || n.contains("كلور") || n.contains("تايد") || n.contains("soap") -> "🧼"
        n.contains("مناديل") || n.contains("فاين") || n.contains("tissue") -> "🧻"
        n.contains("بنزين") || n.contains("وقود") || n.contains("fuel") -> "⛽"
        n.contains("دواء") || n.contains("علاج") || n.contains("مسكن") || n.contains("بنادول") || n.contains("panadol") -> "💊"
        else -> "🍽️"
    }
}

/**
 * ── 2. إيدج صحة المخزون والنواقص (Food Inventory Health & Shortages) ──
 * شريط تمرير أفقي كروت بيضاء بزوايا 18dp وأيقونات الأغذية الحية ومؤشر الأيام
 */
data class FoodItemSample(
    val name: String,
    val emoji: String,
    val category: String,
    val daysLeft: Int,
    val isLow: Boolean,
    val catBg: Color,
    val rawItem: ZadInventory? = null
)

@Composable
fun ZadFoodShortagesGlanceCard(
    inventory: List<ZadInventory>,
    onViewAllClick: () -> Unit,
    onConfirmItem: (ZadInventory) -> Unit = {},
    onAddToShoppingList: (ZadInventory) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // البالتة والتينت المحايد بيتقروا برّه الـ remember: دول `@Composable get()`
    // ومينفعش يتنادوا جوه لامبدا مش كومبوزابل. وبيدخلوا كمفاتيح عشان اللستة
    // تتحسب من أول وجديد لما الثيم يتبدّل.
    val categoryPalette = chartCategorical
    val neutralTint = outlineVariant
    val sampleItems = remember(inventory, categoryPalette, neutralTint) {
        if (inventory.isNotEmpty()) {
            inventory.map {
                val catBg = when (it.category) {
                    "خضار" -> categoryPalette[0].copy(alpha = 0.14f)
                    "ألبان", "مشروبات" -> categoryPalette[1].copy(alpha = 0.14f)
                    "مخبوزات" -> categoryPalette[2].copy(alpha = 0.14f)
                    "فاكهة", "لحوم" -> categoryPalette[3].copy(alpha = 0.14f)
                    else -> neutralTint
                }
                FoodItemSample(
                    name = it.itemName,
                    emoji = resolveFoodEmoji(it.itemName),
                    category = it.category ?: "عام",
                    daysLeft = it.quantity.toInt().coerceAtLeast(1),
                    isLow = it.quantity <= 2,
                    catBg = catBg,
                    rawItem = it
                )
            }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(stringResource(R.string.glance_inventory_health_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                    Text(stringResource(R.string.glance_inventory_health_sub), fontSize = 11.5.sp, color = textSecondary)
                }
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.glance_open_inventory), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = primary)
            }
        }

        // شريط تمرير أفقي انسيابي للنواقص والأصناف (Horizontal Scrollable Rail)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(sampleItems) { item ->
                FoodGlanceTile(
                    item = item,
                    modifier = Modifier.width(155.dp),
                    onConfirm = { item.rawItem?.let { onConfirmItem(it) } },
                    onAddToCart = { item.rawItem?.let { onAddToShoppingList(it) } }
                )
            }
        }
    }
}

@Composable
private fun FoodGlanceTile(
    item: FoodItemSample,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToCart: () -> Unit = {}
) {
    val barColor = if (item.isLow) dangerColor else if (item.daysLeft <= 4) warningColor else primary
    val progress = (item.daysLeft / 10f).coerceIn(0.1f, 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceContainerLow)
            .border(1.dp, outlineVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(item.catBg),
                contentAlignment = Alignment.Center
            ) {
                Text(item.emoji, fontSize = 20.sp)
            }
            if (item.isLow) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(dangerColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.glance_expiring_soon), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dangerColor)
                }
            }
        }

        Text(
            text = item.name,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }

        Text(
            text = "متبقي ${item.daysLeft} أيام",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textSecondary
        )

        if (item.isLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.1f))
                    .clickable { onAddToCart() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = primary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.glance_add_to_cart), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = primary)
            }
        }
    }
}

/**
 * ── 3. إيدج الاشتراكات الشهرية (Subscriptions Quick Glance) ──
 * خطوط ملونة جانبية (--accent)، إجمالي الاشتراك، وتاريخ التجديد
 */
@Composable
fun ZadSubscriptionsGlanceCard(
    subscriptions: List<ZadSubscription>,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive } }
    val totalAmount = remember(activeSubs) { activeSubs.sumOf { it.amount } }

    // المبلغ بيفضل Double لحد ما يترسم — CurrencyFormatter.format() بتقرا
    // MarketPrefs.currentMarket (Compose state)، فلو ناديناها جوّه remember كان النص
    // هيتجمّد على عملة أول تركيب ومايتغيّرش لما المستخدم يبدّل السوق.
    val subAccentWarning = warningColor
    val subAccentDanger = dangerColor
    val subAccentPrimary = primary
    val displayList = remember(activeSubs, subAccentWarning, subAccentDanger, subAccentPrimary) {
        activeSubs.take(3).map {
            Triple(it.title, it.amount, when (it.title) {
                "نتفليكس", "Netflix" -> subAccentWarning
                "الجيم", "Gym" -> subAccentDanger
                else -> subAccentPrimary
            })
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(infoColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = infoColor, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(stringResource(R.string.glance_monthly_subscriptions), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                    Text(
                        text = if (activeSubs.isEmpty()) "مفيش اشتراكات مسجّلة"
                               else "إجمالي شهري: ${com.example.data.CurrencyFormatter.format(context, totalAmount)}",
                        fontSize = 11.5.sp,
                        color = textSecondary
                    )
                }
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.glance_view_all), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = infoColor)
            }
        }

        if (displayList.isEmpty()) {
            // كان هنا ٣ اشتراكات مخترعة (نتفليكس/STC TV/الجيم بالريال) بتتعرض لأي حساب
            // فاضي — المستخدم كان بيشوف اشتراكات ما سجّلهاش وبعملة مش بتاعته. الحالة
            // الفاضية الصح هي دي: بنقول مفيش، وبندي طريق للإضافة.
            ZadEmptyState(
                icon = Icons.Default.CreditCard,
                title = "مفيش اشتراكات لسه",
                subtitle = "ضيف اشتراكاتك الشهرية عشان زاد يحسبها في المتاح ويفكّرك بمواعيد التجديد",
                iconTint = infoColor.copy(alpha = 0.55f),
                iconBackground = infoColor.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displayList.forEach { (name, amount, accentColor) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor)
                        )
                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    }
                    Text(
                        com.example.data.CurrencyFormatter.format(context, amount),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary
                    )
                }
            }
        }
        }
    }
}

/**
 * ── 4. إيدج الصيدلية والجرعات (Pharmacy & Doses Quick Glance) ──
 * الأدوية النشطة ونسبة الالتزام الحقيقية.
 *
 * [adherencePercent] بييجي من ZadViewModel.weeklyAdherencePercent، وهو `Int?` عن قصد:
 * null معناها "مفيش جرعات مسجّلة كفاية آخر ٧ أيام عشان نحسب نسبة" — مش صفر ومش رقم
 * مخمّن. كان مكتوب هنا "91% • منتظم" ثابت في الكود لأي حساب، حتى الحساب اللي مفيهوش
 * ولا دواء واحد.
 */
@Composable
fun ZadPharmacyGlanceCard(
    pharmacyItems: List<ZadPharmacyItem>,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    adherencePercent: Int? = null,
    nextDoseItem: ZadPharmacyItem? = null,
    nextDoseTime: String? = null,
    onTakeNextDose: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(dangerColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = dangerColor, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(stringResource(R.string.glance_family_pharmacy_title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                    val adherenceLabel = when {
                        pharmacyItems.isEmpty() -> "مفيش أدوية مسجّلة"
                        adherencePercent != null -> "الالتزام بالجرعات: $adherencePercent% • " +
                            if (adherencePercent >= 80) "منتظم" else "محتاج انتباه"
                        else -> "${pharmacyItems.size} دواء نشط • لسه بنجمّع بيانات الالتزام"
                    }
                    Text(
                        text = adherenceLabel,
                        fontSize = 11.5.sp,
                        // رمادي محايد لما مفيش بيانات — الأخضر بيقرا كأنه حالة كويسة،
                        // و"مفيش أدوية مسجّلة" مش حالة كويسة ولا وحشة، دي غياب بيانات.
                        color = when {
                            pharmacyItems.isEmpty() -> textSecondary
                            adherencePercent == null -> textSecondary
                            adherencePercent < 80 -> warningColor
                            else -> primary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.glance_open_pharmacy), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = dangerColor)
            }
        }

        // الجرعة التالية — أقرب موعد حقيقي من doseTimes، بزر تسجيل مباشر
        if (nextDoseItem != null && nextDoseTime != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(dangerColor.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.glance_next_dose), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dangerColor)
                    Text("${nextDoseItem.name} • $nextDoseTime", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(dangerColor)
                        .clickable { onTakeNextDose() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = onError, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.glance_dose_taken), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = onError)
                }
            }
        }

        // Medicines Pill Rows — الجرعة الحقيقية من الصف نفسه. كان مكتوب "جرعة منتظمة"
        // لكل دواء مهما كانت جرعته، وللحسابات الفاضية كان بيخترع ٣ أدوية
        // (باراسيتامول/فيتامين د/أنسولين) — أخطر نوع بيانات وهمية في التطبيق، لأنها طبية.
        val medAccentWarning = warningColor
        val medAccentPrimary = primary
        val meds = remember(pharmacyItems, medAccentWarning, medAccentPrimary) {
            pharmacyItems.take(3).map { item ->
                val note = item.dosage?.takeIf { it.isNotBlank() }
                    ?: item.daysOfSupplyLeft()?.let { "يكفي $it يوم" }
                    ?: "${item.dailyDoseCount} جرعة يومياً"
                Triple(item.name, note, if (item.isLowStock()) medAccentWarning else medAccentPrimary)
            }
        }

        if (meds.isEmpty()) {
            ZadEmptyState(
                icon = Icons.Default.LocalPharmacy,
                title = "الصيدلية فاضية",
                subtitle = "صوّر شريط الدواء أو ضيفه يدوي، وزاد هيفكّرك بمواعيد الجرعات وينبّهك قبل ما يخلص",
                iconTint = dangerColor.copy(alpha = 0.55f),
                iconBackground = dangerColor.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meds.forEach { (name, note, statusColor) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    }
                    Text(note, fontSize = 11.5.sp, color = textSecondary)
                }
            }
        }
        }
    }
}

/**
 * ── 5. إيدج زاد بريميوم الجذاب بدون إعلانات (Zad Premium Promo Hero Card) ──
 * مصمم لجذب العميل لترقية حسابه بدون إعلانات + ذكاء اصطناعي غير محدود + دفع مباشر
 */
@Composable
fun ZadPremiumPromoCard(
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "premiumGlow")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF052E16),
                        Color(0xFF0B6B4E),
                        Color(0xFF064E3B)
                    )
                )
            )
            .border(1.5.dp, Color(0xFF34D399).copy(alpha = shimmerAlpha), RoundedCornerShape(24.dp))
            .clickable { onUpgradeClick() }
            .padding(18.dp)
            .pressableScale()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF4A93B).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👑", fontSize = 20.sp)
                    }
                    Column {
                        Text(
                            text = "زاد بريميوم (بدون إعلانات)",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "ذكاء اصطناعي فوري + مزامنة عائلية كاملة",
                            fontSize = 11.5.sp,
                            color = Color(0xFF6EE7B7)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color(0xFFF4A93B))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ترقية VIP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF052E16)
                    )
                }
            }

            // Bullet points
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    "🚫 بلا إعلانات",
                    "🧠 شات AI بلا حدود",
                    "⚡ رصد بنكي لحظي"
                ).forEach { perk ->
                    Text(
                        text = perk,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD9F2E6)
                    )
                }
            }

            // CTA Button
            Button(
                onClick = onUpgradeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34D399),
                    contentColor = Color(0xFF052E16)
                )
            ) {
                Text(
                    text = "اشترك الآن واستمتع بتجربة بلا إعلانات ←",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * ── بيانات عقد الفضاء ثلاثي الأبعاد ──
 */
data class Node3D(
    val id: String,
    val title: String,
    val emoji: String,
    val color: Color,
    val initialX: Float,
    val initialY: Float,
    val initialZ: Float,
    val layerName: String,
    val statusText: String
)

/**
 * ── 6. العقل الثاني: الكرة العصبية المجسمة ثلاثية الأبعاد (3D Holographic Neural Sphere Widget) ──
 * مستوحى من نظام (Second Brain: Intellectual OS)
 * يدعم الدوران الحر ثلاثي الأبعاد والتقريب والإبعاد (Pinch-to-zoom) وجزيئات الطاقة المضيئة ولوحات الـ HUD
 */
@Composable
fun Zad3DNeuralSphereWidget(
    stats: com.example.data.ZadBrainStats? = null,
    // بند 35.1 (تكملة) — الـ9 عقد بقيت بتتبنى من داتا حقيقية في ZadIntelligenceScreen
    // (الشاشة عندها كل الـStateFlows اللازمة). null = fallback للنص القديم، مش المفروض
    // يحصل من الاستدعاء الوحيد الحالي بس بيمنع كسر أي كولر تاني/معاينة مستقبلية.
    nodes: List<Node3D>? = null,
    onNodeClick: (String) -> Unit = {},
    onViewFullMapClick: () -> Unit = {},
    onOpenDossierClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var yawAngle by remember { mutableFloatStateOf(0f) }
    var pitchAngle by remember { mutableFloatStateOf(15f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    // حلقة نبض الطاقة وجزيئات الفوتونات
    val infiniteTransition = rememberInfiniteTransition(label = "sphere3D")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "photonFlow"
    )
    val coreGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreGlow"
    )

    // عقد الفضاء ثلاثي الأبعاد الموزعة في طبقات كروية هندسية — fallback لو مفيش nodes حقيقية اتبعتت
    val nodes3D = nodes ?: remember {
        listOf(
            Node3D("budget", "المصاريف والتدفق", "💳", Color(0xFF0F9B76), -0.72f, -0.45f, 0.45f, "ROOT - FINANCIAL", "معدل الصرف اليومي: آمن ومستقر"),
            Node3D("family", "عقل العائلة", "👨‍👩‍👧‍👦", Color(0xFF2563EB), 0.75f, -0.42f, 0.40f, "AREAS - FAMILY", "مزامنة نشطة • 4 أفراد"),
            Node3D("inventory", "المخزون وتأمين الغذاء", "📦", Color(0xFFF59E0B), 0.85f, 0.15f, -0.35f, "PROJECTS - PANTRY", "كفاية المخزون: 18 يوماً"),
            Node3D("pharmacy", "صيدلية الأسرة", "💊", Color(0xFFDC2626), -0.80f, 0.20f, -0.38f, "KNOWLEDGE - HEALTH", "الالتزام الدوائي: 91%"),
            Node3D("subs", "الاشتراكات والفواتير", "⚡", Color(0xFF8B5CF6), 0.05f, -0.85f, 0.25f, "ROOT - COMMITMENTS", "4 اشتراكات نشطة"),
            Node3D("chef", "شيف زاد الذكي", "🍲", Color(0xFF10B981), -0.55f, 0.65f, 0.35f, "RESOURCES - NUTRITION", "جاهز لـ 12 وصفة فورية"),
            Node3D("maintenance", "الصيانة والضمانات", "🔧", Color(0xFF64748B), 0.50f, 0.68f, 0.38f, "PROJECTS - HOME", "3 أجهزة تحت الضمان"),
            Node3D("forecast", "التنبؤات السلوكية", "🔮", Color(0xFFEC4899), 0.10f, 0.82f, -0.42f, "AI PREDICTIVE", "دقة التوقع: 94.2%"),
            Node3D("tasbiha", "بستان التسبيح والبركة", "🌿", Color(0xFF34D399), 0.0f, -0.30f, -0.85f, "SPIRITUAL - ZAD", "مستمر يومياً • نمو الشجرة")
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030A08),
                        Color(0xFF061A14),
                        Color(0xFF020705)
                    )
                )
            )
            .border(1.2.dp, Color(0xFF0F9B76).copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0xFF0F9B76).copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── HUD Header Telemetry ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34D399))
                    )
                    Text(
                        text = "العقل الثاني: نظام تشغيل فكري ثلاثي الأبعاد",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                Text(
                    text = "SECOND BRAIN: ACTIVE NEURAL PROCESSING",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6EE7B7).copy(alpha = 0.8f),
                    letterSpacing = 0.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color(0xFF0F9B76).copy(alpha = 0.25f))
                    .border(1.dp, Color(0xFF34D399).copy(alpha = 0.4f), RoundedCornerShape(9999.dp))
                    .clickable { onOpenDossierClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "📄 التقرير المطبوع",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD9F2E6)
                )
            }
        }

        // ── HUD Telemetry Badges Row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // بند 35.1 — أرقام حقيقية من zad_brain_stats بدل القيم المكتوبة يدويًا. growth/
            // accuracy بيتعرضوا "لسه مبكّر" لو الـRPC رجعت null (مش رقم مخترع زي +196%/96%
            // القدام) — مفيش بيانات كفاية للمقارنة لسه، ودي حالة حقيقية مش نقص بيانات نعرضه كصفر.
            listOf(
                Triple("العقد النشطة", stats?.activeNodes?.toString() ?: "—", Color(0xFF38BDF8)),
                Triple("الروابط العصبية", stats?.neuralLinks?.toString() ?: "—", Color(0xFFFBBF24)),
                Triple("معدل النمو", stats?.growthPct?.let { "${if (it >= 0) "+" else ""}${it}%" } ?: "لسه مبكّر", Color(0xFF34D399)),
                Triple("دقة التنبؤ", stats?.predictionAccuracyPct?.let { "$it%" } ?: "لسه مبكّر", Color(0xFFA78BFA))
            ).forEach { (title, count, badgeColor) ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    Text(count, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = badgeColor)
                }
            }
        }

        // ── 3D Holographic Sphere Canvas ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF010604).copy(alpha = 0.7f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        yawAngle += pan.x * 0.45f
                        pitchAngle = (pitchAngle - pan.y * 0.45f).coerceIn(-65f, 65f)
                        zoomScale = (zoomScale * zoom).coerceIn(0.7f, 2.2f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2, h / 2)
                val baseRadius = (minOf(w, h) / 2) * 0.78f * zoomScale
                val fovDistance = 2.4f

                val yawRad = Math.toRadians(yawAngle.toDouble())
                val pitchRad = Math.toRadians(pitchAngle.toDouble())

                // دالة إسقاط ثلاثي الأبعاد
                fun project3D(x: Float, y: Float, z: Float): Triple<Offset, Float, Float> {
                    // دوران Yaw حول المحور Y
                    val x1 = (x * kotlin.math.cos(yawRad) - z * kotlin.math.sin(yawRad)).toFloat()
                    val z1 = (x * kotlin.math.sin(yawRad) + z * kotlin.math.cos(yawRad)).toFloat()

                    // دوران Pitch حول المحور X
                    val y2 = (y * kotlin.math.cos(pitchRad) - z1 * kotlin.math.sin(pitchRad)).toFloat()
                    val z2 = (y * kotlin.math.sin(pitchRad) + z1 * kotlin.math.cos(pitchRad)).toFloat()

                    // المنظور المنظوري
                    val perspective = fovDistance / (fovDistance + z2).coerceAtLeast(0.1f)
                    val projX = center.x + x1 * baseRadius * perspective
                    val projY = center.y + y2 * baseRadius * perspective

                    return Triple(Offset(projX, projY), perspective, z2)
                }

                // 1. رسم الدائرة الزجاجية الخارجية للهولوجرام
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F9B76).copy(alpha = 0.08f),
                            Color(0xFF064E3B).copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.15f
                    ),
                    radius = baseRadius * 1.12f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF34D399).copy(alpha = 0.22f),
                    radius = baseRadius * 1.05f,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f))
                )

                // 2. رسم الطبقات الدائرية الأفقية المتداخلة (Holographic Layer Planes)
                listOf(-0.6f, -0.2f, 0.2f, 0.6f).forEach { layerY ->
                    val layerCenterProj = project3D(0f, layerY, 0f)
                    val layerRadius = kotlin.math.sqrt((1f - layerY * layerY).coerceAtLeast(0f)) * baseRadius * layerCenterProj.second
                    if (layerRadius > 4f) {
                        drawOval(
                            color = Color(0xFF6EE7B7).copy(alpha = (0.12f * layerCenterProj.second).coerceIn(0.04f, 0.22f)),
                            topLeft = Offset(layerCenterProj.first.x - layerRadius, layerCenterProj.first.y - (layerRadius * 0.25f)),
                            size = androidx.compose.ui.geometry.Size(layerRadius * 2, layerRadius * 0.5f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // 3. حساب مواقع جميع العقد وفرزها حسب العمق (Depth Sorting)
                val projectedNodes = nodes3D.map { node ->
                    val proj = project3D(node.initialX, node.initialY, node.initialZ)
                    Triple(node, proj.first, proj.third) // node, 2D pos, zDepth
                }.sortedBy { it.third } // فرز من الخلف للأمام

                val centerProj = project3D(0f, 0f, 0f)

                // 4. رسم الخطوط العصبية والفوتونات المتدفقة
                projectedNodes.forEach { (node, pos, zDepth) ->
                    val isFront = zDepth > 0f
                    val lineAlpha = if (isFront) 0.45f else 0.16f

                    // خط عصبي ثلاثي الأبعاد
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF34D399).copy(alpha = lineAlpha),
                                node.color.copy(alpha = lineAlpha)
                            ),
                            start = centerProj.first,
                            end = pos
                        ),
                        start = centerProj.first,
                        end = pos,
                        strokeWidth = if (node.id == selectedNodeId) 2.5.dp.toPx() else 1.2.dp.toPx()
                    )

                    // فوتون طاقة متحرك (Luminous Traveling Particle)
                    val flowPos = Offset(
                        x = centerProj.first.x + (pos.x - centerProj.first.x) * pulseProgress,
                        y = centerProj.first.y + (pos.y - centerProj.first.y) * pulseProgress
                    )
                    drawCircle(
                        color = Color(0xFF6EE7B7).copy(alpha = if (isFront) 0.9f else 0.4f),
                        radius = 2.5.dp.toPx(),
                        center = flowPos
                    )
                }

                // 5. رسم العقل المركزي في المنتصف (Core Brain Hologram)
                drawCircle(
                    color = Color(0xFF0F9B76).copy(alpha = coreGlow * 0.4f),
                    radius = 28.dp.toPx() * zoomScale,
                    center = centerProj.first
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF34D399), Color(0xFF0F9B76), Color(0xFF052E16))
                    ),
                    radius = 16.dp.toPx() * zoomScale,
                    center = centerProj.first
                )
                drawCircle(
                    color = Color(0xFFD9F2E6),
                    radius = 5.dp.toPx() * zoomScale,
                    center = centerProj.first
                )

                // 6. رسم العقد ثلاثية الأبعاد بالألوان والإيموجي
                projectedNodes.forEach { (node, pos, zDepth) ->
                    val isFront = zDepth > -0.1f
                    val nodeRadius = (16.dp.toPx() * (1f + zDepth * 0.4f).coerceIn(0.55f, 1.4f) * zoomScale)
                    val isSelected = node.id == selectedNodeId

                    // هالة التوهج للعقدة
                    drawCircle(
                        color = node.color.copy(alpha = if (isSelected) 0.65f else if (isFront) 0.35f else 0.12f),
                        radius = nodeRadius * 1.5f,
                        center = pos
                    )
                    // خلفية العقدة
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isSelected) Color.White else node.color,
                                Color(0xFF0A1813)
                            )
                        ),
                        radius = nodeRadius,
                        center = pos
                    )
                    drawCircle(
                        color = if (isSelected) Color.White else Color(0xFFE2E8F0).copy(alpha = if (isFront) 0.8f else 0.3f),
                        radius = nodeRadius,
                        center = pos,
                        style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
                    )
                }
            }

            // تلميح التحكم باللمس
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "🌐 اسحب للتدوير 3D • باعد أصابعك للتقريب والإبعاد",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Node Chips Carousel & Quick Actions ──
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(nodes3D) { node ->
                val isSelected = node.id == selectedNodeId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) node.color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (isSelected) node.color else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selectedNodeId = if (selectedNodeId == node.id) null else node.id
                            onNodeClick(node.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(node.emoji, fontSize = 13.sp)
                        Text(
                            text = node.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFFD1D5DB)
                        )
                    }
                }
            }
        }
    }
}

/**
 * ── 7. التقرير الاستراتيجي الشامل المطبوع بختم زاد الرسمي (Zad Executive Intelligence Dossier) ──
 * تقرير تحليلي شامل وسلوكي وتنبؤي عائلي قابل للتصدير والمشاركة والطباعة المباشرة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZadExecutiveDossierSheet(
    onDismiss: () -> Unit,
    totalSpent: Double = 0.0,
    // null = "لسه مفيش بيانات كفاية للحساب"، مش صفر ومش رقم افتراضي. الافتراضيات
    // القديمة (3250/145/4100/4 أفراد) كانت أرقام عرض تقديمي سايبة في كود الإنتاج.
    safeDailySpend: Double? = null,
    forecastNextMonth: Double? = null,
    familyMembersCount: Int = 1,
    pharmacyAdherencePct: Int? = null,
    lowStockItemCount: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentDate = remember { java.time.LocalDate.now().toString() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Dossier Header ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF052E16), Color(0xFF0F9B76))
                        )
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("👑", fontSize = 28.sp)
                Text(
                    text = "التقرير الاستراتيجي الشامل لعقل زاد",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "ZAD EXECUTIVE FAMILY & BEHAVIORAL INTELLIGENCE DOSSIER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6EE7B7),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "تاريخ الإصدار: $currentDate",
                    fontSize = 11.sp,
                    color = Color(0xFFD9F2E6).copy(alpha = 0.85f)
                )
            }

            // ── Section 1: الميزانية والتنبؤات المستقبلية ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔮", fontSize = 18.sp)
                    Text(stringResource(R.string.report_section_forecast), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                }
                Text(
                    text = "• إجمالي الصرف الفعلي للدورة الحالية: ${com.example.data.CurrencyFormatter.format(context, totalSpent)}.\n" +
                           (safeDailySpend?.let { "• معدل الصرف اليومي الآمن الموصى به: ${com.example.data.CurrencyFormatter.format(context, it)}/يوم.\n" }
                               ?: "• معدل الصرف اليومي الآمن: محتاج ميزانية ورصيد محدّدين عشان يتحسب.\n") +
                           (forecastNextMonth?.let { "• التكلفة التقديرية للشهر القادم بناءً على الذكاء الاصطناعي: ${com.example.data.CurrencyFormatter.format(context, it)}." }
                               ?: "• التكلفة التقديرية للشهر القادم: لسه مفيش تاريخ صرف كفاية للتنبؤ."),
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFE2E8F0)
                )
            }

            // ── Section 2: الشركة المنزلية وصحة المخزون والصيدلية ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏡", fontSize = 18.sp)
                    Text(stringResource(R.string.report_section_household), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                }
                Text(
                    text = "• عقل العائلة المشترك: $familyMembersCount أفراد متصلين ومزامنين لحظياً.\n" +
                           "• أصناف قاربت على النفاد في المخزون: $lowStockItemCount صنف.\n" +
                           (pharmacyAdherencePct?.let { "• الالتزام الدوائي هذا الأسبوع: $it%." }
                               ?: "• الالتزام الدوائي: لسه مفيش جرعات كفاية مسجّلة لحساب نسبة."),
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFE2E8F0)
                )
            }

            // ── Action Buttons (Print / Share / WhatsApp) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val shareText = "📄 تقرير عقل زاد:\n" +
                                "• إجمالي الصرف: ${com.example.data.CurrencyFormatter.format(context, totalSpent)}\n" +
                                (forecastNextMonth?.let { "• التكلفة التقديرية للشهر القادم: ${com.example.data.CurrencyFormatter.format(context, it)}\n" } ?: "") +
                                (pharmacyAdherencePct?.let { "• الالتزام الدوائي: $it%\n" } ?: "")
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, "مشاركة تقرير عقل زاد"))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F9B76),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.report_share), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1))
                ) {
                    Text(stringResource(R.string.report_close), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

