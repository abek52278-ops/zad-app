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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ZadInventory
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

private data class CategoryDef(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color
)

private fun getIconForItem(itemName: String): ImageVector {
    val lower = itemName.lowercase()
    return when {
        "بيض" in lower -> Icons.Default.Restaurant
        "حليب" in lower || "لبن" in lower -> Icons.Default.LocalGroceryStore
        "خبز" in lower || "عيش" in lower || "صامولي" in lower -> Icons.Default.BakeryDining
        "دجاج" in lower || "فراخ" in lower -> Icons.Default.Restaurant
        "لحم" in lower -> Icons.Default.Restaurant
        "سمك" in lower -> Icons.Default.Restaurant
        "طماطم" in lower || "بندورة" in lower -> Icons.Default.Eco
        "بطاطس" in lower || "بطاطا" in lower -> Icons.Default.Eco
        "بصل" in lower -> Icons.Default.Eco
        "تفاح" in lower || "موز" in lower || "برتقال" in lower -> Icons.Default.Fastfood
        "قهوة" in lower || "بن" in lower -> Icons.Default.Coffee
        "شاي" in lower -> Icons.Default.LocalCafe
        "سكر" in lower || "ملح" in lower -> Icons.Default.Science
        "زيت" in lower -> Icons.Default.LocalBar
        "ماء" in lower || "مياه" in lower -> Icons.Default.WaterDrop
        "صابون" in lower -> Icons.Default.CleaningServices
        "شامبو" in lower -> Icons.Default.LocalHospital
        "مناديل" in lower || "فاين" in lower -> Icons.Default.Restaurant
        "جبن" in lower || "جبنة" in lower -> Icons.Default.Restaurant
        else -> Icons.Default.Inventory2
    }
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

private fun categoryDefFor(key: String?): CategoryDef =
    categoryDefs.find { it.key == key } ?: categoryDefs.last()

private val units = listOf("حبة", "كيلو", "جرام", "لتر", "علبة", "كيس", "قرشة", "صندوق")
private val unitLabels = listOf("حبة", "كجم", "جرام", "لتر", "علبة", "كيس", "قرشة", "صندوق")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: ZadViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val allItems by viewModel.inventory.collectAsState()
    val searchQuery by viewModel.inventorySearchQuery.collectAsState()
    var selectedCategory by remember { mutableStateOf("الكل") }
    var showAddDialog by remember { mutableStateOf(false) }

    val expiringItems = remember(allItems) {
        allItems.filter { item ->
            val d = daysUntilExpiry(item.expiryDate)
            d != null && d <= 3
        }
    }

    val lowStockItems = remember(allItems) {
        allItems.filter { it.quantity <= (it.lowStockThreshold ?: 2) }
    }

    val filteredItems = remember(allItems, selectedCategory, searchQuery) {
        allItems.filter { item ->
            val matchesCategory = selectedCategory == "الكل" || item.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() || item.itemName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            InventoryTopBar(
                onOpenDrawer = onOpenDrawer,
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
                ExpiringSoonSection(
                    items = expiringItems,
                    onNavigateToAssistant = {
                        val names = expiringItems.joinToString("، ") { it.itemName }
                        viewModel.sendAiChatMessage("اقترح لي وصفة سريعة تستخدم هذه المكونات التي تنتهي قريباً: $names")
                        onNavigateToAssistant()
                    }
                )
            }

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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        InventoryItemCard(
                            item = item,
                            onDelete = { viewModel.deleteInventory(item.id) },
                            onConsume = { viewModel.consumeInventoryItem(item) },
                            onRestock = { viewModel.injectScannedItems(listOf(item.copy(quantity = 1))) }
                        )
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
                Icon(Icons.Default.DocumentScanner, contentDescription = "تصوير المخزون")
            }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = primary,
                contentColor = onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة يدوية")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryTopBar(
    onOpenDrawer: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    "المخزون الذكي",
                    fontWeight = FontWeight.Bold,
                    color = onBackground
                )
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "القائمة")
                }
            },
            actions = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.Search, contentDescription = "بحث")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = background
            )
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text("ابحث في المخزون...", color = onSurfaceVariant)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = outline,
                    focusedContainerColor = surface,
                    unfocusedContainerColor = surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                "نواقص المخزون (${items.size})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = onErrorContainer
            )
            Text(
                "التكلفة التقديرية للتسوق: ${com.example.data.CurrencyFormatter.format(context, totalEstimatedCost)}",
                style = MaterialTheme.typography.bodySmall,
                color = onErrorContainer.copy(alpha = 0.8f)
            )
        }
        TextButton(onClick = onShopClick) {
            Text("نزّلها في التسوق", color = dangerColor, fontWeight = FontWeight.Bold)
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
                    "ينتهي قريباً",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onBackground
                )
            }
            TextButton(onClick = onNavigateToAssistant) {
                Text("اقتراح وصفة", color = primary, fontWeight = FontWeight.Medium)
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
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
                                if (days != null && days <= 0) "منتهي الصلاحية" else "باقي ${days ?: "?"} يوم",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categoryDefs.forEach { def ->
            val isSelected = selected == def.key
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) primary else Color.Transparent,
                modifier = Modifier
                    .border(
                        width = 1.2.dp,
                        color = if (isSelected) primary else outline,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(def.key) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        def.icon,
                        contentDescription = null,
                        tint = if (isSelected) onPrimary else def.fg,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        def.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) onPrimary else def.fg
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInventoryState(onNavigateToCamera: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = primary.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "المخزون فارغ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "ابدأ بإضافة منتجات لتنظم مخزون منزلك",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToCamera,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصوير", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "لا توجد نتائج",
                style = MaterialTheme.typography.titleSmall,
                color = onSurfaceVariant
            )
            Text(
                "جرّب البحث بكلمة مختلفة",
                style = MaterialTheme.typography.bodySmall,
                color = outline
            )
        }
    }
}

@Composable
private fun InventoryItemCard(
    item: ZadInventory,
    onDelete: () -> Unit,
    onConsume: () -> Unit = {},
    onRestock: () -> Unit = {}
) {
    val days = daysUntilExpiry(item.expiryDate)
    val catDef = categoryDefFor(item.category)
    val isLowStock = item.quantity <= (item.lowStockThreshold ?: 2)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(catDef.bg.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(12.dp)
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
                    Icon(
                        imageVector = getIconForItem(item.itemName),
                        contentDescription = null,
                        tint = catDef.fg,
                        modifier = Modifier.size(20.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onConsume,
                    enabled = item.quantity > 0,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = "استهلاك واحدة",
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
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = "زيادة واحدة",
                        tint = primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (days != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (days <= 0) "منتهي" else "$days يوم",
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
                        shape = RoundedCornerShape(6.dp),
                        color = dangerColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "منخفض",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = dangerColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                        "إضافة منتج جديد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onBackground
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "اسم المنتج",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("مثلاً: حليب", color = outline) },
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
                            "الكمية",
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
                            "الوحدة",
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
                    "القسم",
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
                    "تاريخ الصلاحية (اختياري)",
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
                        Text("إلغاء")
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
                        Text("حفظ")
                    }
                }
            }
        }
    }
}
