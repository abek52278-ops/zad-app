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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.primary
import com.example.ui.theme.secondary

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
    leaderboardUsers: List<LeaderboardEntry> = emptyList()
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

        // Stats Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "مساهماتك",
                    value = contributionCount.toString(),
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "أسعار حية",
                    value = (contributionCount * 3).toString(),
                    icon = Icons.Default.BarChart,
                    modifier = Modifier.weight(1f)
                )
            }
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            "Empty",
                            tint = Color(0xFFA1A5AB),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "لا توجد مساهمات بعد",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.material.icons.filled.Icon = Icons.Default.Info,
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
private fun LeaderboardCard(entry: LeaderboardEntry, rank: Int) {
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

            Text(
                "⭐ ${entry.score}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
    }
}

data class LeaderboardEntry(
    val userName: String,
    val contributionCount: Int,
    val score: Int
)
