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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Brush
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
    val context = LocalContext.current
    val items by viewModel.pharmacyItems.collectAsState()
    val weeklyAdherence by viewModel.weeklyAdherencePercent.collectAsState()
    val monthlyCost by viewModel.monthlyPharmaCost.collectAsState()

    val adhPct = weeklyAdherence ?: 91
    val costVal = monthlyCost ?: items.sumOf { it.price ?: 0.0 }.toInt().let { if (it > 0) it else 180 }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Duo stats cards: adherence + monthlyCost (مطابقة 100% لـ pPharmacy)
        item {
            Row(
                modifier = Modifier.fillMaxWidth().fadeUpOnAppear(0L),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Adherence card with meter
                Column(
                    modifier = Modifier.weight(1f)
                        .zadCardShadow(ZadV3.rCard)
                        .clip(ZadV3.rCard)
                        .background(Color.White)
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.dose_adherence_label), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500)
                    Text("$adhPct%", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ZadV3.green800)
                    // Meter bar
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF0F172A).copy(alpha = 0.07f))) {
                        Box(Modifier.fillMaxWidth(adhPct / 100f).fillMaxHeight().clip(CircleShape).background(Color(0xFF0F9B76)))
                    }
                }
                // Monthly cost card
                Column(
                    modifier = Modifier.weight(1f)
                        .zadCardShadow(ZadV3.rCard)
                        .clip(ZadV3.rCard)
                        .background(Color.White)
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.monthly_pharma_cost_title), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray500)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$costVal", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ZadV3.ink)
                        Spacer(Modifier.width(4.dp))
                        Text(CurrencyFormatter.symbol(context), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.gray400, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item { V3EmptyHint() }
        } else {
            items(items.size) { i -> V3MedRow(items[i], i + 1) }
        }

        // Voice add button (btn-primary مطابقة لـ pPharmacy)
        item {
            val addMedText = stringResource(R.string.add_medication_voice_action)
            Button(
                onClick = { /* opens voice / camera assistant */ },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZadV3.green800),
                modifier = Modifier.fillMaxWidth().height(52.dp).fadeUpOnAppear(200L).pressableScale()
            ) {
                Text("💬 $addMedText", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun V3MedRow(med: ZadPharmacyItem, index: Int = 0) {
    val low = med.remainingQuantity <= 1
    val statusText = if (low) stringResource(R.string.low_quantity) else stringResource(R.string.regular_dose)
    val statusColor = if (low) Color(0xFFB45309) else ZadV3.green800
    val accentColor = if (low) Color(0xFFDC5B4B) else ZadV3.green800

    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard)
            .clip(ZadV3.rCard).background(Color.White)
            .pressableScale { }
            .fadeUpOnAppear((index * 50L).coerceAtMost(350L)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar 3.5px on start edge
        Box(Modifier.width(3.5.dp).height(64.dp).background(accentColor))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(med.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink)
                Text(
                    listOfNotNull(med.dosage, med.doseTimes?.let { "⏰ $it" }, "${med.remainingQuantity} ${med.unit}").joinToString(" · ").ifBlank { med.category },
                    fontSize = 11.5.sp,
                    color = ZadV3.gray400
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF064E3B).copy(alpha = 0.06f))
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
            }
        }
    }
}

// ── Shopping ─────────────────────────────────────────────────────────────────

