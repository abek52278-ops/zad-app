package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * مرحلة ٥ب-٤ (docs/agent/PLAN_2026_08_06_rebuild.md) — هوية بصرية للخدمات المشهورة في
 * شاشة الاشتراكات. الأيقونات هنا أيقونات Material عامة ملوّنة بلون الخدمة المعروف —
 * **مش الشعار الرسمي نفسه**. استخدام لوجو نتفليكس/سبوتيفاي الفعلي محتاج ترخيص/برند
 * غايدلاينز خارج نطاق تعديل كود مفتوح، ونفس المبدأ اللي التطبيق ماشي عليه أصلاً في كل
 * أيقونات الفئات الملوّنة (`catFoodIcon`, `catBillsIcon`, ...) — لون + أيقونة معبّرة،
 * مش أصول براند حقيقية. أي اسم مايتطابقش مع القايمة بيرجع null والمستدعي بيعرض
 * الأيقونة العامة القديمة (`Icons.Default.Subscriptions`)، نفس السلوك قبل كده بالظبط.
 */
data class SubscriptionBrand(val icon: ImageVector, val color: Color)

private val subscriptionBrands: List<Pair<List<String>, SubscriptionBrand>> = listOf(
    listOf("netflix", "نتفلكس", "نتفليكس") to SubscriptionBrand(Icons.Default.Movie, Color(0xFFE50914)),
    listOf("spotify", "سبوتيفاي", "سبوتيفي") to SubscriptionBrand(Icons.Default.MusicNote, Color(0xFF1DB954)),
    listOf("youtube", "يوتيوب") to SubscriptionBrand(Icons.Default.SmartDisplay, Color(0xFFFF0000)),
    listOf("chatgpt", "openai", "شات جي بي تي", "أوبن إيه آي") to SubscriptionBrand(Icons.Default.AutoAwesome, Color(0xFF10A37F)),
    listOf("amazon prime", "أمازون برايم", "prime video", "برايم") to SubscriptionBrand(Icons.Default.ShoppingBag, Color(0xFFFF9900)),
    listOf("disney") to SubscriptionBrand(Icons.Default.Theaters, Color(0xFF113CCF)),
    listOf("apple music", "آبل ميوزك", "icloud", "آيكلاود") to SubscriptionBrand(Icons.Default.Cloud, Color(0xFF555555)),
    listOf("shahid", "شاهد") to SubscriptionBrand(Icons.Default.LiveTv, Color(0xFF00A651)),
    listOf("stc", "موبايلي", "زين", "vodafone", "فودافون", "orange", "اورانج", "إنترنت", "انترنت") to SubscriptionBrand(Icons.Default.Wifi, Color(0xFF3B82F6)),
    listOf("جيم", "gym", "fitness") to SubscriptionBrand(Icons.Default.FitnessCenter, Color(0xFFF97316)),
    listOf("مياه", "المياه", "water") to SubscriptionBrand(Icons.Default.WaterDrop, Color(0xFF0EA5E9)),
    listOf("كهرباء", "الكهرباء", "كهربا", "electricity") to SubscriptionBrand(Icons.Default.Bolt, Color(0xFFF59E0B))
)

/** بيدوّر في العنوان والمزوّد مع بعض — عشان "Netflix" ممكن تيجي في title أو provider حسب مصدر الاشتراك */
fun subscriptionBrandFor(title: String, provider: String?): SubscriptionBrand? {
    val haystack = "$title ${provider.orEmpty()}".lowercase()
    return subscriptionBrands.firstOrNull { (keys, _) -> keys.any { haystack.contains(it.lowercase()) } }?.second
}
