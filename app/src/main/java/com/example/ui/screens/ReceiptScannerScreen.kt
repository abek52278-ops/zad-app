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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.primary

data class ScannedReceiptData(
    val storeName: String,
    val storeLocation: String,
    val totalAmount: Double,
    val itemCount: Int,
    val items: List<String>,
    val confidence: Int,
    val date: String
)

@Composable
fun ReceiptScannerScreen(
    onCapturePhoto: (() -> Unit)? = null,
    onBack: () -> Unit,
    scannedReceipts: List<ScannedReceiptData> = emptyList(),
    isProcessing: Boolean = false
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
                    "مسح الفواتير",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.Receipt, "Receipt", tint = primary, modifier = Modifier.size(24.dp))
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
                        "صوّر فاتورة السوبرماركت لتسجيل الأسعار تلقائياً بـ AI",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        }

        // Capture Button
        item {
            Button(
                onClick = onCapturePhoto ?: {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isProcessing && onCapturePhoto != null,
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("جاري المعالجة...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PhotoCamera, "Capture", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التقط صورة الفاتورة", fontWeight = FontWeight.Bold)
                }
            }
        }

        // History Title
        if (scannedReceipts.isNotEmpty()) {
            item {
                Text(
                    "السجل",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Receipt Cards
        items(scannedReceipts.size) { index ->
            ReceiptCard(receipt = scannedReceipts[index])
        }

        // Empty State
        if (scannedReceipts.isEmpty() && !isProcessing) {
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
                            Icons.Default.Receipt,
                            "Empty",
                            tint = Color(0xFFA1A5AB),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "لا توجد فواتير محفوظة",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "ابدأ بتصوير فاتورة جديدة",
                            fontSize = 12.sp,
                            color = Color(0xFFA1A5AB)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ReceiptCard(receipt: ScannedReceiptData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Confidence Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = when {
                                receipt.confidence >= 90 -> Color(0xFF10B981)
                                receipt.confidence >= 70 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${receipt.confidence}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        receipt.storeName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        receipt.storeLocation,
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }

                Text(
                    "${receipt.date}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Items Summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${receipt.itemCount} منتج",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )

                receipt.items.take(3).forEach { item ->
                    Text(
                        "• $item",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }

                if (receipt.items.size > 3) {
                    Text(
                        "+${receipt.items.size - 3} منتج آخر",
                        fontSize = 10.sp,
                        color = Color(0xFFA1A5AB)
                    )
                }
            }

            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Total
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AttachMoney,
                    "Total",
                    tint = primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${receipt.totalAmount.toInt()} ج.م",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }
        }
    }
}
