package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ZadEmptyState
import com.example.ui.theme.primary
import com.example.ui.viewmodels.PriceReportingViewModel

/**
 * UI_ARCHITECTURE_SPEC.md §7.1 — كانت `PriceReportingScreen`/`CrowdsourceDashboard`
 * موجودين بالكامل (نموذج + leaderboard حقيقي عبر PriceReportingViewModel) بس صفر
 * استدعاء في كل التطبيق. دلوقتي "زر جانبي" جوه PantryShoppingScreen (نفس نمط
 * BrainFamilyScreen — local sub-view، مش NavController).
 */
@Composable
fun PriceReportingRoute(onBack: () -> Unit, viewModel: PriceReportingViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showForm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadLeaderboard() }

    if (showForm) {
        PriceReportingScreen(
            onSubmit = { itemName, category, price, location, storeName ->
                viewModel.submitPrice(itemName, category, price, location, storeName)
                showForm = false
            },
            onBack = { showForm = false }
        )
    } else {
        CrowdsourceDashboard(
            onReportPrice = { showForm = true },
            onBack = onBack,
            contributionCount = state.contributionCount,
            leaderboardUsers = state.leaderboard
        )
    }
}

@Composable
fun PriceReportingScreen(
    onSubmit: (itemName: String, category: String, price: Double, location: String, storeName: String) -> Unit,
    onBack: () -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("bread") }
    var price by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf("bread", "milk", "eggs", "oil", "vegetables", "fruits", "general")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = primary)
                }
                Text(
                    "سجّل السعر",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.TrendingUp, "Report", tint = primary, modifier = Modifier.size(24.dp))
            }
        }

        // Info Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        "Info",
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "ساهم في تحديث أسعار السوق الحية. بيانات العائلة تساعد تنبؤات أفضل.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        }

        // Item Name
        item {
            Text("اسم السلعة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                placeholder = { Text("مثل: خبز، لبن، بيض") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        // Category
        item {
            Text("الفئة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Price
        item {
            Text("السعر (جنيه)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            OutlinedTextField(
                value = price,
                onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) price = it },
                placeholder = { Text("مثل: 15.50") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                leadingIcon = { Text("ج.م", fontSize = 12.sp) }
            )
        }

        // Location
        item {
            Text("المنطقة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = { Text("مثل: القاهرة، الجيزة") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        // Store Name (Optional)
        item {
            Text("اسم المتجر (اختياري)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                placeholder = { Text("مثل: كارفور، سبينيز") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }

        // Submit Button
        item {
            Button(
                onClick = {
                    if (itemName.isNotEmpty() && price.isNotEmpty()) {
                        isSubmitting = true
                        onSubmit(itemName, category, price.toDouble(), location, storeName)
                        // Reset form
                        itemName = ""
                        price = ""
                        location = ""
                        storeName = ""
                        isSubmitting = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = itemName.isNotEmpty() && price.isNotEmpty() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, "Submit", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("أرسل السعر", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Spacer
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CrowdsourceDashboard(
    onReportPrice: () -> Unit,
    onBack: () -> Unit,
    contributionCount: Int = 0,
    leaderboardUsers: List<com.example.ui.viewmodels.LeaderboardEntryData> = emptyList()
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = primary)
                }
                Text(
                    "لوحة الأسعار",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }

        // Stats Card — UI_ARCHITECTURE_SPEC.md §7.1 Zero-Mock: كان فيه كارت تاني
        // "أسعار حية" = contributionCount * 3، رقم مختلق مالوش أي مصدر حقيقي. اتشال.
        item {
            StatCard(
                title = "مساهماتك",
                value = contributionCount.toString(),
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }

        // Report Button
        item {
            Button(
                onClick = onReportPrice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Icon(Icons.Default.Add, "Report", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("سجّل سعر جديد", fontWeight = FontWeight.Bold)
            }
        }

        // Leaderboard Title
        item {
            Text(
                "أكثر المشاركين 🏆",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }

        // Leaderboard Items
        items(leaderboardUsers.size) { index ->
            val entry = leaderboardUsers[index]
            LeaderboardCard(entry = entry, rank = index + 1)
        }

        // Empty State
        if (leaderboardUsers.isEmpty()) {
            item {
                ZadEmptyState(
                    icon = Icons.Default.Info,
                    title = "لا توجد مساهمات بعد",
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector = Icons.Default.Info,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .background(Color.White)
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                title,
                tint = primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            Text(
                title,
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
        }
    }
}

@Composable
private fun LeaderboardCard(entry: com.example.ui.viewmodels.LeaderboardEntryData, rank: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> Color(0xFFE0E0E0)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    rank.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.userName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    "${entry.contributionCount} مساهمات",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}
