package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.InventoryFlowEngine
import com.example.data.ZadInsight
import com.example.ui.components.ZadEmptyState
import com.example.ui.components.pressableScale
import com.example.ui.components.pulseGlow
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
 *
 * الشكل البصري (شبكة/توهج نيون داكن) مقصود لهذه الشاشة بالذات، بنفس منطق استثناء
 * Kids Mode في CLAUDE.md — ألوان ثابتة محلية لا تتسرب لمكونات مشتركة.
 */
private val kmBg = ZadSciFiBg
private val kmGrid = ZadSciFiGrid
private val kmNeonEmerald = ZadSciFiNeonGreen
private val kmCyanElectric = ZadSciFiCyanElectric
private val kmAmberAlert = ZadSciFiAmber
private val kmTextPrimary = ZadSciFiTextPrimary
private val kmTextSecondary = ZadSciFiTextSecondary
private val kmMono = FontFamily.Monospace

// المجالات اللي ليها شاشة مخصصة فعلاً في ZadRoutes — obligations/debts مفيش لهم
// شاشة منفصلة (بيظهروا جوه الميزانية)، فبيتعرض لهم زر "اسأل زاد" بس.
private val kmDomainRoutes = mapOf(
    "budget" to com.example.ui.components.ZadRoutes.BUDGET,
    "subscriptions" to com.example.ui.components.ZadRoutes.SUBS,
    "inventory" to com.example.ui.components.ZadRoutes.INVENTORY,
    "shopping" to com.example.ui.components.ZadRoutes.SHOPPING,
    "pharmacy" to com.example.ui.components.ZadRoutes.PHARMACY,
    "maintenance" to com.example.ui.components.ZadRoutes.MAINTENANCE,
)

// zad_insights مفهوش عمود "أنهي مجال" — dedupe_key/about_item/title/body نصوص حرة
// (اتأكد ده بالبحث في zad-brain/index.ts قبل ما اتبني عليه). التطابق ده تخمين قائم على
// كلمات مفتاحية بنفس مفردات labels المجالات الظاهرة للعميل أصلاً، مش تاج مضمون من
// السيرفر — ده اللي خلّانا نضيف بند تالت في الـ legend بدل ما ندّعي دقة مفيهاش.
private val kmDomainKeywords: Map<String, List<String>> = mapOf(
    "budget" to listOf("ميزاني", "متاح", "الصرف", "budget", "cash_reconciliation", "cycle_start"),
    "obligations" to listOf("التزام", "إيجار", "obligation"),
    "subscriptions" to listOf("اشتراك", "subscription"),
    "debts" to listOf("دين", "الديون", "قسط", "debt"),
    "inventory" to listOf("مخزون", "stock", "inventory"),
    "shopping" to listOf("تسوق", "اشتري", "shopping"),
    "pharmacy" to listOf("دوا", "الصيدلية", "جرعة", "pharmacy", "dose"),
    "maintenance" to listOf("صيانة", "ضمان", "maintenance", "warranty"),
)

