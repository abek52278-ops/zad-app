package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.AsyncImage
import com.example.data.PexelsRepo

/**
 * صورة أكل واحدة — من الرابط اللي جه مع الوصفة، وإلا من Pexels بالاسم، وإلا البديل.
 *
 * الشاشات كانت بتتعامل مع الحالة دي كل واحدة لوحدها وبنتايج مختلفة: كارت الشيف كان
 * بيعرض `recipe.imageUrl` ولو null يرسم أيقونة، وشاشة تفاصيل الوصفة **مكانش فيها صورة
 * شبكة خالص** — تدرّج لوني وإيموجي، فاضل من وقت ما Unsplash قفلت الـ hotlink بتاعها.
 * فنفس الوصفة بالظبط كان ليها صورة في الكارت ومالهاش في الشاشة اللي المفروض تكون أوضح.
 *
 * الترتيب هنا مقصود:
 *
 * ١. `imageUrl` لو موجود — الأكشن جابه بالفعل مع الوصفة، فمفيش سبب نسأل تاني.
 * ٢. وإلا بحث Pexels بـ[query] عن طريق [PexelsRepo] (بيتخزّن محلياً، فتمريرة تانية على
 *    نفس الشاشة مبتكلفش شبكة).
 * ٣. وإلا [fallback] — أيقونة أو إيموجي حسب الشاشة.
 *
 * البديل بيتعرض كمان **أثناء التحميل**، مش سبينر. صورة الأكل تزيين مش محتوى؛ سبينر
 * مكانها بيقول للعميل "استنى" على حاجة مش مستنيها، والكارت بيبان مكسور لحظة أطول.
 */
@Composable
fun FoodImage(
    query: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Robolectric/الـPreview مالهومش شبكة، والنداء هناك بيبقى تأخير من غير أي فايدة —
    // نفس سبب LocalInspectionMode في أي مكان تاني بيلمس IO.
    val inspecting = LocalInspectionMode.current

    var resolved by remember(query, imageUrl) { mutableStateOf(imageUrl) }
    // فشل تحميل الرابط نفسه (رابط ميت، أوفلاين) مش نفس "مفيش رابط". Coil بياخد
    // `error` كـPainter مش composable، فمن غير الحالة دي الكارت كان هيعرض مربع فاضي بدل
    // البديل — وهو بالظبط اللي الدالة دي موجودة عشان تمنعه.
    var failed by remember(query, imageUrl) { mutableStateOf(false) }
    // بيمنع اللف: رابط الوصفة يفشل → ندوّر على Pexels → ده كمان يفشل → نقف عند البديل.
    var lookedUp by remember(query, imageUrl) { mutableStateOf(false) }

    LaunchedEffect(query, imageUrl, failed) {
        if (inspecting || query.isBlank() || lookedUp) return@LaunchedEffect
        // بندوّر لما مفيش رابط أصلاً، أو لما الرابط اللي جه مع الوصفة طلع ميت — التانية
        // دي هي اللي بتخلي رابط قديم متخزّن في وصفة ما يبقاش حكم نهائي على الصورة.
        if (resolved != null && !failed) return@LaunchedEffect
        lookedUp = true
        val found = PexelsRepo.imageUrlFor(context, query)
        if (found != null && found != resolved) {
            resolved = found
            failed = false
        }
    }

    val url = resolved
    if (url == null || failed) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { fallback() }
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onError = { failed = true },
            modifier = modifier,
        )
    }
}
