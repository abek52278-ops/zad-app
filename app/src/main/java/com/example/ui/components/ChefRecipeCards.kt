package com.example.ui.components

import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CurrencyFormatter
import com.example.data.ZadRecipe
import com.example.ui.theme.Typography
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.theme.secondaryDark
import com.example.ui.theme.surface

/**
 * كروت وصفات شيف زاد.
 *
 * الكارت القديم كان بيعرض فقرة نص واحدة. فقرة مينفعش يتطبخ منها، ومينفعش تقول ناقصك إيه،
 * ومينفعش تحط الناقص في قائمة التسوق. دي بتتبني من `recipes` اللي الأكشن بقى يرجّعها.
 *
 * صف أفقي مش عمودي: الاقتراحات ٣ لـ٥، وعرضهم فوق بعض كان هياكل الشاشة الرئيسية كلها
 * لحاجة العميل بيتصفّحها بسرعة قبل ما يقرر.
 */
@Composable
fun ChefRecipeRow(
    recipes: List<ZadRecipe>,
    onAddMissingToShopping: (List<String>) -> Unit,
    ratedRecipes: Map<String, Boolean> = emptyMap(),
    onRate: (String, Boolean) -> Unit = { _, _ -> },
) {
    if (recipes.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(recipes) { recipe ->
            ChefRecipeCard(
                recipe = recipe,
                onAddMissingToShopping = onAddMissingToShopping,
                liked = ratedRecipes[recipe.recipeName],
                onRate = { liked -> onRate(recipe.recipeName, liked) },
            )
        }
    }
}

@Composable
private fun ChefRecipeCard(
    recipe: ZadRecipe,
    onAddMissingToShopping: (List<String>) -> Unit,
    liked: Boolean?,
    onRate: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    // الموديل أحياناً بيرجّع وصفة من غير cooking_instructions (حقل ناقص في رد الـ AI).
    // كارت clickable ملوش حاجة يوسّعها كان بيدّي ضغطة من غير أي رد فعل — يبان معطوب.
    // بدل ما نختلق خطوات، الكارت مش قابل للتوسيع أصلاً لو مفيش خطوات فعلاً.
    val expandable = recipe.cookingInstructions.isNotEmpty()

    Column(
        modifier = Modifier
            .width(260.dp)
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .let { if (expandable) it.clickable { expanded = !expanded } else it }
            .padding(bottom = 14.dp),
    ) {
        // الصورة بتيجي من Pexels عبر الأكشن. لو مفيش رابط (النموذج نسي image_keyword_en،
        // أو المفتاح ناقص) FoodImage بتدوّر بنفسها باسم الوصفة العربي — Pexels بتفهم
        // العربي، وده اللي Unsplash ماكانتش بتعمله. ولو برضه مفيش، أيقونة: أكلة من غير
        // صورة أحسن من كارت مكسور.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFFFDF3E1)),
            contentAlignment = Alignment.Center,
        ) {
            FoodImage(
                query = recipe.recipeName,
                imageUrl = recipe.imageUrl,
                contentDescription = recipe.recipeName,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            ) {
                Icon(
                    Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = secondaryDark,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                recipe.recipeName,
                style = Typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )
            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (recipe.prepTimeMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${recipe.prepTimeMinutes} د", style = Typography.labelSmall, color = onSurfaceVariant)
                    }
                }
                if (recipe.costEstimate > 0) {
                    Text(
                        CurrencyFormatter.format(context, recipe.costEstimate),
                        style = Typography.labelSmall,
                        color = onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                // إعجاب/عدم إعجاب — بيغذّي الاقتراحات الجاية (rate_recipe). ضغطة تانية
                // على نفس الرأي بترجّعه لغير محدد، مش قفل عليه.
                Icon(
                    Icons.Default.ThumbUp,
                    contentDescription = stringResource(R.string.chef_recipe_like_action),
                    tint = if (liked == true) primary else onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onRate(true) },
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.ThumbDown,
                    contentDescription = stringResource(R.string.chef_recipe_dislike_action),
                    tint = if (liked == false) secondaryDark else onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onRate(false) },
                )
            }

            if (recipe.missingIngredientsToBuy.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ناقصك: " + recipe.missingIngredientsToBuy.joinToString("، "),
                    style = Typography.labelSmall,
                    color = onSurfaceVariant,
                )
                // الزرار ده هو السبب إن `missing_ingredients_to_buy` محدّدة بدقة في
                // البرومبت: اللي جواها بيتحوّل لقائمة تسوق على طول، فأي صنف زيادة فيها
                // بيكلّف العميل فلوس بجد.
                TextButton(
                    onClick = { onAddMissingToShopping(recipe.missingIngredientsToBuy) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.AddShoppingCart, null, tint = primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.auto_comp_chefrecipecards_34776), style = Typography.labelMedium, color = primary)
                }
            }

            // الخطوات مطويّة افتراضياً — الكارت وظيفته الاختيار، والخطوات بتبان لما يختار.
            if (expanded && recipe.cookingInstructions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                recipe.cookingInstructions.forEachIndexed { i, step ->
                    Text(
                        "${i + 1}. $step",
                        style = Typography.bodySmall,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            } else if (recipe.cookingInstructions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.auto_comp_chefrecipecards_23405), style = Typography.labelSmall, color = primary)
            }
        }
    }
}