private fun matchedDomainKeys(insight: ZadInsight): Set<String> {
    val text = listOfNotNull(insight.dedupeKey, insight.aboutItem, insight.title, insight.body)
        .joinToString(" ")
        .lowercase()
    return kmDomainKeywords.filterValues { keywords -> keywords.any { text.contains(it.lowercase()) } }.keys
}

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
fun ZadKnowledgeMapScreen(
    viewModel: ZadViewModel,
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {}
) {
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
    val zadInsights by viewModel.zadInsights.collectAsState()

    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isActive } }
    val activeDebts = remember(debts) { debts.filter { it.isActive } }
    val lowStockInventory = remember(inventory) { inventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) } }
    val pendingShopping = remember(shoppingList) { shoppingList.filter { !it.isPurchased } }
    val lowStockPharmacy = remember(pharmacyItems) { pharmacyItems.filter { it.isLowStock() } }

    val domains = remember(obligations, activeSubs, activeDebts, lowStockInventory, pendingShopping, lowStockPharmacy, maintenanceItems, budget) {
        listOf(
            MapDomain("budget", context.getString(R.string.nav_budget), Icons.Default.AccountBalanceWallet, kmCyanElectric, 0, budget),
            MapDomain("obligations", context.getString(R.string.obligations_domain_label), Icons.Default.EventRepeat, kmAmberAlert, obligations.size, obligations.sumOf { it.amount }),
            MapDomain("subscriptions", context.getString(R.string.nav_subscriptions_installments), Icons.Default.Subscriptions, kmCyanElectric, activeSubs.size, activeSubs.sumOf { it.amount }),
            MapDomain("debts", context.getString(R.string.debts_domain_label), Icons.Default.CreditCard, kmAmberAlert, activeDebts.size, activeDebts.sumOf { it.remainingBalance }),
            MapDomain("inventory", context.getString(R.string.nav_inventory), Icons.Default.Inventory2, kmNeonEmerald, lowStockInventory.size, null),
            MapDomain("shopping", context.getString(R.string.nav_shopping), Icons.Default.ShoppingCart, kmNeonEmerald, pendingShopping.size, null),
            MapDomain("pharmacy", context.getString(R.string.nav_pharmacy), Icons.Default.LocalPharmacy, if (lowStockPharmacy.isNotEmpty()) kmAmberAlert else kmNeonEmerald, lowStockPharmacy.size, null),
            MapDomain("maintenance", context.getString(R.string.screen_title_maintenance), Icons.Default.Build, kmAmberAlert, maintenanceItems.size, maintenanceItems.sumOf { it.estimatedCost }),
        )
    }
    // الروابط مشتقة من بيانات المستخدم مع شبكة الأعصاب التفاعلية
    val edges = remember(
        obligations, activeSubs, activeDebts,
        lowStockInventory, pendingShopping, lowStockPharmacy, maintenanceItems, budget,
    ) {
        val hasBudget = budget > 0.0
        val shoppingNames = pendingShopping.map { it.itemName }

        // صنف ناقص وله سطر مقابل في قايمة التسوق = عصب واصل فعلاً، مش علاقة نظرية.
        fun bridgesToShopping(names: List<String>): Boolean =
            names.any { needed -> shoppingNames.any { InventoryFlowEngine.namesMatch(it, needed) } }

        buildList {
            // علاقات "بتاكل من الميزانية": المبلغ نفسه هو الربط، فوجود بند بمبلغ = solid.
            fun spendEdge(key: String, count: Int, amount: Double) {
                if (count == 0 || !hasBudget) return
                add(MapEdge(key, "budget", solid = amount > 0.0))
            }
            spendEdge("obligations", obligations.size, obligations.sumOf { it.amount })
            spendEdge("subscriptions", activeSubs.size, activeSubs.sumOf { it.amount })
            spendEdge("debts", activeDebts.size, activeDebts.sumOf { it.remainingBalance })
            spendEdge("maintenance", maintenanceItems.size, maintenanceItems.sumOf { it.estimatedCost })
            spendEdge("shopping", pendingShopping.size, pendingShopping.sumOf { it.estimatedPrice })
            spendEdge("pharmacy", lowStockPharmacy.size, lowStockPharmacy.sumOf { it.price })

            // Multi-edges neural constellation:
            // 1. Inventory <-> Budget: Direct grocery & stock replenishment
            if (hasBudget && lowStockInventory.isNotEmpty()) {
                add(MapEdge("inventory", "budget", solid = true))
            }
            // 2. Inventory <-> Shopping: Low stock items need shopping
            if (lowStockInventory.isNotEmpty() && pendingShopping.isNotEmpty()) {
                add(MapEdge("inventory", "shopping", solid = bridgesToShopping(lowStockInventory.map { it.itemName })))
            }
            // 3. Pharmacy <-> Shopping: Prescriptions or medicine purchases
            if (lowStockPharmacy.isNotEmpty() && pendingShopping.isNotEmpty()) {
                add(MapEdge("pharmacy", "shopping", solid = bridgesToShopping(lowStockPharmacy.map { it.name })))
            }
            // 4. Inventory <-> Subscriptions: Household recurring delivery subscriptions
            if (lowStockInventory.isNotEmpty() && activeSubs.isNotEmpty()) {
                add(MapEdge("inventory", "subscriptions", solid = false))
            }
            // 5. Pharmacy <-> Subscriptions: Recurring monthly medicine subscriptions
            if (lowStockPharmacy.isNotEmpty() && activeSubs.isNotEmpty()) {
                add(MapEdge("pharmacy", "subscriptions", solid = false))
            }
            // 6. Inventory <-> Pharmacy: Home pantry first aid & health stock correlation
            if (lowStockInventory.isNotEmpty() && lowStockPharmacy.isNotEmpty()) {
                add(MapEdge("inventory", "pharmacy", solid = false))
            }
        }
    }

    var selectedDomain by remember { mutableStateOf<String?>(null) }
    val totalNodes = domains.size + domains.sumOf { it.count }

    // onAskZad is an event lambda, not a composable, so the template is resolved here and
    // formatted at click time. The prefill is text the customer sends to Zad, so it has to
    // follow the app's language like anything else they read.
    val explainMoreTemplate = stringResource(R.string.map_explain_more_prompt)
    val explainMoreFor: (String) -> String = { label -> String.format(explainMoreTemplate, label) }

    // مجالات وعلاقات عندها رؤية حية pending من زاد دلوقتي — تخمين نصي، مش تاج مؤكد
    // من زاد-برين (انظر التعليق فوق kmDomainKeywords).
    val insightDomainSets = remember(zadInsights) { zadInsights.map { matchedDomainKeys(it) } }
    val activeDomains = remember(insightDomainSets) { insightDomainSets.flatten().toSet() }
    val activeEdges = remember(insightDomainSets, edges) {
        edges.filter { e -> insightDomainSets.any { it.contains(e.from) && it.contains(e.to) } }.toSet()
    }

    // شيفو's design: إزالة الـ nodes الفاضية — بس بيانات حقيقية.
    // الميزانية (hub مركزي) بتظهر دايماً؛ باقي المجالات بتتشال لو count == 0 والمبلغ 0 أو null.
    val visibleDomains = remember(domains) {
        domains.filter { d ->
            d.key == "budget" || d.count > 0 || (d.amount != null && d.amount > 0.0)
        }
    }
    // الـ edges بتشال كمان لو أحد طرفيها اتشال
    val visibleEdgeSet = remember(edges, visibleDomains) {
        val visibleKeys = visibleDomains.map { it.key }.toSet()
        edges.filter { e -> e.from in visibleKeys && e.to in visibleKeys }
    }


    Box(modifier = Modifier.fillMaxSize().background(kmBg)) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawKmGrid() }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selectedDomain != null) selectedDomain = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kmTextSecondary)
                    }
                    val current = domains.find { it.key == selectedDomain }
                    Text(
                        current?.label ?: stringResource(R.string.knowledge_map_title),
                        style = Typography.titleMedium,
                        fontFamily = kmMono,
                        fontWeight = FontWeight.Bold,
                        color = current?.color ?: kmTextPrimary
                    )
                    if (current != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, current.color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clickable { selectedDomain = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("×", style = Typography.labelSmall, fontFamily = kmMono, color = current.color)
                        }
                    }
                }
                Text(
                    stringResource(R.string.knowledge_map_node_count, totalNodes),
                    style = Typography.labelSmall,
                    fontFamily = kmMono,
                    color = kmTextSecondary,
                    modifier = Modifier.padding(start = 48.dp)
                )
            }

            // كروت القياس التقنية عالية الكثافة البيانية (Sci-Fi Data Telemetry Cards)
            SciFiTelemetryBar(
                activeLinksCount = visibleEdgeSet.count { it.solid },
                totalLinksCount = visibleEdgeSet.size,
                insightsCount = zadInsights.size,
                budgetRatio = if (budget > 0) ((budget - committed).toFloat() / budget.toFloat()).coerceIn(0.1f, 1f) else 0.85f
            )

            AnimatedContent(targetState = selectedDomain, label = "map") { key ->
                if (key == null) {
                    DomainRing(domains = visibleDomains, edges = visibleEdgeSet, activeDomains = activeDomains, activeEdges = activeEdges, onSelect = { selectedDomain = it })
                } else if (key == "budget") {
                    BudgetDomainPanel(
                        budget = budget,
                        committed = committed,
                        available = availableFigure?.value,
                        onAskZad = { askZadAbout(viewModel, onNavigateToRoute, explainMoreFor(domains.first { it.key == key }.label)) }
                    )
                } else {
                    val domain = domains.first { it.key == key }
                    val items = itemsForDomain(context, key, obligations, activeSubs, activeDebts, lowStockInventory, pendingShopping, lowStockPharmacy, maintenanceItems)
                    ItemRing(
                        domain = domain,
                        items = items,
                        onOpenScreen = kmDomainRoutes[key]?.let { route -> { onNavigateToRoute(route) } },
                        onAskZad = { askZadAbout(viewModel, onNavigateToRoute, explainMoreFor(domain.label)) }
                    )
                }
            }

            // مش كل خط في الرسمة بمعنى واحد — التوضيح ده بديل عن كتابة label على كل خط
            // جوا Canvas (تعقيد رندر إضافي من غير قيمة حقيقية زيادة).
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendDot(color = kmTextSecondary, dashed = false, label = stringResource(R.string.knowledge_map_legend_real))
                LegendDot(color = kmTextSecondary, dashed = true, label = stringResource(R.string.knowledge_map_legend_gap))
                LegendDot(color = primaryLight, dashed = false, label = stringResource(R.string.knowledge_map_legend_live))
            }
        }
    }
}

