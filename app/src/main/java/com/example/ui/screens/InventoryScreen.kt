package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ZadInventory
import com.example.ui.components.pressableScale
import com.example.ui.components.floatingIdle
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.viewmodels.ZadViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun daysUntilExpiry(expiryDate: String?): Int? {
    if (expiryDate.isNullOrBlank()) return null
    return try {
        val expiry = LocalDate.parse(expiryDate)
        val today = LocalDate.now()
        ChronoUnit.DAYS.between(today, expiry).toInt()
    } catch (e: Exception) {
        null
    }
}

fun expiryColor(days: Int?): Color = when {
    days == null -> Color(0xFFBDBDBD)
    days <= 3 -> dangerColor
    days <= 7 -> warningColor
    else -> successColor
}

/** نسبة المخزون لعرضها كـ progress bar — نفترض "امتلاء" عند 3 أضعاف حد التنبيه، مفيش حقل "أقصى كمية" في الموديل */
private fun stockRatio(item: ZadInventory): Float {
    val threshold = (item.lowStockThreshold ?: 2).coerceAtLeast(1)
    return (item.quantity.toFloat() / (threshold * 3f)).coerceIn(0f, 1f)
}

private fun stockColor(item: ZadInventory): Color {
    val threshold = (item.lowStockThreshold ?: 2).coerceAtLeast(1)
    return when {
        item.quantity <= threshold -> dangerColor
        item.quantity <= threshold * 2 -> warningColor
        else -> successColor
    }
}

private data class CategoryDef(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color
)

/**
 * إيموجي مميز لكل منتج (بدل أيقونة Material العامة) — يُستخدم مع floatingIdle() لإحساس "حي"
 * متحرك لكل صنف في المخزون. لو الاسم مش متعرّف عليه، الفallback بقى إيموجي التصنيف
 * (🥦 للخضار، 🥫 للبقالة...) بدل صندوق عام واحد للكل — كان كل صنف مش من القايمة اليدوية
 * دي (زي أي حاجة من مسح AI باسم غير متوقع) بيرجع 📦 بغض النظر عن تصنيفه الفعلي.
 */
private fun getEmojiForItem(itemName: String, category: String? = null): String {
    val lower = itemName.lowercase()
    return when {
        "بيض" in lower -> "🥚"
        "حليب" in lower || "لبن" in lower -> "🥛"
        "خبز" in lower || "عيش" in lower || "صامولي" in lower -> "🍞"
        "دجاج" in lower || "فراخ" in lower -> "🍗"
        "لحم" in lower -> "🥩"
        "سمك" in lower -> "🐟"
        "طماطم" in lower || "بندورة" in lower -> "🍅"
        "بطاطس" in lower || "بطاطا" in lower -> "🥔"
        "بصل" in lower -> "🧅"
        "تفاح" in lower -> "🍎"
        "موز" in lower -> "🍌"
        "برتقال" in lower -> "🍊"
        "قهوة" in lower || "بن" in lower -> "☕"
        "شاي" in lower -> "🍵"
        "سكر" in lower -> "🧂"
        "ملح" in lower -> "🧂"
        "زيت" in lower -> "🫙"
        "ماء" in lower || "مياه" in lower -> "💧"
        "صابون" in lower -> "🧼"
        "شامبو" in lower -> "🧴"
        "مناديل" in lower || "فاين" in lower -> "🧻"
        "جبن" in lower || "جبنة" in lower -> "🧀"
        "أرز" in lower || "رز" in lower -> "🍚"
        "معكرونة" in lower || "مكرونة" in lower -> "🍝"
        else -> emojiForCategory(normalizeCategoryKey(category))
    }
}

private fun emojiForCategory(categoryKey: String): String = when (categoryKey) {
    "البقالة" -> "🥫"
    "الخضار" -> "🥦"
    "الفواكه" -> "🍎"
    "اللحوم" -> "🥩"
    "الألبان" -> "🥛"
    "المشروبات" -> "🥤"
    "العناية" -> "🧴"
    else -> "📦"
}

