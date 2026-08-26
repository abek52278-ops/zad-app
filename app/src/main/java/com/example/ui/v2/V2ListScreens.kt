package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.ZadInventory
import com.example.data.ZadMaintenanceItem
import com.example.data.ZadPharmacyItem
import com.example.data.ZadShoppingItem
import com.example.data.ZadSubscription
import com.example.ui.theme.ZadMeterBar
import com.example.ui.theme.ZadV3
import com.example.ui.theme.zadCardShadow
import com.example.ui.theme.zadV2Card
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate

// ═══════════════════════════════════════════════════════════════════════════════
// V3 List Screens — Pharmacy, Shopping, Subscriptions, Maintenance, Inventory
// ═══════════════════════════════════════════════════════════════════════════════

// ── Pharmacy ─────────────────────────────────────────────────────────────────

@Composable
fun V3PharmacyScreen(viewModel: ZadViewModel, modifier: Modifier = Modifier) {
    val items by viewModel.pharmacyItems.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.isEmpty()) item { V3EmptyHint() }
        items(items.size) { i -> V3MedRow(items[i], i) }
    }
}

@Composable
private fun V3MedRow(med: ZadPharmacyItem, index: Int = 0) {
    val low = med.remainingQuantity <= 1
    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard)
            .clip(ZadV3.rCard).background(if (low) Color(0xFFFCE8ED) else Color.White)
            .pressableScale { }
            .fadeUpOnAppear((index * 60L).coerceAtMost(360L))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFFCE8ED)), contentAlignment = Alignment.Center) { Text("💊", fontSize = 20.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(med.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
            Text(listOfNotNull(med.dosage, med.doseTimes?.let { "⏰ $it" }).joinToString(" · ").ifBlank { med.category }, fontSize = 12.sp, color = ZadV3.gray500)
        }
        Text("${med.remainingQuantity} ${med.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (low) ZadV3.danger else ZadV3.green800)
    }
}

// ── Shopping ─────────────────────────────────────────────────────────────────

@Composable
fun V3ShoppingScreen(viewModel: ZadViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val list by viewModel.shoppingList.collectAsState()
    val pending = list.filter { !it.isPurchased }
    val done = list.filter { it.isPurchased }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pending.isEmpty() && done.isEmpty()) item { V3EmptyHint() }
        item { Text(stringResource(R.string.v2_to_buy), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink) }
        pending.forEachIndexed { idx, item ->
            item { Box(Modifier.fadeUpOnAppear((idx * 50L).coerceAtMost(300L))) { V3ShopRow(item, context, onToggle = { viewModel.toggleShoppingItemPurchased(item.id) }) } }
        }
        if (done.isNotEmpty()) {
            item { Text(stringResource(R.string.v2_purchased), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink, modifier = Modifier.padding(top = 8.dp)) }
            done.forEach { item -> item { V3ShopRow(item, context, onToggle = { viewModel.toggleShoppingItemPurchased(item.id) }) } }
        }
    }
}

@Composable
private fun V3ShopRow(item: ZadShoppingItem, context: android.content.Context, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
            .pressableScale(onClick = onToggle).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).border(1.5.dp, if (item.isPurchased) Color.Transparent else ZadV3.gray400, CircleShape)
                .clip(CircleShape).background(if (item.isPurchased) ZadV3.green800 else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { if (item.isPurchased) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Text(item.itemName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (item.isPurchased) ZadV3.gray400 else ZadV3.ink, modifier = Modifier.weight(1f))
        if (item.estimatedPrice > 0) Text(CurrencyFormatter.format(context, item.estimatedPrice), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.slate)
    }
}

// ── Subscriptions ────────────────────────────────────────────────────────────

