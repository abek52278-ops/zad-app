package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@androidx.compose.runtime.Composable
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

@androidx.compose.runtime.Composable
private fun stockColor(item: ZadInventory): Color {
    val threshold = (item.lowStockThreshold ?: 2).coerceAtLeast(1)
    return when {
        item.quantity <= threshold -> dangerColor
        item.quantity <= threshold * 2 -> warningColor
        else -> successColor
    }
}

/** لون خلفية الفئة — "الكل" بتتبع الثيم، الباقي باستيل ثابت مقصود. */
@androidx.compose.runtime.Composable
private fun CategoryDef.bgColor(): Color = if (key == "الكل") primary else bg

/** لون المقدمة المقابل، بنفس المنطق. */
@androidx.compose.runtime.Composable
private fun CategoryDef.fgColor(): Color = if (key == "الكل") onPrimary else fg

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
    // "الكل" هي الوحيدة اللي بتاخد لون الثيم؛ الباقي باستيل مقصود ثابت. القيم هنا
    // خام لأن القايمة top-level (بتتقري كمان من دوال مش composable)، والقراءة
    // الواعية بالثيم بتحصل في bgColor()/fgColor() تحت.
    CategoryDef("الكل", "الكل", Icons.Default.Apps, ZadForestEmerald, ZadOnAccent),
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
private fun normalizeCategoryKey(raw: String?, itemName: String? = null): String {
    val fromCategory = categoryFromText(raw)
    // التصنيف نفسه فشل ("عام"، "أخرى"، null، أو نص الموديل اخترعه) — الاسم لسه مصدر
    // صالح. من غير الشوطة دي، "كرتونة مياه" و"حليب المراعي" و"صابون" كلهم بيقعوا في
    // "أخرى" ومايظهروش تحت أي تاب تصنيف، وده اللي بيخلي التابات تبان فاضية والعميل
    // شايف إن الصنف "مش اتحط في عموده".
    if (fromCategory != "أخرى") return fromCategory
    return categoryFromText(itemName)
}

/** نفس القاموس، مطبَّق على أي نص — تصنيف جاي من الموديل أو اسم الصنف نفسه. */
private fun categoryFromText(raw: String?): String {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return "أخرى"
    if (categoryDefs.any { it.key == v }) return v
    val lower = v.lowercase()
    return when {
        "خضر" in v || "خضار" in v || "طماطم" in v || "بطاطس" in v || "بصل" in v || "خيار" in v ||
            "جزر" in v || "فلفل" in v || "سلطة" in v || "vegetable" in lower -> "الخضار"
        "فاكه" in v || "فواك" in v || "تفاح" in v || "موز" in v || "برتقال" in v || "مانجو" in v ||
            "عنب" in v || "بطيخ" in v || "فراولة" in v || "fruit" in lower -> "الفواكه"
        "لحم" in v || "لحوم" in v || "دجاج" in v || "فراخ" in v || "دواجن" in v || "سمك" in v ||
            "كفتة" in v || "بانيه" in v || "meat" in lower || "poultry" in lower || "fish" in lower -> "اللحوم"
        "لبن" in v || "ألبان" in v || "البان" in v || "جبن" in v || "حليب" in v || "زبادي" in v ||
            "قشطة" in v || "زبدة" in v || "بيض" in v || "dairy" in lower || "milk" in lower ||
            "cheese" in lower || "yogurt" in lower -> "الألبان"
        // كانت ناقصة تماماً، وهي أكتر صنف في بيوت مصر: المية كانت بتروح "أخرى"
        "مشروب" in v || "عصير" in v || "مياه" in v || "ميه" in v || "ماء" in v || "شاي" in v ||
            "قهوة" in v || "نسكافيه" in v || "كولا" in v || "بيبسي" in v || "beverage" in lower ||
            "drink" in lower || "water" in lower || "juice" in lower || "coffee" in lower ||
            "tea" in lower -> "المشروبات"
        "عناي" in v || "تنظيف" in v || "نظاف" in v || "صابون" in v || "شامبو" in v || "معجون" in v ||
            "مناديل" in v || "غسيل" in v || "مسحوق" in v || "كلور" in v || "ديتول" in v ||
            "hygiene" in lower || "clean" in lower || "soap" in lower || "shampoo" in lower -> "العناية"
        "بقال" in v || "غذائي" in v || "أرز" in v || "ارز" in v || "مكرونة" in v || "زيت" in v ||
            "سكر" in v || "ملح" in v || "دقيق" in v || "عدس" in v || "فول" in v || "خبز" in v ||
            "عيش" in v || "معلب" in v || "صلصة" in v || "grocery" in lower || "groceries" in lower ||
            "rice" in lower || "pasta" in lower || "oil" in lower || "sugar" in lower ||
            "bread" in lower -> "البقالة"
        else -> "أخرى"
    }
}