private val categoryDefs = listOf(
    CategoryDef("الكل", "الكل", Icons.Default.Apps, primary, onPrimary),
    CategoryDef("البقالة", "البقالة", Icons.Default.ShoppingBasket, Color(0xFFFFF3E0), Color(0xFFF57C00)),
    CategoryDef("الخضار", "الخضار", Icons.Default.Eco, Color(0xFFE8F5E9), Color(0xFF43A047)),
    CategoryDef("الفواكه", "الفواكه", Icons.Default.Fastfood, Color(0xFFFCE4EC), Color(0xFFD81B60)),
    CategoryDef("اللحوم", "اللحوم", Icons.Default.SetMeal, Color(0xFFFFEBEE), Color(0xFFC62828)),
    CategoryDef("الألبان", "الألبان", Icons.Default.LocalDrink, Color(0xFFE3F2FD), Color(0xFF1976D2)),
    CategoryDef("المشروبات", "المشروبات", Icons.Default.LocalCafe, Color(0xFFF3E5F5), Color(0xFF8E24AA)),
    CategoryDef("العناية", "العناية", Icons.Default.Spa, Color(0xFFE0F7FA), Color(0xFF00838F)),
    CategoryDef("أخرى", "أخرى", Icons.Default.MoreHoriz, Color(0xFFF5F5F5), Color(0xFF757575))
)

/**
 * الفئات القادمة من مسح AI (zad-core-intelligence's meal_suggestions/inventory prompts) نص حر
 * بلا قاموس مقيّد — ممكن ترجع "خضروات" مش "الخضار"، أو "عام" (نفس مثال الـ JSON schema
 * في الـ prompt) بدل تصنيف حقيقي. من غير التطبيع ده، الصنف كان بيختفي من كل تابات
 * التصنيف (يفضل يظهر بس تحت "الكل") لأن الفلتر كان بيقارن النص الخام حرفياً. يرجع نفس
 * مفاتيح [categoryDefs] بالظبط.
 */
private fun normalizeCategoryKey(raw: String?): String {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return "أخرى"
    if (categoryDefs.any { it.key == v }) return v
    val lower = v.lowercase()
    return when {
        "خضر" in v || "vegetable" in lower -> "الخضار"
        "فاكه" in v || "فواك" in v || "fruit" in lower -> "الفواكه"
        "لحم" in v || "لحوم" in v || "دجاج" in v || "فراخ" in v || "دواجن" in v ||
            "meat" in lower || "poultry" in lower -> "اللحوم"
        "لبن" in v || "ألبان" in v || "البان" in v || "جبن" in v || "dairy" in lower -> "الألبان"
        "مشروب" in v || "عصير" in v || "beverage" in lower || "drink" in lower -> "المشروبات"
        "عناي" in v || "تنظيف" in v || "نظاف" in v || "hygiene" in lower || "clean" in lower -> "العناية"
        "بقال" in v || "غذائي" in v || "grocery" in lower || "groceries" in lower -> "البقالة"
        else -> "أخرى"
    }
}

private fun categoryDefFor(key: String?): CategoryDef =
    categoryDefs.find { it.key == normalizeCategoryKey(key) } ?: categoryDefs.last()

private val units = listOf("حبة", "كيلو", "جرام", "لتر", "علبة", "كيس", "قرشة", "صندوق")
private val unitLabels = listOf("حبة", "كجم", "جرام", "لتر", "علبة", "كيس", "قرشة", "صندوق")

/** إشارة خفيفة لفتح تبويب النواقص مباشرة عند الدخول من كارت خارجي (زي HomeScreen) — نفس نمط MainActivity.pendingInviteCode */
object InventoryNavState {
    var openShortagesTab by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: ZadViewModel,
    onNavigateToAssistant: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val allItems by viewModel.inventory.collectAsState()
    val searchQuery by viewModel.inventorySearchQuery.collectAsState()
    var selectedCategory by remember { mutableStateOf("الكل") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ZadInventory?>(null) }
    var selectedTab by remember { mutableStateOf(if (InventoryNavState.openShortagesTab) 1 else 0) }

    LaunchedEffect(Unit) {
        InventoryNavState.openShortagesTab = false
    }

    val expiringItems = remember(allItems) {
        allItems.filter { item ->
            val d = daysUntilExpiry(item.expiryDate)
            d != null && d <= 3
        }
    }

    val lowStockItems = remember(allItems) {
        allItems.filter { it.quantity <= (it.lowStockThreshold ?: 2) }
    }

    val shortageItems = remember(lowStockItems, expiringItems) {
        (lowStockItems + expiringItems).distinctBy { it.id }
    }

