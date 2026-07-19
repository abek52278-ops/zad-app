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
import com.example.data.ZadShoppingItem
import com.example.data.AiPriceEstimate
import com.example.data.AffiliateProduct
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.widgets.AffiliateProductCard
import com.example.ui.widgets.AffiliateConsentBanner

import com.example.ui.widgets.AffiliateEmptyState
import com.example.ui.widgets.AffiliateLoadingSkeleton
import kotlinx.coroutines.launch

private const val TAG = "ShoppingListScreen"

@Composable
fun ShoppingListScreen(
    viewModel: ZadViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    val shoppingList by viewModel.shoppingList.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val scope = rememberCoroutineScope()

    val unpurchased = shoppingList.filter { !it.isPurchased }
    val totalPrice = unpurchased.sumOf { it.estimatedPrice * it.quantity }
    val budgetRemaining = budget - transactions.filter { it.isExpense }.sumOf { it.amount }
    val budgetPct = if (budget > 0) (totalPrice / budget * 100).toInt().coerceIn(0, 100) else 0

    val affiliateProducts by viewModel.affiliateProducts.collectAsState()
    val matchedProductId by viewModel.matchedProductId.collectAsState()
    val isMatchingProduct by viewModel.isMatchingProduct.collectAsState()
    val affiliateConsentGiven by viewModel.affiliateConsentGiven.collectAsState()
    var recentlyPurchasedItemName by remember { mutableStateOf("") }

    val matchedProduct = remember(matchedProductId, affiliateProducts) {
        matchedProductId?.let { id -> affiliateProducts.find { it.id == id } }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPriority by remember { mutableStateOf("الكل") }
    var priceEstimates by remember { mutableStateOf<Map<String, AiPriceEstimate>>(emptyMap()) }
    var isLoadingPrices by remember { mutableStateOf(false) }
    var showPrediction by remember { mutableStateOf(false) }
    var predictionText by remember { mutableStateOf("") }

    LaunchedEffect(shoppingList) {
        val itemsNeedingPrice = shoppingList.filter { it.estimatedPrice <= 0 && !it.isPurchased }.take(5)
        if (itemsNeedingPrice.isNotEmpty()) {
            isLoadingPrices = true
            val estimates = mutableMapOf<String, AiPriceEstimate>()
            for (item in itemsNeedingPrice) {
                val estimate = com.example.data.ZadAiRepository.estimatePrice(item.itemName)
                if (estimate != null) estimates[item.itemName] = estimate
            }
            priceEstimates = estimates
            isLoadingPrices = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(background)) {
            ShoppingTopBar(onOpenDrawer = onOpenDrawer)

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ShoppingBudgetHeader(totalPrice = totalPrice, budgetRemaining = budgetRemaining, budgetPct = budgetPct) }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    viewModel.refreshSmartShopping()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primary)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تعبئة ذكية", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { shareOnWhatsApp(context, shoppingList) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = MaterialTheme.colorScheme.onSurface)
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
                        items(filtered, key = { it.id }) { item ->
                            EnhancedShoppingItemCard(
                                item = item,
                                priceEstimate = priceEstimates[item.itemName],
                                onCheck = {
                                    recentlyPurchasedItemName = item.itemName
                                    viewModel.matchProduct(item.itemName)
                                    viewModel.toggleShoppingItemPurchased(item.id)
                                },
                                onDelete = { viewModel.deleteShoppingItem(item.id) }
                            )
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
                                            val link = viewModel.buildAmazonLink(matchedProduct.asin)
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                        }
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
private fun ShoppingTopBar(onOpenDrawer: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = onSurfaceVariant) }
        Text("وكيل التسوق الذكي", style = Typography.titleLarge, color = onSurface, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ShoppingBudgetHeader(totalPrice: Double, budgetRemaining: Double, budgetPct: Int) {
    val isOverBudget = totalPrice > budgetRemaining && budgetRemaining > 0
    Card(
        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(
                    if (isOverBudget) listOf(Color(0xFFC62828), Color(0xFFE53935))
                    else listOf(Color(0xFF1B5E20), Color(0xFF43A047))
                ))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سلة زاد الذكية", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الميزانية المتبقية", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text(
                            "%.0f ريال".format(budgetRemaining),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.0f ريال".format(totalPrice), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    if (budgetPct > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("$budgetPct% من الميزانية", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
private fun EnhancedShoppingItemCard(
    item: ZadShoppingItem,
    priceEstimate: AiPriceEstimate?,
    onCheck: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (item.priority) {
        "high" -> dangerColor
        "medium" -> Color(0xFFF9A825)
        else -> successColor
    }
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.04f)),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCheck, modifier = Modifier.size(42.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(outlineVariant.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "تم", tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.itemName, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(priorityColor)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(secondaryContainer).padding(horizontal = 7.dp, vertical = 3.dp)
                    ) { Text("× ${item.quantity}", style = Typography.bodySmall, color = onSecondaryContainer, fontWeight = FontWeight.Medium) }

                    if (priceEstimate != null) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(primaryContainer).padding(horizontal = 7.dp, vertical = 3.dp)
                        ) { Text("%.0f-%.0f ريال".format(priceEstimate.lowPrice, priceEstimate.highPrice), style = Typography.bodySmall, color = onPrimaryContainer, fontWeight = FontWeight.Medium) }
                    } else if (item.estimatedPrice > 0) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(primaryContainer).padding(horizontal = 7.dp, vertical = 3.dp)
                        ) { Text("%.0f ريال".format(item.estimatedPrice), style = Typography.bodySmall, color = onPrimaryContainer, fontWeight = FontWeight.Medium) }
                    }

                    if (item.predictedDaysLeft != null && item.predictedDaysLeft <= 3) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(errorContainer).padding(horizontal = 7.dp, vertical = 3.dp)
                        ) { Text("ينفذ بعد ${item.predictedDaysLeft} أيام", style = Typography.bodySmall, color = dangerColor, fontWeight = FontWeight.Bold) }
                    }

                    if (item.store != null) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(catBankingBg).padding(horizontal = 7.dp, vertical = 3.dp)
                        ) { Text(item.store!!, style = Typography.bodySmall, color = catBankingIcon, fontWeight = FontWeight.Medium) }
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SmartEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(100.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0xFF43A047).copy(alpha = 0.15f), Color.Transparent))
            ),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(52.dp), tint = Color(0xFF43A047).copy(alpha = 0.7f)) }
        Spacer(Modifier.height(20.dp))
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
                shape = RoundedCornerShape(12.dp),
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
    val full = "قائمة تسوق زاد:\n$shareText\n\nالإجمالي: %.0f ريال".format(total)
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
