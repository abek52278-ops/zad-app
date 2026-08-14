package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.ZadEmptyState
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * خريطة زاد — عقد المجالات الحقيقية (التزامات/اشتراكات/ديون/مخزون/تسوق/صيدلية/صيانة/ميزانية)
 * ومين بيأثر على مين، محسوبة لحظيًا من نفس StateFlows اللي كل شاشة تانية في التطبيق
 * بتستخدمها — مش قاعدة بيانات Graph منفصلة. الخطوط المتصلة (solid) بتمثل علاقة محسوبة
 * فعليًا في الكود (BudgetMath.committedInCycle بتجمع الالتزامات والاشتراكات بس)؛ الخطوط
 * المنقطة بتمثل علاقة منطقية موجودة (الديون والصيانة) لسه مش داخلة في حساب "المتاح" —
 * فرق حقيقي، مش نقص في الرسمة.
 */
internal data class MapDomain(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val count: Int,
    val amount: Double?
)

internal data class MapEdge(val from: String, val to: String, val solid: Boolean)

internal data class MapItem(val label: String, val sublabel: String)

@Composable
fun ZadKnowledgeMapScreen(viewModel: ZadViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val obligations by viewModel.obligations.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val shoppingList by viewModel.shoppingList.collectAsState()
    val pharmacyItems by viewModel.pharmacyItems.collectAsState()
    val maintenanceItems by viewModel.maintenanceItems.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val committed by viewModel.committed.collectAsState()
    val availableFigure by viewModel.availableFigure.collectAsState()

    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive } }
    val activeDebts = remember(debts) { debts.filter { it.isActive } }
    val lowStockInventory = remember(inventory) { inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) } }
    val pendingShopping = remember(shoppingList) { shoppingList.filter { !it.isPurchased } }
    val lowStockPharmacy = remember(pharmacyItems) { pharmacyItems.filter { it.isLowStock() } }

    val domains = remember(obligations, activeSubs, activeDebts, lowStockInventory, pendingShopping, lowStockPharmacy, maintenanceItems, budget) {
        listOf(
            MapDomain("budget", context.getString(R.string.nav_budget), Icons.Default.AccountBalanceWallet, catBankingIcon, 0, budget),
            MapDomain("obligations", context.getString(R.string.obligations_domain_label), Icons.Default.EventRepeat, catBillsIcon, obligations.size, obligations.sumOf { it.amount }),
            MapDomain("subscriptions", context.getString(R.string.nav_subscriptions_installments), Icons.Default.Subscriptions, catEntertainIcon, activeSubs.size, activeSubs.sumOf { it.amount }),
            MapDomain("debts", context.getString(R.string.debts_domain_label), Icons.Default.CreditCard, catTransportIcon, activeDebts.size, activeDebts.sumOf { it.remainingBalance }),
            MapDomain("inventory", context.getString(R.string.nav_inventory), Icons.Default.Inventory2, catFoodIcon, lowStockInventory.size, null),
            MapDomain("shopping", context.getString(R.string.nav_shopping), Icons.Default.ShoppingCart, catDailyIcon, pendingShopping.size, null),
            MapDomain("pharmacy", context.getString(R.string.nav_pharmacy), Icons.Default.LocalPharmacy, catHealthIcon, lowStockPharmacy.size, null),
            MapDomain("maintenance", context.getString(R.string.screen_title_maintenance), Icons.Default.Build, catSavingsIcon, maintenanceItems.size, maintenanceItems.sumOf { it.estimatedCost }),
        )
    }
    val edges = remember {
        listOf(
            MapEdge("obligations", "budget", solid = true),
            MapEdge("subscriptions", "budget", solid = true),
            MapEdge("inventory", "shopping", solid = true),
            MapEdge("pharmacy", "shopping", solid = true),
            MapEdge("debts", "budget", solid = false),
            MapEdge("maintenance", "budget", solid = false),
        )
    }

    var selectedDomain by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (selectedDomain != null) selectedDomain = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurfaceVariant)
            }
            val current = domains.find { it.key == selectedDomain }
            Text(
                current?.label ?: stringResource(R.string.knowledge_map_title),
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = current?.color ?: onSurface
            )
        }

        AnimatedContent(targetState = selectedDomain, label = "map") { key ->
            if (key == null) {
                DomainRing(domains = domains, edges = edges, onSelect = { selectedDomain = it })
            } else if (key == "budget") {
                BudgetDomainPanel(budget = budget, committed = committed, available = availableFigure?.value)
            } else {
                val domain = domains.first { it.key == key }
                val items = itemsForDomain(key, obligations, activeSubs, activeDebts, lowStockInventory, pendingShopping, lowStockPharmacy, maintenanceItems)
                ItemRing(domain = domain, items = items)
            }
        }

        // مش كل خط في الرسمة بمعنى واحد — التوضيح ده بديل عن كتابة label على كل خط
        // جوا Canvas (تعقيد رندر إضافي من غير قيمة حقيقية زيادة).
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(color = onSurfaceVariant, dashed = false, label = stringResource(R.string.knowledge_map_legend_real))
            LegendDot(color = onSurfaceVariant, dashed = true, label = stringResource(R.string.knowledge_map_legend_gap))
        }
    }
}