    val filteredItems = remember(allItems, selectedCategory, searchQuery) {
        allItems.filter { item ->
            // normalizeCategoryKey — raw item.category (AI-scanned items especially) doesn't
            // always match a categoryDefs key exactly, so compare on the normalized key, not
            // the raw string, or a mistagged "خضروات" item would vanish from every tab except "الكل".
            val matchesCategory = selectedCategory == "الكل" || normalizeCategoryKey(item.category) == selectedCategory
            val matchesSearch = searchQuery.isBlank() || item.itemName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Transparent: MainScreen paints the mockup's canvas gradient behind every
    // screen, and a white fill here flattens every white list card on top of it.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            InventorySearchBar(
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) }
            )

            if (lowStockItems.isNotEmpty()) {
                LowStockBanner(
                    items = lowStockItems,
                    onShopClick = { viewModel.runAutoReplenish() }
                )
            }

            if (expiringItems.isNotEmpty()) {
                val suggestRecipePrompt = stringResource(R.string.suggest_recipe_for_expiring_prompt)
                ExpiringSoonSection(
                    items = expiringItems,
                    onNavigateToAssistant = {
                        val names = expiringItems.joinToString("، ") { it.itemName }
                        viewModel.sendAiChatMessage("$suggestRecipePrompt $names")
                        onNavigateToAssistant()
                    }
                )
            }

            com.example.ui.components.ZadSegmentedTabs(
                tabs = listOf(
                    stringResource(R.string.tab_all_products),
                    if (shortageItems.isEmpty()) stringResource(R.string.tab_shortages)
                    else "${stringResource(R.string.tab_shortages)} (${shortageItems.size})"
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )

            if (selectedTab == 1) {
                if (shortageItems.isEmpty()) {
                    com.example.ui.components.ZadEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(R.string.no_shortages_title),
                        subtitle = stringResource(R.string.no_shortages_hint),
                        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
                        iconTint = successColor,
                        iconBackground = successColor.copy(alpha = 0.1f)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        // bottom=100dp clears the floating Add/Scan buttons — same clearance
                        // EmptyInventoryState/EmptySearchState already use below; the grid
                        // itself was still using a flat 12dp, so its last row sat under the FABs.
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shortageItems, key = { it.id }) { item ->
                            ShortageItemCard(
                                item = item,
                                onAddToShoppingList = {
                                    viewModel.addShoppingItem(
                                        com.example.data.ZadShoppingItem(
                                            itemName = item.itemName,
                                            quantity = 1,
                                            estimatedPrice = getEstimatedPrice(item.itemName)
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                CategoryPills(
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it }
                )

                if (allItems.isEmpty() && searchQuery.isBlank()) {
                    EmptyInventoryState(onNavigateToCamera = onNavigateToCamera)
                } else if (filteredItems.isEmpty()) {
                    EmptySearchState()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        // bottom=100dp clears the floating Add/Scan buttons — same clearance
                        // EmptyInventoryState/EmptySearchState already use below; the grid
                        // itself was still using a flat 12dp, so its last row sat under the FABs.
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                            com.example.ui.components.AppearOnEntry(delayMs = (index * 40).coerceAtMost(400)) {
                                InventoryItemCard(
                                    item = item,
                                    onDelete = { viewModel.deleteInventory(item.id) },
                                    onEdit = { editingItem = item },
                                    onConsume = { viewModel.consumeInventoryItem(item) },
                                    onRestock = { viewModel.injectScannedItems(listOf(item.copy(quantity = 1))) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = { onNavigateToCamera() },
                containerColor = secondary,
                contentColor = onSecondary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = stringResource(R.string.photograph_inventory))
            }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = primary,
                contentColor = onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_manually))
            }
        }
    }

    if (showAddDialog) {
        AddInventoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, qty, unitIdx, catIdx, expiry ->
                viewModel.addInventory(
                    ZadInventory(
                        itemName = name,
                        quantity = qty,
                        unit = units[unitIdx],
                        category = categoryDefs[catIdx + 1].key,
                        expiryDate = expiry.ifBlank { null }
                    )
                )
                showAddDialog = false
            }
        )
    }

    editingItem?.let { item ->
        EditInventoryDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { name, qty, unit ->
                viewModel.updateInventoryItem(item, name, qty, unit)
                editingItem = null
            }
        )
    }
}

/**
 * تعديل سريع لصنف مخزون قايم: الاسم والكمية والوحدة. متعمد إنه أبسط من
 * [AddInventoryDialog] — التصنيف وتاريخ الانتهاء اتحددوا وقت الإضافة، واللي بيتصحح
 * عملياً هو الكمية.
 */