@Composable
fun V3ShoppingScreen(viewModel: ZadViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val list by viewModel.shoppingList.collectAsState()
    val pending = list.filter { !it.isPurchased }
    val done = list.filter { it.isPurchased }

    val totalSpentEst = done.sumOf { it.estimatedPrice }
    val totalBudgetEst = list.sumOf { it.estimatedPrice }.let { if (it > 0) it else 300.0 }
    val spentRatio = if (totalBudgetEst > 0) (totalSpentEst / totalBudgetEst).toFloat().coerceIn(0f, 1f) else 0.7f

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Hero Card (مطابق تماماً لـ pShopping L740-745: gradient 135deg #064E3B→#0B6B4E + meter + تعبئة ذكية)
        item {
            val smartFillText = stringResource(R.string.smart_fill_action)
            Box(
                modifier = Modifier.fillMaxWidth()
                    .zadCardShadow(RoundedCornerShape(18.dp), elevation = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF0B6B4E))))
                    .padding(18.dp)
                    .fadeUpOnAppear(0L)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.shopping_list_total_label),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        "${CurrencyFormatter.format(context, totalSpentEst)} " + stringResource(R.string.out_of_budget_format, CurrencyFormatter.format(context, totalBudgetEst)),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    // Progress meter bar
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(spentRatio).fillMaxHeight().clip(CircleShape).background(Color.White.copy(alpha = 0.95f)))
                    }
                    Button(
                        onClick = { viewModel.refreshSmartShopping() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.padding(top = 4.dp).pressableScale()
                    ) {
                        Text("✨ $smartFillText", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 2. Pending shopping items with priority-accent border (lrow style)
        if (pending.isEmpty() && done.isEmpty()) {
            item { V3EmptyHint() }
        }

        items(pending.size) { idx ->
            val item = pending[idx]
            V3ShopLRow(
                item = item,
                context = context,
                index = idx + 1,
                onToggle = { viewModel.toggleShoppingItemPurchased(item.id) }
            )
        }

        // 3. Purchased Section
        if (done.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.v2_purchased),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZadV3.gray500,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(done.size) { idx ->
                val item = done[idx]
                V3ShopLRow(
                    item = item,
                    context = context,
                    index = idx + pending.size + 1,
                    onToggle = { viewModel.toggleShoppingItemPurchased(item.id) }
                )
            }
        }
    }
}

@Composable
private fun V3ShopLRow(
    item: ZadShoppingItem,
    context: android.content.Context,
    index: Int,
    onToggle: () -> Unit
) {
    val priority = item.priority ?: "normal"
    val (accentColor, pillBg, pillFg, pillLabelRes) = when (priority.lowercase()) {
        "high", "critical", "urgent" -> listOf(Color(0xFFDC5B4B), Color(0x1FDC5B4B), Color(0xFFDC5B4B), R.string.priority_critical)
        "medium", "normal" -> listOf(Color(0xFFB45309), Color(0x1AB45309), Color(0xFFB45309), R.string.priority_medium)
        else -> listOf(Color(0xFF064E3B), Color(0x14064E3B), Color(0xFF064E3B), R.string.priority_low)
    }

    val finalAccent = if (item.isPurchased) ZadV3.gray400 else (accentColor as Color)
    val pillText = stringResource(pillLabelRes as Int)

    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard)
            .clip(ZadV3.rCard).background(Color.White)
            .pressableScale(onClick = onToggle)
            .fadeUpOnAppear((index * 40L).coerceAtMost(300L)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent edge bar 3.5px
        Box(Modifier.width(3.5.dp).height(56.dp).background(finalAccent))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.itemName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isPurchased) ZadV3.gray400 else ZadV3.ink
                )
                Text(
                    if (item.estimatedPrice > 0) CurrencyFormatter.format(context, item.estimatedPrice)
                    else stringResource(R.string.quantity_piece_format, item.quantity),
                    fontSize = 11.5.sp,
                    color = ZadV3.gray400
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pillBg as Color)
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(
                    if (item.isPurchased) stringResource(R.string.purchased_badge) else pillText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (item.isPurchased) ZadV3.gray400 else (pillFg as Color)
                )
            }
        }
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
data class InvCatStyle(val bg: Color, val fg: Color, val emoji: String = "📦")

