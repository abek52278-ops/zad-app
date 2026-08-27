package com.example.ui.components

import androidx.compose.animation.*
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * ── 1. شريط الاختصارات الأفقي السلس (Horizontal Shortcuts Rail) ──
 * مستوحى من zad_premium_v5.html: أزرار الاختصارات قابلة للتمرير يميناً ويساراً
 * بخلفيات باستيل ناعمة وفيزياء ضغط نابضية.
 */
data class QuickShortcut(
    val id: String,
    val title: String,
    val emoji: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color,
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
            QuickShortcut("inventory", "المخزون", "📦", Icons.Default.Inventory2, Color(0xFFE3F5EC), Color(0xFF0B6B4E), onNavigateToInventory),
            QuickShortcut("shopping", "التسوق", "🛒", Icons.Default.ShoppingCart, Color(0xFFFCEEE3), Color(0xFFC2703D), onNavigateToShopping),
            QuickShortcut("family", "العائلة", "👨‍👩‍👧", Icons.Default.FamilyRestroom, Color(0xFFF1EAFB), Color(0xFF7C3AED), onNavigateToFamily),
            QuickShortcut("subs", "الاشتراكات", "💳", Icons.Default.Subscriptions, Color(0xFFE8F1FC), Color(0xFF2563EB), onNavigateToSubscriptions),
            QuickShortcut("pharmacy", "الصيدلية", "💊", Icons.Default.LocalPharmacy, Color(0xFFFCE8ED), Color(0xFFDC5B4B), onNavigateToPharmacy),
            QuickShortcut("maint", "الصيانة", "🔧", Icons.Default.Build, Color(0xFFFDF3E1), Color(0xFFB45309), onNavigateToMaintenance),
            QuickShortcut("tasbiha", "التسبيح", "🌿", Icons.Default.Park, Color(0xFFF3E8FF), Color(0xFF6B21A8), onNavigateToTasbiha)
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        items(shortcuts, key = { it.id }) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .pressableScale()
                    .clickable { item.onClick() }
                    .width(66.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(item.bg)
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = item.fg.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.emoji, fontSize = 24.sp)
                }
                Text(
                    text = item.title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * ── 2. إيدج صحة المخزون والنواقص (Food Inventory Health & Shortages) ──
 * كروت بيضاء بزوايا 18dp وأيقونات الأغذية الحية ومؤشر الأيام المتبقية
 */
data class FoodItemSample(
    val name: String,
    val emoji: String,
    val category: String,
    val daysLeft: Int,
    val isLow: Boolean,
    val catBg: Color
)

@Composable
fun ZadFoodShortagesGlanceCard(
    inventory: List<ZadInventory>,
    onViewAllClick: () -> Unit,
    onConfirmItem: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sampleItems = remember(inventory) {
        if (inventory.isNotEmpty()) {
            inventory.take(4).map {
                val catBg = when (it.category) {
                    "فاكهة" -> Color(0xFFFCEAEA)
                    "خضار" -> Color(0xFFE9F5E9)
                    "ألبان" -> Color(0xFFEAF2FB)
                    "مخبوزات" -> Color(0xFFFBF1E3)
                    else -> Color(0xFFF1F5F9)
                }
                FoodItemSample(
                    name = it.itemName,
                    emoji = when {
                        it.itemName.contains("موز") -> "🍌"
                        it.itemName.contains("تفاح") -> "🍎"
                        it.itemName.contains("حليب") -> "🥛"
                        it.itemName.contains("طماطم") -> "🍅"
                        it.itemName.contains("خبز") -> "🍞"
                        it.itemName.contains("جبن") -> "🧀"
                        it.itemName.contains("عصير") -> "🧃"
                        else -> "🥑"
                    },
                    category = it.category ?: "عام",
                    daysLeft = it.quantity.toInt().coerceAtLeast(1),
                    isLow = it.quantity <= 2,
                    catBg = catBg
                )
            }
        } else {
            listOf(
                FoodItemSample("موز طازج", "🍌", "فاكهة", 2, true, Color(0xFFFCEAEA)),
                FoodItemSample("تفاح أحمر", "🍎", "فاكهة", 6, false, Color(0xFFFCEAEA)),
                FoodItemSample("حليب كامل", "🥛", "ألبان", 1, true, Color(0xFFEAF2FB)),
                FoodItemSample("خبز بلدي", "🍞", "مخبوزات", 3, false, Color(0xFFFBF1E3))
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
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
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE3F5EC)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🍏", fontSize = 16.sp)
                }
                Column {
                    Text("صحة المخزون والنواقص", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                    Text("مراقبة التلف والاحتياج الفعلي", fontSize = 11.5.sp, color = Color(0xFF64748B))
                }
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("فتح المخزون ←", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F9B76))
            }
        }

        // 2x2 Grid of Food Items
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sampleItems.take(2).forEach { item ->
                FoodGlanceTile(item = item, modifier = Modifier.weight(1f), onConfirm = { onConfirmItem(item.name) })
            }
        }
        if (sampleItems.size > 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sampleItems.drop(2).take(2).forEach { item ->
                    FoodGlanceTile(item = item, modifier = Modifier.weight(1f), onConfirm = { onConfirmItem(item.name) })
                }
            }
        }
    }
}

