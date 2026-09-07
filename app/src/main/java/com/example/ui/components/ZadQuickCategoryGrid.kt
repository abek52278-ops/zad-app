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
/**
 * الأقسام اللي بتفتح شاشة خاصة بيها — مقابل باقي التصنيفات اللي كلها بتروح
 * نفس شاشة التسوق (`else -> onNavigateToShopping` في HomeScreen).
 */
private val PRIMARY_DESTINATIONS = listOf(
    ZadCategoryType.PHARMACY,
    ZadCategoryType.FAMILY,
    ZadCategoryType.TASBIHA,
    ZadCategoryType.SUBSCRIPTIONS,
    ZadCategoryType.MAINTENANCE,
)

/**
 * ترتيب العرض مفصول عن ترتيب الـenum عن قصد.
 *
 * الـenum مرتّب بالتصنيف: ست فئات مقاضي الأول، وأقسام التطبيق بعدهم. والصف كان
 * بيعرضه زي ما هو — فالستة اللي **كلهم بيروحوا نفس الشاشة** كانوا ماسكين كل
 * المساحة الظاهرة، والخمسة اللي بيفتحوا شاشات مختلفة من ٧ لـ١١.
 *
 * وTASBIHA كانت الأخيرة. الصف عرضه ~1220dp (١١ × 100dp + فواصل 12dp) على شاشة
 * ~400dp، يعني الزرار على بعد ~820dp ورا سحب أفقي من غير أي إشارة إنه موجود.
 * ده اللي اتقري في اختبار الجهاز كـ«زر التسبيح مختفي»: الزرار مرسوم فعلاً،
 * بس مفيش طريقة معقولة العميل يوصله بيها.
 *
 * الباقي بيتضاف بترتيب الـenum، فأي فئة جديدة بتظهر تلقائياً في الآخر بدل ما
 * تتنسي من القايمة.
 */
internal val categoryDisplayOrder: List<ZadCategoryType> =
    PRIMARY_DESTINATIONS + (ZadCategoryType.entries - PRIMARY_DESTINATIONS.toSet())

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
        items(categoryDisplayOrder) { category ->
            ZadCategoryCard(
                category = category,
                modifier = Modifier.width(100.dp),
                onClick = { onCategoryClick(category) }
            )
        }
    }
}
