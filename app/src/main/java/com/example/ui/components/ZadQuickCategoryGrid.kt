package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * منتقي الأقسام السريع — يفتح البوابة 3 (المخزون والتسوق) أو البوابة الصح مباشرة
 * حسب القسم، بدل ما يودّي كلها لمكان واحد. كل زر بينده callback حقيقي موجود أصلاً
 * في HomeScreen (onNavigateToShopping/Pharmacy/Subscriptions/Family/Tasbiha) —
 * مفيش route مختلق. فلترة التصنيف الدقيقة جوه شاشة التسوق نفسها برة نطاق التاسك ده
 * (محتاجة تعديل PantryShoppingScreen/ViewModel، مش HomeScreen وكومبوننتاتها).
 */
@Composable
fun ZadQuickCategoryGrid(
    onCategoryClick: (ZadCategoryType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(ZadCategoryType.entries) { category ->
            ZadCategoryCard(
                category = category,
                modifier = Modifier.width(100.dp),
                onClick = { onCategoryClick(category) }
            )
        }
    }
}