@Composable
private fun LegendDot(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(color.copy(alpha = if (dashed) 0.4f else 0.8f))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = Typography.labelSmall, color = onSurfaceVariant)
    }
}

private fun itemsForDomain(
    key: String,
    obligations: List<com.example.data.ZadObligation>,
    activeSubs: List<com.example.data.ZadSubscription>,
    activeDebts: List<com.example.data.ZadDebt>,
    lowStockInventory: List<com.example.data.ZadInventory>,
    pendingShopping: List<com.example.data.ZadShoppingItem>,
    lowStockPharmacy: List<com.example.data.ZadPharmacyItem>,
    maintenanceItems: List<com.example.data.ZadMaintenanceItem>
): List<MapItem> = when (key) {
    "obligations" -> obligations.map { MapItem(it.title, "${it.amount}") }
    "subscriptions" -> activeSubs.map { MapItem(it.title, "${it.amount}/شهر") }
    "debts" -> activeDebts.map { MapItem(it.name, "متبقي ${it.remainingBalance}") }
    "inventory" -> lowStockInventory.map { MapItem(it.itemName, "${it.quantity} ${it.unit ?: ""}") }
    "shopping" -> pendingShopping.map { MapItem(it.itemName, "${it.quantity}") }
    "pharmacy" -> lowStockPharmacy.map { MapItem(it.name, "متبقي ${it.remainingQuantity} ${it.unit}") }
    "maintenance" -> maintenanceItems.map { MapItem(it.name, if (it.estimatedCost > 0) "${it.estimatedCost}" else it.category) }
    else -> emptyList()
}

@Composable
internal fun BudgetDomainPanel(budget: Double, committed: Double, available: Double?) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(catBankingIcon.copy(alpha = 0.08f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = catBankingIcon, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            BudgetFigureRow(stringResource(R.string.budget_label), com.example.data.CurrencyFormatter.format(context, budget))
            BudgetFigureRow(stringResource(R.string.committed_label), com.example.data.CurrencyFormatter.format(context, committed))
            BudgetFigureRow(stringResource(R.string.available_label), available?.let { com.example.data.CurrencyFormatter.format(context, it) } ?: "—")
        }
    }
}

@Composable
private fun BudgetFigureRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.bodyMedium, color = onSurfaceVariant)
        Text(value, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
    }
}

@Composable
internal fun DomainRing(domains: List<MapDomain>, edges: List<MapEdge>, onSelect: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2
        val radius = (minOf(maxWidth, maxHeight) / 2) - 64.dp
        val nodeSize = 56.dp

        val positions = remember(domains, maxWidth, maxHeight) {
            domains.mapIndexed { i, d ->
                val angle = Math.toRadians(-90.0 + i * (360.0 / domains.size))
                val x = centerX + radius * cos(angle).toFloat()
                val y = centerY + radius * sin(angle).toFloat()
                d.key to (x to y)
            }.toMap()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPx = Offset(centerX.toPx(), centerY.toPx())
            domains.forEach { d ->
                val (x, y) = positions.getValue(d.key)
                drawLine(
                    color = d.color.copy(alpha = 0.35f),
                    start = centerPx,
                    end = Offset(x.toPx(), y.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            edges.forEach { e ->
                val from = positions[e.from] ?: return@forEach
                val to = positions[e.to] ?: return@forEach
                drawLine(
                    color = onSurfaceVariant.copy(alpha = if (e.solid) 0.7f else 0.45f),
                    start = Offset(from.first.toPx(), from.second.toPx()),
                    end = Offset(to.first.toPx(), to.second.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = if (!e.solid) PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) else null
                )
            }
        }

        // مركز الشبكة — "زاد" نفسه
        Box(
            modifier = Modifier
                .offset(centerX - 36.dp, centerY - 36.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center
        ) {
            Text("زاد", style = Typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
        }

        domains.forEach { d ->
            val (x, y) = positions.getValue(d.key)
            Column(
                modifier = Modifier
                    .offset(x - 40.dp, y - nodeSize / 2)
                    .width(80.dp)
                    .clickable { onSelect(d.key) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(nodeSize), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(nodeSize)
                            .clip(CircleShape)
                            .background(surface)
                            .border(2.dp, d.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(d.icon, contentDescription = null, tint = d.color, modifier = Modifier.size(24.dp))
                    }
                    // Badge لازم يكون برا الـ Box المقصوص دائريًا (clip(CircleShape)) —
                    // لو اتحط جواه بيتقطع نص شكله لأنه قاعد على حافة الدايرة.
                    if (d.count > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(d.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${d.count}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(d.label, style = Typography.labelSmall, color = onSurface, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ItemRing(domain: MapDomain, items: List<MapItem>) {
    if (items.isEmpty()) {
        ZadEmptyState(
            icon = domain.icon,
            title = stringResource(R.string.knowledge_map_domain_empty),
            modifier = Modifier.fillMaxSize().padding(24.dp)
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(domain.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(domain.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(surface)
                            .border(1.5.dp, domain.color.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Circle, contentDescription = null, tint = domain.color, modifier = Modifier.size(8.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.label, style = Typography.labelSmall, fontWeight = FontWeight.SemiBold, color = onSurface, textAlign = TextAlign.Center, maxLines = 1)
                    Text(item.sublabel, style = Typography.labelSmall, color = onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