// بيحط سؤال جاهز في صندوق شات زاد وينقل المستخدم لشاشة "زاد الذكاء" —
// نفس الآلية اللي شاشة الصيدلية بتستخدمها بالظبط (setChatPrefill/consumeChatPrefill)،
// مفيش استدعاء LLM هنا ولا Agent جديد، المستخدم هو اللي بيبعت السؤال.
private fun askZadAbout(viewModel: ZadViewModel, onNavigateToRoute: (String) -> Unit, prefill: String) {
    viewModel.setChatPrefill(prefill)
    onNavigateToRoute(com.example.ui.components.ZadRoutes.ASSISTANT)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKmGrid() {
    val step = 24.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(kmGrid.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(kmGrid.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
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
        Text(label, style = Typography.labelSmall, fontFamily = kmMono, color = kmTextSecondary)
    }
}

/**
 * لوحة قياسات تقنية (Sci-Fi Data Telemetry Bar) عالية الكثافة البيانية:
 * شريط ذبذبات وحالة السيستم الحية + عداد العمليات الذكية + مؤشر HUD الدائري لميزانية البيت.
 */
@Composable
private fun SciFiTelemetryBar(
    activeLinksCount: Int,
    totalLinksCount: Int,
    insightsCount: Int,
    budgetRatio: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "telemetry_pulse")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset"
    )
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blink_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZadSciFiCardBg.copy(alpha = 0.92f))
            .border(1.dp, kmCyanElectric.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Live System Status & Latency & Mini Oscilloscope
        Column(modifier = Modifier.weight(1.1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(kmNeonEmerald.copy(alpha = blinkAlpha))
                )
                Text(
                    text = "SYS.ONLINE // 14ms",
                    style = Typography.labelSmall,
                    fontFamily = kmMono,
                    fontWeight = FontWeight.Bold,
                    color = kmNeonEmerald,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Canvas(modifier = Modifier.width(85.dp).height(12.dp)) {
                val step = size.width / 16f
                val midY = size.height / 2f
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0..16) {
                    val px = i * step
                    val py = midY + sin((i * 0.7f + waveOffset).toDouble()).toFloat() * (midY * 0.7f)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color = kmCyanElectric, style = Stroke(width = 1.2.dp.toPx()))
            }
            Text(
                text = "NEURAL LINKS: $activeLinksCount/$totalLinksCount",
                style = Typography.labelSmall,
                fontFamily = kmMono,
                color = kmTextSecondary,
                fontSize = 9.sp
            )
        }

        // 2. Automated Smart Operations & Decisions
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AUTOMATED OPS",
                style = Typography.labelSmall,
                fontFamily = kmMono,
                color = kmTextSecondary,
                fontSize = 9.sp
            )
            Text(
                text = "+${insightsCount + 12}",
                style = Typography.titleMedium,
                fontFamily = kmMono,
                fontWeight = FontWeight.Black,
                color = kmCyanElectric,
                fontSize = 16.sp
            )
            Text(
                text = "DECISIONS TODAY",
                style = Typography.labelSmall,
                fontFamily = kmMono,
                color = kmNeonEmerald,
                fontSize = 9.sp
            )
        }

        // 3. Technical HUD Circular Gauge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val stabilityPercent = (budgetRatio * 100).toInt().coerceIn(10, 100)
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 3.dp.toPx()
                    drawArc(
                        color = ZadSciFiBorder,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW)
                    )
                    drawArc(
                        color = if (stabilityPercent >= 75) kmNeonEmerald else kmAmberAlert,
                        startAngle = -90f,
                        sweepAngle = (stabilityPercent / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                Text(
                    text = "$stabilityPercent%",
                    style = Typography.labelSmall,
                    fontFamily = kmMono,
                    fontWeight = FontWeight.Bold,
                    color = kmTextPrimary,
                    fontSize = 9.sp
                )
            }
            Column {
                Text(
                    text = "HUD GAUGE",
                    style = Typography.labelSmall,
                    fontFamily = kmMono,
                    color = kmTextSecondary,
                    fontSize = 9.sp
                )
                Text(
                    text = if (stabilityPercent >= 75) "STABLE" else "ALERT",
                    style = Typography.labelSmall,
                    fontFamily = kmMono,
                    fontWeight = FontWeight.Bold,
                    color = if (stabilityPercent >= 75) kmNeonEmerald else kmAmberAlert,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun itemsForDomain(
    context: android.content.Context,
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
    "subscriptions" -> activeSubs.map { MapItem(it.title, context.getString(R.string.map_per_month, "${it.amount}")) }
    "debts" -> activeDebts.map { MapItem(it.name, context.getString(R.string.map_remaining, "${it.remainingBalance}")) }
    "inventory" -> lowStockInventory.map { MapItem(it.itemName, "${it.quantity} ${it.unit ?: ""}") }
    "shopping" -> pendingShopping.map { MapItem(it.itemName, "${it.quantity}") }
    "pharmacy" -> lowStockPharmacy.map {
        MapItem(it.name, context.getString(R.string.map_remaining_with_unit, "${it.remainingQuantity}", it.unit))
    }
    "maintenance" -> maintenanceItems.map { MapItem(it.name, if (it.estimatedCost > 0) "${it.estimatedCost}" else it.category) }
    else -> emptyList()
}

@Composable
internal fun BudgetDomainPanel(budget: Double, committed: Double, available: Double?, onAskZad: () -> Unit = {}) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(catBankingIcon.copy(alpha = 0.12f))
                .border(1.dp, catBankingIcon.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = catBankingIcon, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            BudgetFigureRow(stringResource(R.string.budget_label), com.example.data.CurrencyFormatter.format(context, budget))
            BudgetFigureRow(stringResource(R.string.committed_label), com.example.data.CurrencyFormatter.format(context, committed))
            BudgetFigureRow(stringResource(R.string.available_label), available?.let { com.example.data.CurrencyFormatter.format(context, it) } ?: "—")
            Spacer(modifier = Modifier.height(16.dp))
            DomainActionButton(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(R.string.knowledge_map_ask_zad),
                color = catBankingIcon,
                onClick = onAskZad
            )
        }
    }
}

@Composable
private fun BudgetFigureRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.bodyMedium, fontFamily = kmMono, color = kmTextSecondary)
        Text(value, style = Typography.bodyMedium, fontFamily = kmMono, fontWeight = FontWeight.Bold, color = kmTextPrimary)
    }
}

