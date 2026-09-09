package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.NearbyStore
import com.example.ui.components.AppearOnEntry
import com.example.ui.theme.background
import androidx.compose.foundation.background

/**
 * عروض قريبة — شاشة البروتوتايب (deals): متاجر قريبة بمسافاتها + إخلاء
 * مسؤولية صريح («مسافات من خرائط مفتوحة — ليست أسعاراً أو عروضاً فعلية»).
 * البيانات من LocationIQ/Overpass الفعليين — مفيش أسعار مخترعة.
 */
@Composable
fun NearbyDealsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var supermarkets by remember { mutableStateOf<List<NearbyStore>>(emptyList()) }
    var pharmacies by remember { mutableStateOf<List<NearbyStore>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val location = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.data.LocationHelper.getCurrentLocation(context)
            }
            if (location != null) {
                supermarkets = com.example.data.LocationIqRepo.findNearbySupermarkets(location.latitude, location.longitude)
                    .ifEmpty { com.example.data.OverpassRepo.findNearbySupermarkets(location.latitude, location.longitude) }
                pharmacies = com.example.data.LocationIqRepo.findNearbyPharmacies(location.latitude, location.longitude)
                    .ifEmpty { com.example.data.OverpassRepo.findNearbyPharmacies(location.latitude, location.longitude) }
            }
        } catch (_: Exception) { /* بيرجع فاضية والشاشة تعرض الحالة */ }
        loading = false
    }

    Scaffold(containerColor = background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = com.example.ui.theme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_deals), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.onSurface)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // إخلاء المسؤولية — نفس نص البروتوتايب بالظبط
                    androidx.compose.material3.Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                        Text(
                            stringResource(R.string.nearby_deals_disclaimer),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = com.example.ui.theme.primary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (loading) {
                    item { Text(stringResource(R.string.loading), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = com.example.ui.theme.onSurfaceVariant) }
                }
                if (!loading && supermarkets.isEmpty() && pharmacies.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_stores_found), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = com.example.ui.theme.onSurfaceVariant)
                    }
                }
                if (supermarkets.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.deals_supermarkets)) }
                    itemsIndexed(supermarkets, key = { index, store -> "supermarket_${store.lat}_${index}" }) { _, store ->
                        AppearOnEntry { NearbyStoreCard(store, lowStockNames = emptyList(), isPharmacy = false) }
                    }
                }
                if (pharmacies.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.deals_pharmacies)) }
                    itemsIndexed(pharmacies, key = { index, store -> "pharmacy_${store.lat}_${index}" }) { _, store ->
                        AppearOnEntry { NearbyStoreCard(store, lowStockNames = emptyList(), isPharmacy = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.onSurface)
}