@Composable
fun V3SubscriptionsScreen(viewModel: ZadViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val subs by viewModel.subscriptions.collectAsState()
    val active = subs.filter { it.isActive }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (active.isEmpty()) item { V3EmptyHint() }
        active.forEachIndexed { idx, sub ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
                        .fadeUpOnAppear((idx * 60L).coerceAtMost(300L)).padding(start = 14.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F1FC)), contentAlignment = Alignment.Center) { Text("🔄", fontSize = 19.sp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(sub.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                        Text(sub.renewalDate?.take(10) ?: "", fontSize = 12.sp, color = ZadV3.gray500)
                    }
                    Text(CurrencyFormatter.format(context, sub.amount), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZadV3.green800)
                }
            }
        }
    }
}

// ── Maintenance ──────────────────────────────────────────────────────────────

@Composable
fun V3MaintenanceScreen(viewModel: ZadViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items by viewModel.maintenanceItems.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.isEmpty()) item { V3EmptyHint() }
        items(items.size) { i -> V3MaintenanceRow(items[i], context, i) }
    }
}

@Composable
private fun V3MaintenanceRow(m: ZadMaintenanceItem, context: android.content.Context, index: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
            .fadeUpOnAppear((index * 60L).coerceAtMost(300L)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFDF3E1)), contentAlignment = Alignment.Center) { Text("🛠️", fontSize = 18.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
            Text(listOfNotNull(m.category, m.warrantyExpiryDate?.let { stringResource(R.string.v2_warranty_until, it.take(10)) }).joinToString(" · "), fontSize = 12.sp, color = ZadV3.gray500)
        }
        if (m.estimatedCost > 0) Text(CurrencyFormatter.format(context, m.estimatedCost), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.warn)
    }
}

// ── Inventory ────────────────────────────────────────────────────────────────

/** Pastel category look matching the prototype's INV_CATEGORIES palette. */
data class InvCatStyle(val bg: Color, val fg: Color)

private fun invCatStyle(category: String?): InvCatStyle {
    val c = (category ?: "").lowercase()
    return when {
        listOf("fruit", "فاكه").any { c.contains(it) } -> InvCatStyle(Color(0xFFFCEAEA), Color(0xFFDC5B4B))
        listOf("veg", "خضار", "خضروات").any { c.contains(it) } -> InvCatStyle(Color(0xFFE9F5E9), Color(0xFF3CA06E))
        listOf("dairy", "ألبان", "البان").any { c.contains(it) } -> InvCatStyle(Color(0xFFEAF2FB), Color(0xFF2563EB))
        listOf("bakery", "خبز", "مخبوزات").any { c.contains(it) } -> InvCatStyle(Color(0xFFFBF1E3), Color(0xFFB45309))
        listOf("drink", "مشروب", "مياه").any { c.contains(it) } -> InvCatStyle(Color(0xFFE7F5F8), Color(0xFF0891B2))
        listOf("sweet", "حلويات").any { c.contains(it) } -> InvCatStyle(Color(0xFFF1E9E3), Color(0xFFC2703D))
        listOf("clean", "تنظيف").any { c.contains(it) } -> InvCatStyle(Color(0xFFEEF0F3), Color(0xFF374151))
        else -> InvCatStyle(Color(0xFFEDEEF0), Color(0xFF374151))
    }
}

private fun ZadInventory.daysUntilExpiry(): Int? =
    expiryDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }

private fun freshnessColor(days: Int): Color =
    if (days <= 2) Color(0xFFDC5B4B) else if (days <= 6) Color(0xFFB45309) else Color(0xFF064E3B)

