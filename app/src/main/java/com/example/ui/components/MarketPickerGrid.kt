package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Market
import com.example.ui.theme.*

/**
 * مرحلة ٢ (docs/agent/PLAN_2026_08_06_rebuild.md) — مكوّن واحد لاختيار البلد، مستخدم في
 * الـ onboarding (MarketSelectionScreen) وفي البروفايل ("البلد والعملة") مع بعض. كان كل
 * مكان بيعمل الاختيار بطريقته الخاصة (كارت قايمة هنا، Row متساوي العرض هناك) — Row
 * بالذات كانت بتتكسر مع أكتر من ٣-٤ أسواق (١٩ عنصر في صف واحد بعرض متساوي). بحث بسيط
 * (اسم البلد أو كود العملة) لأن ١٩ سوق صعب تتصفح بعين مجردة.
 */
@Composable
fun MarketPickerGrid(
    selected: Market?,
    onSelect: (Market) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) Market.entries.toList()
        else Market.entries.filter {
            it.displayNameAr.contains(query, ignoreCase = true) ||
                it.currencyCode.contains(query, ignoreCase = true) ||
                it.currencySymbol.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.market_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
        ) {
            itemsIndexed(filtered, key = { index, market -> "${market.countryCode}_$index" }) { _, market ->
                val isSelected = market == selected
                val cellShape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .clip(cellShape)
                        .background(if (isSelected) primary.copy(alpha = 0.12f) else surfaceContainer)
                        .then(if (isSelected) Modifier.border(1.5.dp, primary, cellShape) else Modifier)
                        .clickable { onSelect(market) }
                        .padding(vertical = 14.dp, horizontal = 6.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Text(market.flagEmoji, fontSize = 28.sp)
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        market.displayNameAr,
                        style = Typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) primary else onSurface,
                        maxLines = 1
                    )
                    Text(market.currencySymbol, style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        }
    }
}
