package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieConstants
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.ZadShoppingItem
import com.example.data.AiPriceEstimate
import com.example.data.AffiliateProduct
import com.example.data.GrocerySuggestion
import com.example.ui.components.GlassCard
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.ZadTransitions
import com.example.ui.components.pressableScale
import com.example.ui.components.ZadListCard
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.widgets.AffiliateProductCard
import com.example.ui.widgets.AffiliateConsentBanner

import com.example.ui.widgets.AffiliateEmptyState
import com.example.ui.widgets.AffiliateLoadingSkeleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ShoppingListScreen"

@Composable
fun ShoppingListScreen(
    viewModel: ZadViewModel,
    onNavigateToAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val shoppingList by viewModel.shoppingList.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val availableFigure by viewModel.availableFigure.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val scope = rememberCoroutineScope()

    val unpurchased = shoppingList.filter { !it.isPurchased }

    val grocerySuggestions by viewModel.grocerySuggestions.collectAsState()
    val affiliateProducts by viewModel.affiliateProducts.collectAsState()
    val matchedProductId by viewModel.matchedProductId.collectAsState()
    val isMatchingProduct by viewModel.isMatchingProduct.collectAsState()
    val affiliateConsentGiven by viewModel.affiliateConsentGiven.collectAsState()
    val affiliateStats by viewModel.affiliateStats.collectAsState()
    // Consent was one-way: it could be granted from the banner below and never
    // withdrawn from anywhere, and `loadAffiliateStats()` — the user's own click
    // history, RLS-scoped — had no caller at all. Both are fixed by the footer
    // that renders under the suggestion once consent exists.
    LaunchedEffect(affiliateConsentGiven) {
        if (affiliateConsentGiven) viewModel.loadAffiliateStats()
    }
    var recentlyPurchasedItemName by remember { mutableStateOf("") }
    var justCheckedItemName by remember { mutableStateOf<String?>(null) }

    val matchedProduct = remember(matchedProductId, affiliateProducts) {
        matchedProductId?.let { id -> affiliateProducts.find { it.id == id } }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPriority by remember { mutableStateOf("الكل") }
    var priceEstimates by remember { mutableStateOf<Map<String, AiPriceEstimate>>(emptyMap()) }
    var isLoadingPrices by remember { mutableStateOf(false) }
    var showPrediction by remember { mutableStateOf(false) }
    var predictionText by remember { mutableStateOf("") }

    // estimatedPrice بيتحسب من متوسط تاريخي حقيقي وقت الإضافة (ZadViewModel.addShoppingItem)،
    // لكن أي صنف اتضاف بدون تاريخ إنفاق سابق فاضل 0.0 لحد ما تقدير AI (priceEstimates تحت)
    // يوصله — كان مستبعد تماماً من الإجمالي، فالميزانية المعروضة كانت بتقل عن الحقيقة
    // لأي صنف جديد كليًا.
    val pricedUnits = unpurchased.map {
        val perUnit = if (it.estimatedPrice > 0) it.estimatedPrice else priceEstimates[it.itemName]?.avgPrice ?: 0.0
        it to perUnit
    }
    val totalPrice = pricedUnits.sumOf { (item, perUnit) -> perUnit * item.quantity }
    // "٠ ج.م" ورقم مؤكد مش نفس الحاجة. لو مفيش ولا صنف عندنا له سعر — لا تاريخ ولا تقدير —
    // فإحنا **مش عارفين** تكلفة السلة، والصفر بيتقري على إنها ببلاش. نفس التفرقة اللي
    // BudgetMath بيعملها بين `remaining = 0` و`remaining = null`.
    val basketPriceKnown = pricedUnits.any { (_, perUnit) -> perUnit > 0.0 }
    // Phase 0 — كان ده بيحسب "الميزانية المتبقية" لوحده (budget - كل المصاريف من الأول)
    // من غير ما يخصم المحجوز (التزامات+اشتراكات) ولا يلتزم بحدود دورة الراتب، فكان بيديله رقم
    // مختلف عن "المتاح" اللي شاشات تانية بتعرضه بنفس فرق قيمة المحجوز بالظبط. دلوقتي بيقرا
    // نفس القيمة الموثوقة (zad_budget_state عبر ZadViewModel) اللي الداشبورد والميزانية بيعرضوها.
    val budgetRemaining = availableFigure?.value ?: remainingBalance ?: (budget - transactions.filter { it.isExpense }.sumOf { it.amount })
    val budgetPct = if (budget > 0) (totalPrice / budget * 100).toInt().coerceIn(0, 100) else 0

    LaunchedEffect(Unit) {
        viewModel.fetchGrocerySuggestions()
    }

    LaunchedEffect(justCheckedItemName) {
        if (justCheckedItemName != null) {
            delay(1200)
            justCheckedItemName = null
        }
    }

    LaunchedEffect(shoppingList) {
        val itemsNeedingPrice = shoppingList.filter { it.estimatedPrice <= 0 && !it.isPurchased }.take(5)
        if (itemsNeedingPrice.isNotEmpty()) {
            isLoadingPrices = true
            val estimates = mutableMapOf<String, AiPriceEstimate>()
            for (item in itemsNeedingPrice) {
                val estimate = com.example.data.ZadAiRepository.estimatePrice(item.itemName)
                if (estimate != null) {
                    estimates[item.itemName] = estimate
                    // يتحفظ على الصف عشان مايتسألش تاني كل فتحة، وعشان الإجمالي مايرجعش
                    // صفر لو النموذج كان واقع وقت الفتحة الجاية.
                    viewModel.persistEstimatedPrice(item.id, estimate.avgPrice)
                }
            }
            priceEstimates = estimates
            isLoadingPrices = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ShoppingBudgetHeader(totalPrice = totalPrice, budgetRemaining = budgetRemaining, budgetPct = budgetPct, priceKnown = basketPriceKnown) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    viewModel.refreshSmartShopping()
                                }
                            },
                            modifier = Modifier.weight(1f).pressableScale(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primary)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تعبئة ذكية", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { shareOnWhatsApp(context, shoppingList) },
                            modifier = Modifier.weight(1f).pressableScale(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("واتساب", fontSize = 13.sp)
                        }
                    }
                }

                item {
                    PriorityFilterChips(selected = selectedPriority, onSelected = { selectedPriority = it })
                }

                if (grocerySuggestions.isNotEmpty()) {
                    item {
                        GrocerySuggestionsCard(
                            suggestions = grocerySuggestions,
                            onAdd = { suggestion ->
                                val qty = Regex("\\d+").find(suggestion.quantity)?.value?.toIntOrNull() ?: 1
                                viewModel.addShoppingItem(
                                    ZadShoppingItem(itemName = suggestion.name, quantity = qty, estimatedPrice = 0.0, store = "")
                                )
                            }
                        )
                    }
                }

                if (unpurchased.isEmpty()) {
                    item { SmartEmptyState() }
                } else {
                    val filtered = when (selectedPriority) {
                        "حرج" -> unpurchased.filter { it.priority == "high" }
                        "متوسط" -> unpurchased.filter { it.priority == "medium" }
                        "منخفض" -> unpurchased.filter { it.priority == "low" }
                        else -> unpurchased
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "لا توجد عناصر بهذا التصنيف",
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                textAlign = TextAlign.Center,
                                color = onSurfaceVariant
                            )
                        }
                    } else {
                        itemsIndexed(filtered, key = { _, item -> item.id }) { index, item ->
                            androidx.compose.animation.AnimatedVisibility(visible = true, enter = ZadTransitions.listItemEnter(index)) {
                                EnhancedShoppingItemCard(
                                    item = item,
                                    priceEstimate = priceEstimates[item.itemName],
                                    onCheck = {
                                        recentlyPurchasedItemName = item.itemName
                                        justCheckedItemName = item.itemName
                                        viewModel.matchProduct(item.itemName)
                                        viewModel.toggleShoppingItemPurchased(item.id)
                                    },
                                    onDelete = { viewModel.deleteShoppingItem(item.id) }
                                )
                            }
                        }
                    }

                    // Amazon affiliate suggestion
                    if (recentlyPurchasedItemName.isNotBlank()) {
                        if (!affiliateConsentGiven) {
                            item {
                                AffiliateConsentBanner(
                                    onAccept = { viewModel.setAffiliateConsent(true) }
                                )
                            }
                        } else if (isMatchingProduct) {
                            item { AffiliateLoadingSkeleton() }
                        } else if (matchedProduct != null) {
                            item {
                                Column {
                                    Text(
                                        "اقتراح: بديل من أمازون",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = onSurfaceVariant
                                    )
                                    AffiliateProductCard(
                                        product = matchedProduct,
                                        onBuyClick = {
                                            viewModel.recordAffiliateClick(matchedProduct.id, "shopping")
                                            com.example.data.AffiliateHelper.openProduct(context, matchedProduct)
                                        }
                                    )
                                    AffiliateTransparencyFooter(
                                        clickCount = affiliateStats.size,
                                        onWithdraw = { viewModel.setAffiliateConsent(false) }
                                    )
                                }
                            }
                        } else if (!isMatchingProduct && matchedProductId == null) {
                            item {
                                AffiliateEmptyState(searchedTerm = recentlyPurchasedItemName)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 32.dp),
            containerColor = primary,
            contentColor = onPrimary,
            shape = RoundedCornerShape(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "إضافة") }

        androidx.compose.animation.AnimatedVisibility(
            visible = justCheckedItemName != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            com.example.ui.components.ZadListCard(
                shape = RoundedCornerShape(50),
                contentPadding = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZadLottieAsset(
                        resId = R.raw.lottie_success_check,
                        modifier = Modifier.size(28.dp),
                        iterations = 1,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "تم شراء ${justCheckedItemName.orEmpty()}",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = onSurface
                    )
                }
            }
        }
    }
    if (showAddDialog) {
        AddShoppingItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, qty, price, store ->
                viewModel.addShoppingItem(ZadShoppingItem(itemName = name, quantity = qty, estimatedPrice = price, store = store))
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ShoppingBudgetHeader(totalPrice: Double, budgetRemaining: Double, budgetPct: Int, priceKnown: Boolean = true) {
    val context = LocalContext.current
    val isOverBudget = totalPrice > budgetRemaining && budgetRemaining > 0
    com.example.ui.components.ZadListCard(
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.Transparent,
        contentPadding = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(
                    if (isOverBudget) listOf(Color(0xFFC62828), Color(0xFFE53935))
                    else listOf(primaryDark, primary)
                ))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سلة زاد الذكية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الميزانية المتبقية", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text(
                            com.example.data.CurrencyFormatter.format(context, budgetRemaining),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (priceKnown) com.example.data.CurrencyFormatter.format(context, totalPrice) else "—",
                        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    if (budgetPct > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$budgetPct% من الميزانية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                if (isOverBudget) {
                    Spacer(Modifier.height(8.dp))
                    Text("هذه القائمة تتجاوز الميزانية المتبقية!", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PriorityFilterChips(selected: String, onSelected: (String) -> Unit) {
    val priorities = listOf("الكل", "حرج", "متوسط", "منخفض")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(priorities) { p ->
            val isSelected = p == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(p) },
                label = { Text(p, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun GrocerySuggestionsCard(suggestions: List<GrocerySuggestion>, onAdd: (GrocerySuggestion) -> Unit) {
    GlassCard(
        containerColor = surface.copy(alpha = 0.9f),
        borderColor = onSurface.copy(alpha = 0.08f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(catEntertainBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = catEntertainIcon, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("قد تحتاج أيضاً", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(suggestion.name, style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = onSurface)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(secondaryContainer).padding(horizontal = 6.dp, vertical = 2.dp)
                            ) { Text(suggestion.quantity, style = Typography.bodySmall, color = onSecondaryContainer) }
                        }
                        if (suggestion.reason.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(suggestion.reason, style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onAdd(suggestion) }, modifier = Modifier.size(36.dp).pressableScale()) {
                        Icon(Icons.Default.AddCircle, contentDescription = "إضافة للقائمة", tint = primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/**
 * The mockup's `SHOP_ITEMS` row: name over the price range on the leading edge,
 * one priority pill on the trailing edge, inside a plain 16dp white card.
 *
 * The row used to stack up to five dark chips (quantity, price, predicted
 * days-left, store, priority dot) built out of `primaryContainer`/
 * `secondaryContainer` — both near-black in this palette, so a shopping list
 * read as a wall of dark blobs. The same facts survive as one grey caption line
 * plus the pill; nothing is dropped, it just stops shouting.
 */
@Composable
private fun EnhancedShoppingItemCard(
    item: ZadShoppingItem,
    priceEstimate: AiPriceEstimate?,
    onCheck: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val priorityColor = when (item.priority) {
        "high" -> dangerColor
        "medium" -> secondaryDark
        else -> primary
    }
    val priorityLabel = when (item.priority) {
        "high" -> stringResource(R.string.priority_critical)
        "medium" -> stringResource(R.string.priority_medium)
        else -> stringResource(R.string.priority_low)
    }
    val priceText = when {
        priceEstimate != null ->
            com.example.data.CurrencyFormatter.formatNumber(context, priceEstimate.lowPrice) + "–" +
                com.example.data.CurrencyFormatter.format(context, priceEstimate.highPrice)
        item.estimatedPrice > 0 -> com.example.data.CurrencyFormatter.format(context, item.estimatedPrice)
        else -> null
    }
    val caption = listOfNotNull(
        "× ${item.quantity}",
        priceText,
        item.store?.takeIf { it.isNotBlank() },
        item.predictedDaysLeft?.takeIf { it <= 3 }?.let { stringResource(R.string.runs_out_in_days, it) }
    ).joinToString(" · ")

    ZadListCard(
        modifier = Modifier.pressableScale(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCheck, modifier = Modifier.size(36.dp).pressableScale()) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(outlineVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "تم", tint = onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.itemName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (caption.isNotBlank()) {
                    Text(caption, fontSize = 11.5.sp, color = textTertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                priorityLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = priorityColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(priorityColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).pressableScale()) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun SmartEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZadLottieAsset(
            resId = R.raw.lottie_empty_box,
            modifier = Modifier.size(140.dp),
            iterations = LottieConstants.IterateForever,
            contentDescription = "قائمة التسوق فارغة"
        )
        Spacer(Modifier.height(12.dp))
        Text("قائمة التسوق فارغة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = onSurface)
        Spacer(Modifier.height(8.dp))
        Text("زاد سيضيف النواقص تلقائياً!", fontSize = 14.sp, color = onSurfaceVariant)
    }
}

@Composable
private fun AddShoppingItemDialog(onDismiss: () -> Unit, onConfirm: (name: String, qty: Int, price: Double, store: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qtyStr by remember { mutableStateOf("1") }
    var priceStr by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("إضافة منتج للقائمة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المنتج") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = qtyStr, onValueChange = { qtyStr = it }, label = { Text("الكمية") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("السعر التقديري") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = store, onValueChange = { store = it }, label = { Text("المتجر (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyStr.trim().toIntOrNull() ?: 1
                    val price = priceStr.trim().toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onConfirm(name.trim(), qty, price, store.trim())
                },
                modifier = Modifier.pressableScale(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) { Text("إضافة", color = onPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = onSurfaceVariant) } }
    )
}

private fun shareOnWhatsApp(context: Context, shoppingList: List<ZadShoppingItem>) {
    val unpurchased = shoppingList.filter { !it.isPurchased }
    val shareText = unpurchased.joinToString("\n") { "- ${it.itemName} (${it.quantity})" }
    val total = unpurchased.sumOf { it.estimatedPrice * it.quantity }
    val full = "قائمة تسوق زاد:\n$shareText\n\nالإجمالي: ${com.example.data.CurrencyFormatter.format(context, total)}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, full)
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "مشاركة القائمة"))
    } catch (e: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, full)
        }
        context.startActivity(Intent.createChooser(fallback, "مشاركة القائمة"))
    }
}

/**
 * Transparency line under the Amazon suggestion: how many of these the user has
 * actually clicked, and a way out.
 *
 * Consent used to be a one-way door — `AffiliateConsentBanner` could set it true
 * and nothing anywhere could set it back — and the click history the app records
 * was never shown to the person it is about. A disclosure the user cannot audit
 * or revoke is not consent.
 */
@Composable
private fun AffiliateTransparencyFooter(clickCount: Int, onWithdraw: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.affiliate_clicks_recorded, clickCount),
            style = Typography.labelSmall,
            color = textTertiary,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.affiliate_withdraw_consent),
            style = Typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onWithdraw() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
