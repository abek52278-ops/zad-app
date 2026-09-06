package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.ZadInventory
import com.example.data.ZadPharmacyItem
import com.example.data.ZadSubscription
import com.example.ui.theme.AppTheme
import com.example.ui.theme.background
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * لقطات المرجع لكل كومبوزابل في ZadHomeGlanceCards، لايت ودارك.
 *
 * ليه ملف لوحده مش إضافة على PreviewTest: الهدف إني أقدر أشغّل الكتلة دي وحدها
 * (`--tests "*GlanceCardsCaptureTest*"`) وقت ترحيل الألوان. تشغيل PreviewTest كامل
 * بيطلع ٩٢ لقطة وبيقع بـ OOM في الكونتينر ده؛ الملف ده ١٤ لقطة وبيخلص في ثواني.
 *
 * **الساعة موقوفة عن قصد** (`autoAdvance = false` + `advanceTimeBy` ثابت). تلاتة من
 * الكومبوزابلز دي فيها `rememberInfiniteTransition` (لمعان الشريط، نبض الكارت
 * البريميوم، دوران الكرة العصبية). من غير إيقاف الساعة كل لقطة بتتصوّر عند طور
 * عشوائي من الأنيميشن، وساعتها أي مقارنة "قبل/بعد" بالبكسل بتبقى ضوضاء صافية —
 * الفرق اللي هيطلع هيكون فرق طور مش فرق لون. ده شرط لازم للبروتوكول مش تحسين.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1080dp-h2400dp-xxhdpi")
class GlanceCardsCaptureTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))

        /** طور ثابت من الأنيميشن — أي رقم ينفع طالما هو نفسه في "قبل" و"بعد". */
        private const val ANIMATION_FRAME_MS = 1_200L
    }

    private fun shot(name: String) =
        "build/outputs/roborazzi/$name${if (dark) "_dark" else ""}.png"

    @get:Rule
    val composeTestRule = createComposeRule()

    /** يوقّف الساعة، يركّب المحتوى، يقدّم لطور ثابت، يصوّر. */
    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { AppTheme(darkTheme = dark) { content() } }
        composeTestRule.mainClock.advanceTimeBy(ANIMATION_FRAME_MS)
        composeTestRule.onRoot().captureRoboImage(filePath = shot(name))
    }

    // ── ١. شريط الاختصارات ────────────────────────────────────────────────────
    /**
     * ملفوف في ZadCanvasBackground عن قصد: عنوان كل اختصار وعنوانه الفرعي
     * بيترسموا **برّه** الكبسولة المتدرجة، يعني على خلفية الصفحة نفسها. لو اتصوّر
     * على خلفية بيضا مفروضة، الباج اللي بندوّر عليه (نص غامق على كانفس غامق في
     * الدارك مود) مش هيبان في اللقطة خالص.
     */
    @Test
    fun captureShortcutsRail() = capture("glance_shortcuts_rail") {
        Box(modifier = Modifier.fillMaxSize()) {
            ZadCanvasBackground(modifier = Modifier.fillMaxSize())
            ZadHorizontalShortcutsRail(
                onNavigateToInventory = {},
                onNavigateToShopping = {},
                onNavigateToFamily = {},
                onNavigateToSubscriptions = {},
                onNavigateToPharmacy = {},
                onNavigateToMaintenance = {},
                onNavigateToTasbiha = {},
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }

    // ── ٢. كارت نواقص الأكل ───────────────────────────────────────────────────
    /**
     * سبعة أصناف بسبع فئات مختلفة عشان تغطّي فروع `catBg` السبعة كلها (فاكهة/خضار/
     * ألبان/مخبوزات/لحوم/مشروبات + الـ else). بأقل من كده فيه تينت مش هيترسم وأي
     * تغيير فيه هيعدّي من المقارنة من غير ما يبان.
     *
     * العرض 1280dp مش 1080dp الافتراضي: التايلز في LazyRow بعرض 155dp للواحد،
     * فالسبعة محتاجين ~1181dp. عند 1080 السابع مش بيتركّب أصلاً.
     */
    @Config(sdk = [33], qualifiers = "w1280dp-h2400dp-xxhdpi")
    @Test
    fun captureFoodShortages() = capture("glance_food_shortages") {
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            ZadFoodShortagesGlanceCard(
                inventory = listOf(
                    inv("تفاح", "فاكهة", 1),
                    inv("طماطم", "خضار", 2),
                    inv("لبن", "ألبان", 5),
                    inv("عيش", "مخبوزات", 1),
                    inv("لحمة", "لحوم", 3),
                    inv("عصير", "مشروبات", 8),
                    inv("أرز", "بقالة", 2),
                ),
                onViewAllClick = {}
            )
        }
    }

    // ── ٣. كارت الاشتراكات ────────────────────────────────────────────────────
    @Test
    fun captureSubscriptions() = capture("glance_subscriptions") {
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            ZadSubscriptionsGlanceCard(
                subscriptions = listOf(
                    ZadSubscription(
                        title = "نتفليكس",
                        amount = 56.0,
                        renewalDate = LocalDate.now().plusDays(3).toString(),
                        category = "ترفيه"
                    ),
                    ZadSubscription(
                        title = "الجيم",
                        amount = 250.0,
                        renewalDate = LocalDate.now().plusDays(19).toString(),
                        category = "صحة"
                    ),
                ),
                onViewAllClick = {}
            )
        }
    }

    // ── ٤. كارت الصيدلية ──────────────────────────────────────────────────────
    /**
     * `adherencePercent` و`nextDoseItem` مبعوتين عشان فرع "الجرعة الجاية" يترسم —
     * هو اللي فيه الشيك الأبيض وشريحة الالتزام. من غيرهم نص الكارت مابيترسمش.
     */
    @Test
    fun capturePharmacy() = capture("glance_pharmacy") {
        val insulin = ZadPharmacyItem(
            name = "أنسولين",
            category = "مزمن",
            dosage = "10 وحدات",
            remainingQuantity = 12,
            unit = "قلم",
            dailyDoseCount = 2,
            doseTimes = "08:00,20:00"
        )
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            ZadPharmacyGlanceCard(
                pharmacyItems = listOf(
                    insulin,
                    ZadPharmacyItem(name = "فيتامين د", category = "فيتامين", remainingQuantity = 4)
                ),
                onViewAllClick = {},
                adherencePercent = 88,
                nextDoseItem = insulin,
                nextDoseTime = "20:00"
            )
        }
    }

    // ── ٥. كارت البريميوم ─────────────────────────────────────────────────────
    @Test
    fun capturePremiumPromo() = capture("glance_premium_promo") {
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            ZadPremiumPromoCard(onUpgradeClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }

    // ── ٦. الكرة العصبية ──────────────────────────────────────────────────────
    /** `nodes = null` عن قصد: بيوقّع على العقد التسعة الافتراضية بألوان هويتها التسعة. */
    @Test
    fun captureNeuralSphere() = capture("glance_neural_sphere") {
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            Zad3DNeuralSphereWidget(
                stats = null,
                nodes = null,
                onNodeClick = {},
                onViewFullMapClick = {},
                onOpenDossierClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // ── ٧. ورقة الملف التنفيذي ────────────────────────────────────────────────
    @Test
    fun captureExecutiveDossier() = capture("glance_executive_dossier") {
        Box(modifier = Modifier.fillMaxSize().background(background)) {
            ZadExecutiveDossierSheet(
                onDismiss = {},
                totalSpent = 4820.0,
                safeDailySpend = 145.0,
                forecastNextMonth = 5100.0,
                familyMembersCount = 4,
                pharmacyAdherencePct = 88,
                lowStockItemCount = 3
            )
        }
    }

    private fun inv(name: String, category: String, qty: Int) = ZadInventory(
        itemName = name,
        category = category,
        quantity = qty,
        unit = "قطعة",
        lowStockThreshold = 2
    )
}