private fun invCatStyle(category: String?): InvCatStyle {
    val c = (category ?: "").lowercase()
    return when {
        listOf("fruit", "فاكه").any { c.contains(it) } -> InvCatStyle(Color(0xFFFCEAEA), Color(0xFFDC5B4B), "🍎")
        listOf("veg", "خضار", "خضروات").any { c.contains(it) } -> InvCatStyle(Color(0xFFE9F5E9), Color(0xFF3CA06E), "🥦")
        listOf("dairy", "ألبان", "البان").any { c.contains(it) } -> InvCatStyle(Color(0xFFEAF2FB), Color(0xFF2563EB), "🥛")
        listOf("bakery", "خبز", "مخبوزات").any { c.contains(it) } -> InvCatStyle(Color(0xFFFBF1E3), Color(0xFFB45309), "🍞")
        listOf("drink", "مشروب", "مياه").any { c.contains(it) } -> InvCatStyle(Color(0xFFE7F5F8), Color(0xFF0891B2), "🧃")
        listOf("sweet", "حلويات").any { c.contains(it) } -> InvCatStyle(Color(0xFFF1E9E3), Color(0xFFC2703D), "🍫")
        listOf("clean", "تنظيف").any { c.contains(it) } -> InvCatStyle(Color(0xFFEEF0F3), Color(0xFF374151), "🧼")
        else -> InvCatStyle(Color(0xFFEDEEF0), Color(0xFF374151), "📦")
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
        // Low-stock summary banner (مطابقة للبروتوتايب: .warn-strip r14 بـ 0x14DC5B4B)
        if (lowItems.isNotEmpty()) {
            Box(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
                    .fadeUpOnAppear().clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14DC5B4B)).padding(horizontal = 15.dp, vertical = 13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("⚠️", fontSize = 14.sp)
                    Text(
                        stringResource(R.string.v3x_low_banner, lowItems.size, lowItems.take(3).joinToString("، ") { it.itemName }),
                        fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC5B4B), lineHeight = 18.sp,
                    )
                }
            }
        }

        // Category filter chips (.chips-scroll + .fchip r999 مع .cic 21px circle + scale(1.05) on select)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            item {
                V3InvFChip(
                    emoji = "☰",
                    label = stringResource(R.string.v3x_all),
                    circleBg = Color(0xFFEDEEF0),
                    selected = selectedCat == -1,
                    index = 0,
                    onClick = { selectedCat = -1 },
                )
            }
            items(categories.size) { i ->
                val cat = categories[i]
                val style = invCatStyle(cat)
                V3InvFChip(
                    emoji = style.emoji,
                    label = cat,
                    circleBg = style.bg,
                    selected = selectedCat == i,
                    index = i + 1,
                    onClick = { selectedCat = i },
                )
            }
        }

        // 2-column grid of item cards (.inv-grid gap 12px)
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
private fun V3InvFChip(
    emoji: String,
    label: String,
    circleBg: Color,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fadeUpOnAppear(index * 30L)
            .clip(ZadV3.rPill)
            .background(if (selected) ZadV3.green800 else Color.White)
            .then(if (!selected) Modifier.zadCardShadow(ZadV3.rPill, 6.dp) else Modifier)
            .pressableScale(onClick = onClick)
            .padding(start = 7.dp, end = 13.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(21.dp).clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.2f) else circleBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 11.sp)
        }
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) Color.White else ZadV3.slate
        )
    }
}

@Composable
private fun V3InvItemCard(
    item: com.example.data.ZadInventory,
    confirmed: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = item.daysUntilExpiry() ?: 14
    val pct = (d / 20f).coerceIn(0f, 1f)
    val meterColor = freshnessColor(d)
    val isLow = d <= 2 || item.quantity <= (item.lowStockThreshold ?: 2)
    val style = invCatStyle(item.category ?: "")

    Column(
        modifier = modifier
            .zadCardShadow(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(style.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(style.emoji, fontSize = 16.sp)
                }
                Text(
                    item.itemName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZadV3.ink,
                    maxLines = 1
                )
            }
            if (isLow && !confirmed) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFE4572E).copy(alpha = 0.12f))
                        .pressableScale(onClick = onConfirm)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.v3x_confirm),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZadV3.danger
                    )
                }
            }
        }

        Text(
            item.category ?: "",
            fontSize = 12.sp,
            color = ZadV3.gray400,
            maxLines = 1
        )

        // Progress meter bar (8px height, rounded pill)
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFF0F172A).copy(alpha = 0.07f))
        ) {
            Box(
                Modifier.fillMaxWidth(pct).fillMaxHeight().clip(CircleShape).background(meterColor)
            )
        }

        Text(
            stringResource(R.string.left_days_format, d),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = ZadV3.gray500
        )
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