@Composable
fun V3InventoryScreen(viewModel: ZadViewModel, modifier: Modifier = Modifier) {
    val inventory by viewModel.inventory.collectAsState()
    var selectedCat by remember { mutableIntStateOf(-1) } // -1 = all
    var restocked by remember { mutableStateOf(setOf<String>()) }


    val items = remember(inventory) { inventory.sortedBy { it.itemName } }
    val categories = remember(items) { items.mapNotNull { it.category }.distinct().sorted() }

    // Low stock: expiring within 2 days OR quantity at/below threshold
    val lowItems = items.filter { item ->
        val d = item.daysUntilExpiry()
        (d != null && d <= 2) || item.quantity <= (item.lowStockThreshold ?: 2)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Low-stock summary banner
        if (lowItems.isNotEmpty()) {
            Box(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                    .fadeUpOnAppear().clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14DC5B4B)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(R.string.v3x_low_banner, lowItems.size, lowItems.take(3).joinToString("، ") { it.itemName }),
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC5B4B), lineHeight = 18.sp,
                )
            }
        }

        // Category filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            item {
                V3InvChip(
                    label = stringResource(R.string.v3x_all),
                    style = InvCatStyle(Color(0xFFEDEEF0), Color(0xFF374151)),
                    selected = selectedCat == -1,
                    index = 0,
                    onClick = { selectedCat = -1 },
                )
            }
            items(categories.size) { i ->
                val cat = categories[i]
                V3InvChip(
                    label = cat,
                    style = invCatStyle(cat),
                    selected = selectedCat == i,
                    index = i + 1,
                    onClick = { selectedCat = i },
                )
            }
        }

        // 2-column grid of item cards
        val filtered = if (selectedCat == -1) items else items.filter { it.category == categories.getOrNull(selectedCat) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filtered.isEmpty()) item { V3EmptyHint() }
            val rows = filtered.chunked(2)
            items(rows.size) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows[r].forEach { item ->
                        V3InvItemCard(
                            item = item,
                            confirmed = item.id in restocked,
                            onConfirm = {
                                restocked = restocked + item.id
                                viewModel.updateInventoryItem(item, item.itemName, item.quantity + 1, item.unit)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rows[r].size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun V3InvChip(label: String, style: InvCatStyle, selected: Boolean, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fadeUpOnAppear(index * 40L)
            .clip(ZadV3.rPill)
            .background(if (selected) ZadV3.green800 else Color.White)
            .then(if (!selected) Modifier.zadCardShadow(ZadV3.rPill, 6.dp) else Modifier)
            .pressableScale(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.2f) else style.bg),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else style.fg)) }
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else ZadV3.slate)
    }
}

@Composable
private fun V3InvItemCard(
    item: com.example.data.ZadInventory,
    confirmed: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = item.daysUntilExpiry() ?: 14
    val pct = ((days.coerceAtLeast(0) / 20f) * 100f).coerceIn(4f, 100f)
    val barColor = freshnessColor(days)
    val style = invCatStyle(item.category)
    val low = days <= 2 || item.quantity <= (item.lowStockThreshold ?: 2)
    val showConfirm = low && !confirmed

    Column(
        modifier = modifier
            .zadCardShadow(ZadV3.rCard)
            .clip(ZadV3.rCard).background(Color.White)
            .fadeUpOnAppear()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(style.bg), contentAlignment = Alignment.Center) {
                Text("🧺", fontSize = 13.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.itemName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.category ?: "", fontSize = 11.5.sp, color = ZadV3.gray400, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showConfirm) {
            Box(
                modifier = Modifier.clip(ZadV3.rPill).background(Color(0x1FE4572E))
                    .pressableScale(onClick = onConfirm).padding(horizontal = 10.dp, vertical = 4.dp)
                    .align(Alignment.End),
            ) {
                Text(stringResource(R.string.v3x_confirm), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ZadV3.danger)
            }
        }
        ZadMeterBar(progress = pct / 100f, color = barColor, height = 5.dp)
        Text(stringResource(R.string.v3x_days_remaining, days), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500)
    }
}

// ── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun V3EmptyHint() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.v2_empty_section), fontSize = 13.sp, color = ZadV3.gray400, textAlign = TextAlign.Center)
    }
}

// Re-export V2EmptyHint for other modules that use it
@Composable
fun V2EmptyHint() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text("", fontSize = 13.sp, color = ZadV3.gray400)
    }
}
