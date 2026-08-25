package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CurrencyFormatter
import com.example.data.ZadMaintenanceItem
import com.example.data.ZadPharmacyItem
import com.example.data.ZadShoppingItem
import com.example.data.ZadSubscription
import com.example.ui.theme.ZadV2
import com.example.ui.viewmodels.ZadViewModel

// ── Pharmacy ─────────────────────────────────────────────────────────────────

@Composable
fun V2PharmacyScreen(
    viewModel: ZadViewModel,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.pharmacyItems.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.isEmpty()) {
            item { V2EmptyHint() }
        }
        items(items.size) { i ->
            V2MedRow(items[i])
        }
    }
}

@Composable
private fun V2MedRow(med: ZadPharmacyItem) {
    val low = med.remainingQuantity <= 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCard)
            .background(if (low) Color(0xFFFCE8ED) else Color.White)
            .pressableScale { }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFFCE8ED)),
            contentAlignment = Alignment.Center,
        ) { Text("💊", fontSize = 20.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(med.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
            Text(
                listOfNotNull(med.dosage, med.doseTimes?.let { "⏰ $it" }).joinToString(" · ").ifBlank { med.category },
                fontSize = 12.sp, color = ZadV2.gray500,
            )
        }
        Text(
            "${med.remainingQuantity} ${med.unit}",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (low) ZadV2.danger else ZadV2.green800,
        )
    }
}

// ── Shopping ─────────────────────────────────────────────────────────────────

@Composable
fun V2ShoppingScreen(
    viewModel: ZadViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val list by viewModel.shoppingList.collectAsState()
    val pending = list.filter { !it.isPurchased }
    val done = list.filter { it.isPurchased }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pending.isEmpty() && done.isEmpty()) {
            item { V2EmptyHint() }
        }
        item { Text(stringResource(R.string.v2_to_buy), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink) }
        pending.forEach { item ->
            item { V2ShopRow(item, context, onToggle = { viewModel.toggleShoppingItemPurchased(item.id) }) }
        }
        if (done.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.v2_purchased),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            done.forEach { item ->
                item { V2ShopRow(item, context, onToggle = { viewModel.toggleShoppingItemPurchased(item.id) }) }
            }
        }
    }
}

@Composable
private fun V2ShopRow(item: ZadShoppingItem, context: android.content.Context, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCard)
            .background(Color.White)
            .pressableScale(onClick = onToggle)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.5.dp, if (item.isPurchased) Color.Transparent else ZadV2.gray400, CircleShape)
                .clip(CircleShape)
                .background(if (item.isPurchased) ZadV2.green800 else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (item.isPurchased) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            item.itemName,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (item.isPurchased) ZadV2.gray400 else ZadV2.ink,
            modifier = Modifier.weight(1f),
        )
        if (item.estimatedPrice > 0) {
            Text(CurrencyFormatter.format(context, item.estimatedPrice), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV2.slate)
        }
    }
}

// ── Subscriptions ────────────────────────────────────────────────────────────

@Composable
fun V2SubscriptionsScreen(
    viewModel: ZadViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val subs by viewModel.subscriptions.collectAsState()
    val active = subs.filter { it.isActive }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (active.isEmpty()) {
            item { V2EmptyHint() }
        }
        active.forEach { sub ->
            item { V2SubRow(sub, context) }
        }
    }
}

@Composable
private fun V2SubRow(sub: ZadSubscription, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCardLg)
            .background(Color.White)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE8F1FC)),
            contentAlignment = Alignment.Center,
        ) { Text("🔄", fontSize = 19.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(sub.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
            Text(sub.renewalDate?.take(10) ?: "", fontSize = 12.sp, color = ZadV2.gray500)
        }
        Text(CurrencyFormatter.format(context, sub.amount), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZadV2.green800)
    }
}

// ── Maintenance ──────────────────────────────────────────────────────────────

@Composable
fun V2MaintenanceScreen(
    viewModel: ZadViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items by viewModel.maintenanceItems.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.isEmpty()) {
            item { V2EmptyHint() }
        }
        items(items.size) { i ->
            V2MaintenanceRow(items[i], context)
        }
    }
}

@Composable
private fun V2MaintenanceRow(m: ZadMaintenanceItem, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZadV2.rCardLg)
            .background(Color.White)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFDF3E1)),
            contentAlignment = Alignment.Center,
        ) { Text("🛠️", fontSize = 18.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
            Text(
                listOfNotNull(
                    m.category,
                    m.warrantyExpiryDate?.let { stringResource(R.string.v2_warranty_until, it.take(10)) },
                ).joinToString(" · "),
                fontSize = 12.sp, color = ZadV2.gray500,
            )
        }
        if (m.estimatedCost > 0) {
            Text(CurrencyFormatter.format(context, m.estimatedCost), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV2.warn)
        }
    }
}

@Composable
private fun V2EmptyHint() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.v2_empty_section), fontSize = 13.sp, color = ZadV2.gray400)
    }
}