@Composable
private fun EditInventoryDialog(
    item: ZadInventory,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Int, unit: String?) -> Unit
) {
    var name by remember { mutableStateOf(item.itemName) }
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var unit by remember { mutableStateOf(item.unit ?: "") }

    val quantity = quantityText.toIntOrNull()
    val canSave = name.isNotBlank() && quantity != null && quantity >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.inventory_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { input -> quantityText = input.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.quantity_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = quantityText.isNotBlank() && quantity == null
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(stringResource(R.string.unit_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(name.trim(), quantity ?: item.quantity, unit.trim().ifBlank { null }) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * The mockup's inventory screen has no app bar of its own — `ZadTopHeader` in the
 * shell carries the title, so all that is left here is the search field, promoted
 * from a collapsed icon to a persistent pill (a filter you cannot see is a filter
 * nobody uses).
 */
@Composable
private fun InventorySearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text(stringResource(R.string.search_inventory), color = onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = onSurfaceVariant) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_cd), tint = onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primary,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

private fun getEstimatedPrice(itemName: String): Double {
    val lower = itemName.lowercase()
    return when {
        "لحم" in lower -> 55.0
        "دجاج" in lower -> 18.0
        "سمك" in lower -> 35.0
        "بيض" in lower -> 22.0
        "حليب" in lower || "لبن" in lower -> 6.0
        "جبن" in lower -> 15.0
        "خبز" in lower || "عيش" in lower -> 2.0
        "قهوة" in lower -> 30.0
        "شاي" in lower -> 12.0
        "طماطم" in lower || "بصل" in lower || "بطاطس" in lower -> 5.0
        "صابون" in lower || "شامبو" in lower -> 25.0
        else -> 10.0 // Default fallback estimate
    }
}

@Composable
private fun LowStockBanner(items: List<ZadInventory>, onShopClick: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val totalEstimatedCost = items.sumOf { getEstimatedPrice(it.itemName) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(dangerColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ShoppingCartCheckout,
                contentDescription = null,
                tint = dangerColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.inventory_shortages_count, items.size),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = onErrorContainer
            )
            Text(
                stringResource(R.string.estimated_shopping_cost, com.example.data.CurrencyFormatter.format(context, totalEstimatedCost)),
                style = MaterialTheme.typography.bodySmall,
                color = onErrorContainer.copy(alpha = 0.8f)
            )
        }
        TextButton(onClick = onShopClick) {
            Text(stringResource(R.string.add_to_shopping_list), color = dangerColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExpiringSoonSection(
    items: List<ZadInventory>,
    onNavigateToAssistant: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(warningColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.expiring_soon_badge),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onBackground
                )
            }
            TextButton(onClick = onNavigateToAssistant) {
                Text(stringResource(R.string.suggest_recipe), color = primary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items) { item ->
                val days = daysUntilExpiry(item.expiryDate)
                com.example.ui.components.ZadListCard(
                    modifier = Modifier.padding(vertical = 2.dp),
                    contentPadding = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(expiryColor(days))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                item.itemName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (days != null && days <= 0) stringResource(R.string.expired) else stringResource(R.string.days_remaining, days?.toString() ?: "?"),
                                style = MaterialTheme.typography.labelSmall,
                                color = expiryColor(days)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPills(
    selected: String,
    onSelect: (String) -> Unit
) {
    // The mockup's category chip: fully rounded, white with a soft shadow when
    // idle, solid brand green when active, and the icon inside a 20dp tinted
    // circle rather than loose next to the label. The old chip was a transparent
    // outline — on the canvas gradient that reads as a disabled control.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categoryDefs.forEach { def ->
            val isSelected = selected == def.key
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .then(if (isSelected) Modifier else Modifier.zadCardShadow(shape, elevation = 6.dp))
                    .clip(shape)
                    .background(if (isSelected) primary else Color.White)
                    .clickable { onSelect(def.key) }
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White.copy(alpha = 0.2f) else def.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        def.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else def.fg,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    def.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else textSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyInventoryState(onNavigateToCamera: () -> Unit) {
    com.example.ui.components.ZadEmptyState(
        icon = Icons.Default.Inventory2,
        title = stringResource(R.string.inventory_empty),
        subtitle = stringResource(R.string.inventory_empty_hint),
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onNavigateToCamera,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}

@Composable
private fun EmptySearchState() {
    com.example.ui.components.ZadEmptyState(
        icon = Icons.Default.SearchOff,
        title = stringResource(R.string.no_results),
        subtitle = stringResource(R.string.try_different_search),
        modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
        iconTint = outline,
        iconBackground = outlineVariant
    )
}

@Composable
private fun InventoryItemCard(
    item: ZadInventory,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onConsume: () -> Unit = {},
    onRestock: () -> Unit = {}
) {
    val days = daysUntilExpiry(item.expiryDate)
    val catDef = categoryDefFor(item.category)
    val isLowStock = item.quantity <= (item.lowStockThreshold ?: 2)
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(cardShape, elevation = 10.dp)
            .clip(cardShape)
            .background(
                Brush.verticalGradient(listOf(catDef.bg.copy(alpha = 0.35f), surface))
            )
            .border(1.dp, catDef.bg.copy(alpha = 0.7f), cardShape)
            .pressableScale()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(catDef.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getEmojiForItem(item.itemName, item.category),
                        fontSize = 20.sp,
                        modifier = Modifier.floatingIdle(amplitude = 2.5f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(expiryColor(days))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                item.itemName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // تحكم بالكمية: − استهلاك (يغذي التعلم والنواقص) / + إعادة تعبئة
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(surfaceContainerLow)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onConsume,
                    enabled = item.quantity > 0,
                    modifier = Modifier.size(26.dp).pressableScale()
                ) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.consume_one_cd),
                        tint = if (item.quantity > 0) dangerColor else outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    "${item.quantity} ${item.unit ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(
                    onClick = onRestock,
                    modifier = Modifier.size(26.dp).pressableScale()
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = stringResource(R.string.restock_one_cd),
                        tint = primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            val animatedStock by androidx.compose.animation.core.animateFloatAsState(
                targetValue = stockRatio(item),
                animationSpec = com.example.ui.components.ZadSprings.Screen,
                label = "stockRatio"
            )
            LinearProgressIndicator(
                progress = { animatedStock },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = stockColor(item),
                trackColor = outlineVariant
            )

            if (days != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (days <= 0) stringResource(R.string.expired_short) else stringResource(R.string.days_count, days),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = expiryColor(days)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLowStock) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = dangerColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            stringResource(R.string.low_stock_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = dangerColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // تعديل جنب المسح — تصحيح كمية غلط مكانش ليه طريق غير المسح وإعادة
                    // الإدخال، واللي بيضيّع معدل الاستهلاك المتعلّم للصنف.
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp).pressableScale()
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.inventory_edit_title),
                            tint = outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).pressableScale()
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.delete_cd),
                            tint = outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortageItemCard(
    item: ZadInventory,
    onAddToShoppingList: () -> Unit
) {
    val days = daysUntilExpiry(item.expiryDate)
    val catDef = categoryDefFor(item.category)
    var added by remember(item.id) { mutableStateOf(false) }

    com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(catDef.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getEmojiForItem(item.itemName, item.category),
                        fontSize = 18.sp,
                        modifier = Modifier.floatingIdle(amplitude = 2f)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.itemName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (days != null && days <= 0) stringResource(R.string.expired_short)
                        else if (days != null) stringResource(R.string.days_count, days)
                        else stringResource(R.string.quantity_colon_count, item.quantity),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (days != null) expiryColor(days) else dangerColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { stockRatio(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = stockColor(item),
                trackColor = outlineVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { added = true; onAddToShoppingList() },
                enabled = !added,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (added) successColor else primary,
                    contentColor = Color.White,
                    disabledContainerColor = successColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (added) Icons.Default.Check else Icons.Default.AddShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(if (added) R.string.added_to_list_label else R.string.add_to_shopping_list),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInventoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var selectedUnitIndex by remember { mutableIntStateOf(0) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var expiryDate by remember { mutableStateOf("") }
    var showUnitMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.add_new_product),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onBackground
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close_action),
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    stringResource(R.string.product_name),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.eg_milk_placeholder), color = outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.quantity_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primary,
                                unfocusedBorderColor = outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.unit_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box {
                            OutlinedTextField(
                                value = unitLabels[selectedUnitIndex],
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { showUnitMenu = true })
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primary,
                                    unfocusedBorderColor = outline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showUnitMenu = true }
                            )
                            DropdownMenu(
                                expanded = showUnitMenu,
                                onDismissRequest = { showUnitMenu = false }
                            ) {
                                unitLabels.forEachIndexed { idx, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedUnitIndex = idx
                                            showUnitMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    stringResource(R.string.section_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box {
                    OutlinedTextField(
                        value = categoryDefs[selectedCategoryIndex + 1].label,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { showCategoryMenu = true })
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary,
                            unfocusedBorderColor = outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategoryMenu = true }
                    )
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categoryDefs.drop(1).forEachIndexed { idx, def ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(def.icon, contentDescription = null, tint = def.fg, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(def.label)
                                    }
                                },
                                onClick = {
                                    selectedCategoryIndex = idx
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    stringResource(R.string.expiry_date_optional),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    placeholder = { Text("2025-12-31", color = outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = onSurfaceVariant)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = {
                            val qty = quantity.toIntOrNull() ?: 1
                            val trimmed = name.trim()
                            if (trimmed.isNotBlank()) {
                                onSave(trimmed, qty, selectedUnitIndex, selectedCategoryIndex, expiryDate.trim())
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primary,
                            contentColor = onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
