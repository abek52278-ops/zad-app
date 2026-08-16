package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ZadAiRepository
import com.example.data.ZadInventory
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.pressableScale
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ParsedRecipe(
    val ingredients: List<String>,
    val steps: List<String>
)

/** Static keyword→emoji map so recipe headers show a relevant dish glyph without any network image. */
private fun dishEmojiFor(title: String): String = when {
    title.contains("بيض") -> "🍳"
    title.contains("تونة") || title.contains("سمك") -> "🐟"
    title.contains("دجاج") || title.contains("فراخ") -> "🍗"
    title.contains("لحم") || title.contains("كفتة") -> "🥩"
    title.contains("أرز") || title.contains("رز") -> "🍚"
    title.contains("سلطة") -> "🥗"
    title.contains("مكرونة") || title.contains("باستا") -> "🍝"
    title.contains("شوربة") -> "🍲"
    title.contains("خبز") || title.contains("عيش") -> "🍞"
    title.contains("فاكهة") || title.contains("موز") || title.contains("تفاح") -> "🍎"
    else -> "🍽️"
}

private fun parseRecipeContent(text: String): ParsedRecipe {
    val lines = text.lines()
    val ingredients = mutableListOf<String>()
    val steps = mutableListOf<String>()
    var currentSection = ""

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.contains("المقادير") -> {
                currentSection = "ingredients"
                continue
            }
            trimmed.contains("طريقة التحضير") || trimmed.contains("الطريقة") || trimmed.contains("التحضير") -> {
                currentSection = "steps"
                continue
            }
            currentSection == "ingredients" && trimmed.isNotBlank() -> {
                val cleaned = trimmed
                    .removePrefix("- ")
                    .removePrefix("• ")
                    .removePrefix("* ")
                    .removePrefix("· ")
                    .trim()
                if (cleaned.isNotBlank() && !cleaned.startsWith("#")) {
                    ingredients.add(cleaned)
                }
            }
            currentSection == "steps" && trimmed.isNotBlank() -> {
                val cleaned = trimmed
                    .replaceFirst(Regex("^\\d+[.)\\-]+\\s*"), "")
                    .trim()
                if (cleaned.isNotBlank() && !cleaned.startsWith("#")) {
                    steps.add(cleaned)
                }
            }
        }
    }

    if (ingredients.isEmpty() && steps.isEmpty()) {
        val fallbackSteps = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                fallbackSteps.add(trimmed.replaceFirst(Regex("^\\d+[.)\\-]+\\s*"), "").trim())
            }
        }
        return ParsedRecipe(emptyList(), fallbackSteps.filter { it.isNotBlank() })
    }

    return ParsedRecipe(ingredients, steps)
}

/**
 * ملحوظة: الشاشة الكاملة `RecipeDetailScreen` اتشالت (2026-08-01) — كانت نسخة تانية
 * من نفس الواجهة، بتاخد نص الوصفة جاهز، ومحدش بيناديها. `RecipeDetailDialog` تحت هي
 * المستخدمة فعلياً (بتجيب الوصفة بنفسها وفيها retry)، وبتشارك معاها نفس الـ parsing
 * و RecipeSectionCard.
 */

@Composable
private fun RecipeSectionCard(
    title: String,
    titleColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .padding(20.dp)
            .animateContentSize()
    ) {
        Text(
            text = title,
            style = Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun RecipeDetailDialog(
    recipeTitle: String,
    inventory: List<ZadInventory>,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var recipeText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    val parsed = remember(recipeText) { if (recipeText.isNotBlank()) parseRecipeContent(recipeText) else ParsedRecipe(emptyList(), emptyList()) }
    val checkedIngredients = remember { mutableStateMapOf<Int, Boolean>() }
    val completedSteps = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(recipeTitle, retryKey) {
        isLoading = true
        errorMessage = null
        try {
            val result = withContext(Dispatchers.IO) {
                ZadAiRepository.getRecipeDetails(recipeTitle, inventory)
            }
            recipeText = result
        } catch (e: Exception) {
            errorMessage = "عذراً، حدث خطأ أثناء تحميل الوصفة"
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.ui.components.ZadListCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            contentPadding = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppearOnEntry {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        // كانت إيموجي على تدرّج لوني وبس: hotlink بتاع source.unsplash.com
                        // اتقفل، ومكانش فيه بديل. النتيجة إن نفس الوصفة يبقى ليها صورة في
                        // كارت الشيف ومالهاش في الشاشة اللي المفروض تكون أوضح منه.
                        //
                        // Pexels بتقبل العربي، فاسم الوصفة نفسه استعلام شغّال ومفيش حاجة
                        // محتاجة تترجم. التدرّج والإيموجي فضلوا زي ما هم كبديل — مش
                        // شاشة تحميل: صورة الأكل تزيين، والكارت لازم يبان كامل من أول لحظة.
                        com.example.ui.components.FoodImage(
                            query = recipeTitle,
                            contentDescription = recipeTitle,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(primaryDark, primary, primaryLight)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dishEmojiFor(recipeTitle),
                                    fontSize = 72.sp
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                        startY = 100f
                                    )
                                )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .pressableScale()
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = recipeTitle,
                                style = Typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = primary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "شيف زاد يجهز لك الوصفة...",
                            style = Typography.bodyLarge,
                            color = onSurfaceVariant
                        )
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\u26A0\uFE0F",
                            style = Typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            style = Typography.bodyLarge,
                            color = dangerColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { retryKey++ }, modifier = Modifier.pressableScale()) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إعادة المحاولة")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (parsed.ingredients.isNotEmpty()) {
                            RecipeSectionCard(
                                title = "\uD83E\uDD50  المقادير",
                                titleColor = primary
                            ) {
                                parsed.ingredients.forEachIndexed { index, ingredient ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checkedIngredients[index] == true,
                                            onCheckedChange = { checkedIngredients[index] = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = primary,
                                                uncheckedColor = outline
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = ingredient,
                                            style = Typography.bodyMedium,
                                            color = if (checkedIngredients[index] == true) onSurfaceVariant else onSurface,
                                            textDecoration = if (checkedIngredients[index] == true) TextDecoration.LineThrough else TextDecoration.None,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        if (parsed.steps.isNotEmpty()) {
                            RecipeSectionCard(
                                title = "\uD83D\uDC68\u200D\uD83C\uDF73  طريقة التحضير",
                                titleColor = tertiary
                            ) {
                                parsed.steps.forEachIndexed { index, step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (completedSteps[index] == true) primary
                                                    else primaryContainer
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (completedSteps[index] == true) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = Typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryDark
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = step,
                                            style = Typography.bodyMedium,
                                            color = if (completedSteps[index] == true) onSurfaceVariant else onSurface,
                                            textDecoration = if (completedSteps[index] == true) TextDecoration.LineThrough else TextDecoration.None,
                                            lineHeight = 24.sp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(top = 2.dp)
                                        )
                                    }
                                    if (index < parsed.steps.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }

                        if (parsed.ingredients.isEmpty() && parsed.steps.isEmpty() && recipeText.isNotBlank()) {
                            Text(
                                text = recipeText,
                                style = Typography.bodyLarge,
                                color = onSurface,
                                lineHeight = 28.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                com.example.ui.components.ZadListCard(
                    shape = RoundedCornerShape(0.dp),
                    contentPadding = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp).pressableScale(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = onSurfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, outline)
                        ) {
                            Text(
                                text = "\u2716  \u0625\u063A\u0644\u0627\u0642",
                                style = Typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