private fun categoryDefFor(key: String?, itemName: String? = null): CategoryDef =
    categoryDefs.find { it.key == normalizeCategoryKey(key, itemName) } ?: categoryDefs.last()

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
    val context = androidx.compose.ui.platform.LocalContext.current
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
            val matchesCategory = selectedCategory == "الكل" || normalizeCategoryKey(item.category, item.itemName) == selectedCategory
            val matchesSearch = searchQuery.isBlank() || item.itemName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Transparent: MainScreen paints the mockup's canvas gradient behind every
    // screen, and a white fill here flattens every white list card on top of it.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // شريط التبويبات المدمج بعرض الشاشة
            com.example.ui.components.ZadSegmentedTabs(
                tabs = listOf(
                    stringResource(R.string.tab_all_products),
                    if (shortageItems.isEmpty()) stringResource(R.string.tab_shortages)
                    else "${stringResource(R.string.tab_shortages)} (${shortageItems.size})"
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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

            if (selectedTab == 1) {
                if (shortageItems.isEmpty()) {
                    com.example.ui.components.ZadEmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(R.string.no_shortages_title),
                        subtitle = stringResource(R.string.no_shortages_hint),
                        modifier = Modifier.fillMaxSize().padding(bottom = ZadHubListBottomPadding),
                        iconTint = successColor,
                        iconBackground = successColor.copy(alpha = 0.1f)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = ZadHubListHorizontalPadding, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(shortageItems, key = { "buy_${it.id}" }) { item ->
                            com.example.ui.widgets.ZadAmazonSearchChip(
                                itemName = item.itemName,
                                reason = stringResource(R.string.amazon_buy_this),
                                onClick = {
                                    com.example.data.AffiliateHelper.open(
                                        context,
                                        com.example.data.AffiliateHelper.productUrl(
                                            asin = null,
                                            fallbackSearchTerm = item.itemName,
                                        )
                                    )
                                }
                            )
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = ZadHubListHorizontalPadding,
                            end = ZadHubListHorizontalPadding,
                            top = 8.dp,
                            bottom = ZadHubListBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shortageItems, key = { it.id }) { item ->
                            ShortageItemCard(
                                item = item,
                                onAddToShoppingList = {
                                    // 0.0 = مش معروف بعد، مش رقم مختلق — نفس القيمة اللي
                                    // AddShoppingItemDialog/GrocerySuggestionsCard بيستخدموها
                                    // للصنف الجديد؛ ShoppingListScreen's LaunchedEffect(shoppingList)
                                    // بيحاول يجيب تقدير حقيقي (ZadAiRepository.estimatePrice) بعد كده.
                                    viewModel.addShoppingItem(
                                        com.example.data.ZadShoppingItem(
                                            itemName = item.itemName,
                                            quantity = 1,
                                            estimatedPrice = 0.0
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                // شريط فلترة الأقسام السريع والأنيق
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = ZadHubListHorizontalPadding)
                ) {
                    items(categoryDefs) { cat ->
                        val isSel = selectedCategory == cat.key
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCategory = cat.key },
                            label = {
                                Text(
                                    cat.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Text(
                                    when (cat.key) {
                                        "الكل" -> "🏠"
                                        "البقالة" -> "🥫"
                                        "الخضار" -> "🥦"
                                        "الفواكه" -> "🍎"
                                        "اللحوم" -> "🥩"
                                        "الألبان" -> "🥛"
                                        "المشروبات" -> "🧃"
                                        "العناية" -> "🧴"
                                        else -> "📦"
                                    },
                                    fontSize = 14.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryContainer,
                                selectedLabelColor = primary,
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (allItems.isEmpty() && searchQuery.isBlank()) {
                    EmptyInventoryState(onNavigateToCamera = onNavigateToCamera)
                } else if (filteredItems.isEmpty()) {
                    EmptySearchState()
                } else {
                    // قائمة المنتجات مباشرة في صفوف رأسية متتالية وأنيقة
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ZadHubListHorizontalPadding,
                            end = ZadHubListHorizontalPadding,
                            top = 8.dp,
                            bottom = ZadHubListBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                            com.example.ui.components.AppearOnEntry(delayMs = (index * 20).coerceAtMost(250)) {
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
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()
            ) {
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

@Composable
private fun LowStockBanner(items: List<ZadInventory>, onShopClick: () -> Unit = {}) {
    // UI_ARCHITECTURE_SPEC.md §3.3 — كان في جدول أسعار محلي وهمي (getEstimatedPrice)
    // هنا. zad_inventory مفيهوش عمود سعر أصلاً، ومفيش cache متزامن حقيقي نقدر نقرا منه
    // في نفس اللحظة دي (المصدر الحقيقي الوحيد، ZadAiRepository.estimatePrice، async
    // ومحجوز لقايمة التسوق بعد ما الصنف يتضاف ليها). فبدل رقم مختلق، البانر بيعرض
    // "غير محدد" — العدد نفسه (items.size) حقيقي وكافي هنا.

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(errorContainer)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(dangerColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ShoppingCartCheckout,
                contentDescription = null,
                tint = dangerColor,
                modifier = Modifier.size(11.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            stringResource(R.string.inventory_shortages_count, items.size),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = onErrorContainer,
            fontSize = 12.sp
        )
        Text(
            " (${stringResource(R.string.estimated_cost_unknown)})",
            style = MaterialTheme.typography.labelSmall,
            color = onErrorContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            stringResource(R.string.add_to_shopping_list),
            style = MaterialTheme.typography.labelSmall,
            color = dangerColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onShopClick() }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(warningColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.expiring_soon_badge),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onBackground,
                    fontSize = 12.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigateToAssistant() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(R.string.suggest_recipe),
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(items) { item ->
                val days = daysUntilExpiry(item.expiryDate)
                com.example.ui.components.ZadListCard(
                    modifier = Modifier.padding(vertical = 1.dp),
                    contentPadding = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(expiryColor(days))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                item.itemName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                            Text(
                                if (days != null && days <= 0) stringResource(R.string.expired) else stringResource(R.string.days_remaining, days?.toString() ?: "?"),
                                style = MaterialTheme.typography.labelSmall,
                                color = expiryColor(days),
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
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
        modifier = Modifier.fillMaxSize().padding(bottom = ZadHubListBottomPadding),
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
        modifier = Modifier.fillMaxSize().padding(bottom = ZadHubListBottomPadding),
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
    val catDef = categoryDefFor(item.category, item.itemName)
    val isLowStock = item.quantity <= (item.lowStockThreshold ?: 2)
    val cardShape = com.example.ui.theme.ZadLuxe.squircle

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(cardShape, elevation = 2.dp)
            .clip(cardShape)
            .background(com.example.ui.theme.ZadLuxe.cardWhite)
            .border(0.5.dp, com.example.ui.theme.ZadLuxe.hairline, cardShape)
            .pressableScale()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة التصنيف
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(catDef.bgColor()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = com.example.ui.components.resolveFoodEmoji(item.itemName),
                    fontSize = 22.sp,
                    modifier = Modifier.floatingIdle(amplitude = 1.2f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // تفاصيل المنتج
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.itemName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLowStock) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFFEE2E2))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.low_stock_badge),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (days != null) {
                        Text(
                            text = if (days <= 0) stringResource(R.string.expired_short) else stringResource(R.string.days_count, days),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = expiryColor(days)
                        )
                        Text("•", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                    Text(
                        text = catDef.label,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val animatedStock by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = stockRatio(item),
                    animationSpec = com.example.ui.components.ZadSprings.Screen,
                    label = "stockRatio"
                )
                LinearProgressIndicator(
                    progress = { animatedStock },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = stockColor(item),
                    trackColor = Color(0xFFF1F5F9)
                )
            }

            // أزرار التحكم بالكمية
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
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
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${item.quantity} ${item.unit ?: ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onRestock,
                    modifier = Modifier.size(26.dp).pressableScale()
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = stringResource(R.string.restock_one_cd),
                        tint = primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // تعديل وحذف
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(26.dp).pressableScale()
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.inventory_edit_title), tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(26.dp).pressableScale()
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete_cd), tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
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
    val catDef = categoryDefFor(item.category, item.itemName)
    var added by remember(item.id) { mutableStateOf(false) }

    com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(catDef.bgColor()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getEmojiForItem(item.itemName, item.category),
                        fontSize = 15.sp,
                        modifier = Modifier.floatingIdle(amplitude = 1.5f)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.itemName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                    Text(
                        if (days != null && days <= 0) stringResource(R.string.expired_short)
                        else if (days != null) stringResource(R.string.days_count, days)
                        else stringResource(R.string.quantity_colon_count, item.quantity),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (days != null) expiryColor(days) else dangerColor,
                        fontSize = 9.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { stockRatio(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = stockColor(item),
                trackColor = outlineVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { added = true; onAddToShoppingList() },
                enabled = !added,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
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
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    stringResource(if (added) R.string.added_to_list_label else R.string.add_to_shopping_list),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
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
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
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
                                        Icon(def.icon, contentDescription = null, tint = def.fgColor(), modifier = Modifier.size(18.dp))
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