@Composable
private fun FoodGlanceTile(
    item: FoodItemSample,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = if (item.isLow) Color(0xFFDC5B4B) else if (item.daysLeft <= 4) Color(0xFFB45309) else Color(0xFF0F9B76)
    val progress = (item.daysLeft / 10f).coerceIn(0.1f, 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
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
                        .background(Color(0xFFFEE2E2))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("قارب ينتهي", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                }
            }
        }

        Text(
            text = item.name,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFFE2E8F0))
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
            color = Color(0xFF64748B)
        )
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
    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive } }
    val totalAmount = remember(activeSubs) {
        if (activeSubs.isNotEmpty()) activeSubs.sumOf { it.amount } else 294.0
    }

    val displayList = remember(activeSubs) {
        if (activeSubs.isNotEmpty()) {
            activeSubs.take(3).map {
                Triple(it.title, "${it.amount.toInt()} ر.س", when (it.title) {
                    "نتفليكس", "Netflix" -> Color(0xFFB45309)
                    "الجيم", "Gym" -> Color(0xFFDC5B4B)
                    else -> Color(0xFF0F9B76)
                })
            }
        } else {
            listOf(
                Triple("نتفليكس", "45 ر.س", Color(0xFFB45309)),
                Triple("STC TV", "99 ر.س", Color(0xFF064E3B)),
                Triple("اشتراك الجيم", "150 ر.س", Color(0xFFDC5B4B))
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
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
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE8F1FC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("الاشتراكات الشهرية", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                    Text("إجمالي شهري: ${totalAmount.toInt()} ر.س", fontSize = 11.5.sp, color = Color(0xFF64748B))
                }
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("عرض الكل ←", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displayList.forEach { (name, price, accentColor) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
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
                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    }
                    Text(price, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                }
            }
        }
    }
}

/**
 * ── 4. إيدج الصيدلية والجرعات (Pharmacy & Doses Quick Glance) ──
 * نسبة الالتزام 91% والتكلفة الشهرية والأدوية النشطة
 */
@Composable
fun ZadPharmacyGlanceCard(
    pharmacyItems: List<ZadPharmacyItem>,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
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
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFCE8ED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = Color(0xFFDC5B4B), modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("صيدلية العائلة والجرعات", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                    Text("الالتزام بالجرعات: 91% • منتظم", fontSize = 11.5.sp, color = Color(0xFF0F9B76), fontWeight = FontWeight.Bold)
                }
            }
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("فتح الصيدلية ←", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC5B4B))
            }
        }

        // Medicines Pill Rows
        val meds = remember(pharmacyItems) {
            if (pharmacyItems.isNotEmpty()) {
                pharmacyItems.take(3).map {
                    Triple(it.name, "جرعة منتظمة", if (it.isLowStock()) Color(0xFFB45309) else Color(0xFF0F9B76))
                }
            } else {
                listOf(
                    Triple("باراسيتامول", "3 مرات يومياً", Color(0xFF0F9B76)),
                    Triple("فيتامين د", "مرة واحدة يومياً", Color(0xFFB45309)),
                    Triple("أنسولين", "مرتين يومياً", Color(0xFFDC5B4B))
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            meds.forEach { (name, note, statusColor) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    }
                    Text(note, fontSize = 11.5.sp, color = Color(0xFF64748B))
                }
            }
        }
    }
}