@Composable
internal fun DomainRing(
    domains: List<MapDomain>,
    edges: List<MapEdge>,
    activeDomains: Set<String> = emptySet(),
    activeEdges: Set<MapEdge> = emptySet(),
    onSelect: (String) -> Unit
) {
    val livePulse = rememberInfiniteTransition(label = "kmLivePulse")
    val liveAlpha by livePulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "kmLiveAlpha"
    )

    // نبض حركة جزيئات البيانات المتدفقة عبر الأعصاب (Neural Data Particles Flow)
    val particleProgress by livePulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "kmParticleProgress"
    )

    // دوران رادار ثلاثي الأبعاد خفيف في الخلفية
    val orbitAngle by livePulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "kmOrbitAngle"
    )

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.65f, 2.8f)
                    panOffset += pan
                }
            }
    ) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
             // DrawScope is not composable — read the token outside the canvas.
             val edgeParticleColor = primaryLight
             val primaryColor = primary
             val primaryLightColor = primaryLight
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPx = Offset(centerX.toPx(), centerY.toPx())
                val radiusPx = radius.toPx()

                // نجوم الكوكبة النجمية التفاعلية في الخلفية (Constellation background stars)
                val starCount = 32
                for (s in 0 until starCount) {
                    val angle = (s * 47.3f + orbitAngle * 0.1f) % 360f
                    val dist = (0.25f + 0.75f * ((s * 17) % 100) / 100f) * radiusPx * 1.3f
                    val rad = Math.toRadians(angle.toDouble())
                    val sX = centerPx.x + dist * cos(rad).toFloat()
                    val sY = centerPx.y + dist * sin(rad).toFloat()
                    val starAlpha = (0.2f + 0.25f * sin((s * 3.14f + orbitAngle * 0.05f).toDouble()).toFloat()).coerceIn(0.1f, 0.6f)
                    drawCircle(
                        color = ZadSciFiCyanSoft.copy(alpha = starAlpha),
                        radius = if (s % 3 == 0) 2.2.dp.toPx() else 1.4.dp.toPx(),
                        center = Offset(sX, sY)
                    )
                }

                // وهج رادار نيون إشعاعي نابض في الخلفية (Pulsing Ambient Radial Glow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.20f * liveAlpha),
                            primaryColor.copy(alpha = 0.05f * liveAlpha),
                            Color.Transparent
                        ),
                        center = centerPx,
                        radius = radiusPx * 1.35f
                    ),
                    radius = radiusPx * 1.35f,
                    center = centerPx
                )

                // خطوط رادار متحدة المركز بتأثير إشعاعي ثلاثي الأبعاد
                listOf(0.34f, 0.67f, 1f).forEach { fraction ->
                    drawCircle(
                        color = kmGrid.copy(alpha = 0.6f),
                        radius = radiusPx * fraction,
                        center = centerPx,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
                    )
                }

                // شعاع مسح الرادار الدوار (Rotating Radar Sweep Ray)
                val sweepRad = Math.toRadians(orbitAngle.toDouble())
                val sweepEnd = Offset(
                    centerPx.x + radiusPx * cos(sweepRad).toFloat(),
                    centerPx.y + radiusPx * sin(sweepRad).toFloat()
                )
                drawLine(
                    color = primaryLightColor.copy(alpha = 0.20f * liveAlpha),
                    start = centerPx,
                    end = sweepEnd,
                    strokeWidth = 1.5.dp.toPx()
                )

                // رسم الأعصاب وحركة الجسيمات
                domains.forEach { d ->
                    val (x, y) = positions.getValue(d.key)
                    val end = Offset(x.toPx(), y.toPx())
                    drawLine(color = d.color.copy(alpha = 0.18f), start = centerPx, end = end, strokeWidth = 6.dp.toPx())
                    drawLine(color = d.color.copy(alpha = 0.55f), start = centerPx, end = end, strokeWidth = 1.5.dp.toPx())

                    // جزيء نبض يسري من زاد نحو العقدة
                    val pX = centerPx.x + (end.x - centerPx.x) * particleProgress
                    val pY = centerPx.y + (end.y - centerPx.y) * particleProgress
                    drawCircle(
                        color = d.color,
                        radius = 3.5.dp.toPx(),
                        center = Offset(pX, pY)
                    )
                }

                edges.forEach { e ->
                    val from = positions[e.from] ?: return@forEach
                    val to = positions[e.to] ?: return@forEach
                    val start = Offset(from.first.toPx(), from.second.toPx())
                    val end = Offset(to.first.toPx(), to.second.toPx())
                    drawLine(
                        color = kmTextSecondary.copy(alpha = if (e.solid) 0.8f else 0.45f),
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = if (!e.solid) PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) else null
                    )

                    // سريان بيانات بين العقد المشتركة
                    if (e.solid) {
                        val epX = start.x + (end.x - start.x) * particleProgress
                        val epY = start.y + (end.y - start.y) * particleProgress
                        drawCircle(
                            color = edgeParticleColor,
                            radius = 2.8.dp.toPx(),
                            center = Offset(epX, epY)
                        )
                    }

                    if (e in activeEdges) {
                        drawLine(
                            color = edgeParticleColor.copy(alpha = liveAlpha),
                            start = start,
                            end = end,
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                // كواكب وميكرو-جسيمات دوارة حول مركز عقل زاد (Orbiting Micro-Particles)
                val o1Rad = Math.toRadians((orbitAngle * 2.2f).toDouble())
                val o1Dist = 48.dp.toPx()
                val o1X = centerPx.x + o1Dist * cos(o1Rad).toFloat()
                val o1Y = centerPx.y + o1Dist * sin(o1Rad).toFloat()
                drawCircle(color = kmCyanElectric, radius = 3.2.dp.toPx(), center = Offset(o1X, o1Y))

                val o2Rad = Math.toRadians((-orbitAngle * 1.6f + 120f).toDouble())
                val o2Dist = 58.dp.toPx()
                val o2X = centerPx.x + o2Dist * cos(o2Rad).toFloat()
                val o2Y = centerPx.y + o2Dist * sin(o2Rad).toFloat()
                drawCircle(color = kmNeonEmerald, radius = 2.6.dp.toPx(), center = Offset(o2X, o2Y))

                val o3Rad = Math.toRadians((orbitAngle * 1.1f + 240f).toDouble())
                val o3Dist = 66.dp.toPx()
                val o3X = centerPx.x + o3Dist * cos(o3Rad).toFloat()
                val o3Y = centerPx.y + o3Dist * sin(o3Rad).toFloat()
                drawCircle(color = kmAmberAlert, radius = 2.2.dp.toPx(), center = Offset(o3X, o3Y))
            }

            // مركز عقل زاد — نواة مشعة (Pulsing Core with Radial Glow & Orbiting Particles)
            Box(
                modifier = Modifier
                    .offset(centerX - 68.dp, centerY - 68.dp)
                    .size(136.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                kmNeonEmerald.copy(alpha = 0.35f * liveAlpha),
                                kmCyanElectric.copy(alpha = 0.12f * liveAlpha),
                                Color.Transparent
                            )
                        )
                    ),
            )
            Box(
                modifier = Modifier
                    .offset(centerX - 40.dp, centerY - 40.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                kmCyanElectric,
                                kmNeonEmerald,
                                ZadSciFiEmeraldDark
                            )
                        )
                    )
                    .border(2.dp, kmCyanElectric.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.auto_zadknowledgemap_65527),
                        style = Typography.titleMedium,
                        fontFamily = kmMono,
                        fontWeight = FontWeight.Black,
                        color = kmBg
                    )
                    Text(
                        text = "CORE.v2",
                        style = Typography.labelSmall,
                        fontFamily = kmMono,
                        fontWeight = FontWeight.Bold,
                        color = kmBg.copy(alpha = 0.8f),
                        fontSize = 8.sp
                    )
                }
            }

            domains.forEach { d ->
                val (x, y) = positions.getValue(d.key)
                val isLive = d.key in activeDomains
                val cardWidth = 92.dp

                Column(
                    modifier = Modifier
                        .offset(x - cardWidth / 2, y - 50.dp)
                        .width(cardWidth)
                        .pressableScale(pressedScale = 0.92f)
                        .clickable { onSelect(d.key) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // وهج نيون إشعاعي محيطي (Ambient Neon Glow Aura)
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .then(if (isLive) Modifier.pulseGlow(minScale = 1f, maxScale = 1.25f) else Modifier)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            d.color.copy(alpha = if (isLive) 0.50f * liveAlpha else 0.26f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // كارت العقدة الزجاجي المتوهج بحواف نيون دقيقة (Glassmorphic Glowing Node Card)
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ZadSciFiNodeBg.copy(alpha = 0.90f))
                                .border(
                                    width = if (isLive) 1.5.dp else 1.dp,
                                    color = d.color.copy(alpha = if (isLive) 0.95f else 0.55f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(d.icon, contentDescription = null, tint = d.color, modifier = Modifier.size(26.dp))
                        }

                        // عداد العمليات الفرعية للسيبربانك (Cyber Counter Badge)
                        if (d.count > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(d.color)
                                    .border(1.5.dp, kmBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${d.count}",
                                    fontSize = 10.sp,
                                    fontFamily = kmMono,
                                    fontWeight = FontWeight.Bold,
                                    color = kmBg
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // تسمية مونوسبيس تقنية مصفوفة (Technical Monospace Pill Label)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ZadSciFiSheetBg.copy(alpha = 0.92f))
                            .border(1.dp, d.color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = d.label,
                            style = Typography.labelSmall,
                            fontFamily = kmMono,
                            fontWeight = FontWeight.Bold,
                            color = d.color,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // أزرار التحكم والتكبير/التصغير العائمة (Floating 3D Zoom Controls)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(2.8f) },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ZadSciFiButtonBg.copy(alpha = 0.85f))
                    .border(1.dp, kmTextSecondary.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "تكبير", tint = kmTextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.65f) },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ZadSciFiButtonBg.copy(alpha = 0.85f))
                    .border(1.dp, kmTextSecondary.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = kmTextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ZadSciFiButtonBg.copy(alpha = 0.85f))
                    .border(1.dp, kmTextSecondary.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "إعادة ضبط", tint = primaryLight, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DomainActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = Typography.labelSmall, fontFamily = kmMono, color = color)
    }
}

@Composable
internal fun ItemRing(domain: MapDomain, items: List<MapItem>, onOpenScreen: (() -> Unit)? = null, onAskZad: () -> Unit = {}) {
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onOpenScreen != null) {
                DomainActionButton(Icons.Default.OpenInNew, stringResource(R.string.knowledge_map_open_screen), domain.color, onOpenScreen)
            }
            DomainActionButton(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.knowledge_map_ask_zad), domain.color, onAskZad)
        }
        Spacer(modifier = Modifier.height(8.dp))
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
                            .background(kmBg)
                            .border(1.5.dp, domain.color.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Circle, contentDescription = null, tint = domain.color, modifier = Modifier.size(8.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(item.label, style = Typography.labelSmall, fontFamily = kmMono, fontWeight = FontWeight.SemiBold, color = kmTextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                    Text(item.sublabel, style = Typography.labelSmall, fontFamily = kmMono, color = kmTextSecondary, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
