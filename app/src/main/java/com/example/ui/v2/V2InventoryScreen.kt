package com.example.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ZadInventory
import com.example.ui.theme.ZadV2

data class V2Category(
    val key: String?,
    val labelRes: Int,
    val emoji: String,
    val bg: Color,
)

private val v2InventoryCategories = listOf(
    V2Category("fruits_veg", R.string.cat_fruits_veg, "🧺", Color(0xFFE3F5EC)),
    V2Category("oils", R.string.cat_oils, "🫒", Color(0xFFFCEEE3)),
    V2Category("meat_fish", R.string.cat_meat_fish, "🥩", Color(0xFFFCE8ED)),
    V2Category("bakery", R.string.cat_bakery, "🥖", Color(0xFFF1EAFB)),
    V2Category("dairy", R.string.cat_dairy, "🧈", Color(0xFFFDF3E1)),
    V2Category("beverages", R.string.cat_beverages, "🥤", Color(0xFFE8F1FC)),
)

private fun matchCategory(raw: String?, key: String?): Boolean {
    if (key == null) return true
    val c = (raw ?: return false).lowercase()
    return when (key) {
        "fruits_veg" -> c.contains("خضار") || c.contains("فاكهة") || c.contains("fruit") || c.contains("veget")
        "oils" -> c.contains("زيت") || c.contains("oil") || c.contains("سمن")
        "meat_fish" -> c.contains("لحوم") || c.contains("سمك") || c.contains("meat") || c.contains("fish") || c.contains("دواجن")
        "bakery" -> c.contains("مخبوزات") || c.contains("bakery") || c.contains("خبز") || c.contains("snack")
        "dairy" -> c.contains("ألبان") || c.contains("البان") || c.contains("dairy") || c.contains("بيض") || c.contains("egg")
        "beverages" -> c.contains("مشروبات") || c.contains("bever") || c.contains("عصير")
        else -> false
    }
}

@Composable
fun V2InventoryScreen(
    viewModel: com.example.ui.viewmodels.ZadViewModel,
    modifier: Modifier = Modifier,
) {
    val inventory by viewModel.inventory.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }

    val visible = if (selected == null) inventory else inventory.filter { matchCategory(it.category, selected) }
    val runningOut = inventory.filter {
        (it.lowStockThreshold ?: 2) >= it.quantity
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (runningOut.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.v2_running_out),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink,
                    )
                    runningOut.take(3).forEach { item ->
                        V2ProductRow(item = item, highlight = true)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.v2_categories),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                v2InventoryCategories.chunked(2).forEach { rowCats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowCats.forEach { cat ->
                            val selectedBg = if (selected == cat.key) ZadV2.green800 else cat.bg
                            val count = inventory.count { matchCategory(it.category, cat.key) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(selectedBg)
                                    .pressableScale { selected = if (selected == cat.key) null else cat.key }
                                    .padding(vertical = 20.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.75f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(cat.emoji, fontSize = 26.sp) }
                                Text(
                                    stringResource(cat.labelRes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected == cat.key) Color.White else ZadV2.ink,
                                )
                                Text(
                                    "$count",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected == cat.key) Color.White.copy(alpha = 0.8f) else ZadV2.gray500,
                                )
                            }
                        }
                        if (rowCats.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Text(
                if (selected == null) stringResource(R.string.v2_all_items)
                else stringResource(v2InventoryCategories.first { it.key == selected }.labelRes),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink,
            )
        }
        if (visible.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.v2_empty_section), fontSize = 13.sp, color = ZadV2.gray400)
                }
            }
        } else {
            items(visible) { item ->
                V2ProductRow(item = item, highlight = false)
            }
        }
    }
}

@Composable
private fun V2ProductRow(item: ZadInventory, highlight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Color(0xFFFCE8ED) else Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.itemName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV2.ink)
            Text(
                item.category ?: "",
                fontSize = 12.sp,
                color = ZadV2.gray500,
            )
        }
        Text(
            "${item.quantity} ${item.unit ?: ""}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) ZadV2.danger else ZadV2.green800,
        )
    }
}
