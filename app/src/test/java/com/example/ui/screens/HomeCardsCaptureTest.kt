package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.AiAgentAlert
import com.example.data.AiAgentSummary
import com.example.data.AiExpensePrediction
import com.example.data.AiSeasonalForecast
import com.example.ui.components.ZadCanvasBackground
import com.example.ui.theme.AppTheme
import com.example.ui.theme.background
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * لقطات مرجعية لكروت HomeScreen اللي فيها ألوان خام ومالهاش تغطية.
 *
 * HomeScreen فيه ٦٣ لون خام، ٣٠ منهم في KidsModeContent (مقصودين بقاعدة CLAUDE.md
 * ومغطّيين أصلاً) و٧ في AiAlertBanner/التنبيهات بتينت موثّق سببه. الباقي هنا،
 * و`AgentSummaryCard` و`UrgentRecipeCard` كان عندهم **صفر** تغطية — يعني أي مقارنة
 * "قبل/بعد" كانت هتقول "مفيش تغيير" وهي عمياء.
 *
 * الساعة موقوفة زي GlanceCardsCaptureTest: الكروت دي فيها حالات تحميل وأنيميشن،
 * ولقطة عند طور عشوائي بتخلي أي فرق بكسل ضوضاء.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1080dp-h2400dp-xxhdpi")
class HomeCardsCaptureTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))

        private const val ANIMATION_FRAME_MS = 1_200L
    }

    private fun shot(name: String) =
        "build/outputs/roborazzi/$name${if (dark) "_dark" else ""}.png"

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent { AppTheme(darkTheme = dark) { content() } }
        composeTestRule.mainClock.advanceTimeBy(ANIMATION_FRAME_MS)
        composeTestRule.onRoot().captureRoboImage(filePath = shot(name))
    }

    /**
     * الكارت ده فيه التدرّج الهجين: طرف توكن (`primaryContainer`) وطرف مثبّت
     * (`#0A382C`). في الدارك مود طرف بيتحرك والتاني لأ. ملفوف في الكانفس زي ما
     * HomeScreen بيركّبه، عشان النص الأبيض جوّاه يتقاس على أرضيته الحقيقية.
     */
    @Test
    fun captureAgentSummaryCard() = capture("home_agent_summary_card") {
        Box(modifier = Modifier.fillMaxSize()) {
            ZadCanvasBackground(modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.padding(16.dp)) {
                AgentSummaryCard(
                    agentSummary = AiAgentSummary(
                        summary = "صرفك الأسبوع ده أقل من المتوسط بـ12%. المخزون فيه صنفين قربوا يخلصوا.",
                        alerts = listOf(
                            AiAgentAlert(type = "warning", title = "الميزانية", description = "فاضل 18% من ميزانية الشهر"),
                            AiAgentAlert(type = "success", title = "توفير", description = "وفّرت 340 هذا الشهر"),
                        ),
                    ),
                    isLoading = false,
                    onRefresh = {},
                    onNavigateToAssistant = {},
                    onNavigateToShopping = {},
                    onNavigateToInventory = {},
                )
            }
        }
    }

    /** حالة التحميل — فرع تاني بألوانه، ولو ماتصوّرش أي تغيير فيه هيعدّي صامت. */
    @Test
    fun captureAgentSummaryCard_loading() = capture("home_agent_summary_loading") {
        Box(modifier = Modifier.fillMaxSize()) {
            ZadCanvasBackground(modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.padding(16.dp)) {
                AgentSummaryCard(
                    agentSummary = null, isLoading = true, onRefresh = {},
                    onNavigateToAssistant = {}, onNavigateToShopping = {}, onNavigateToInventory = {},
                )
            }
        }
    }

    /** `#FED7AA` دايرة أيقونة شاحبة جوه Surface بيتبع الثيم — نفس فئة باج كروت الرئيسية. */
    @Test
    fun captureUrgentRecipeCard() = capture("home_urgent_recipe_card") {
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            UrgentRecipeCard(
                triggerItems = listOf("طماطم", "بصل"),
                text = "عندك طماطم وبصل قربوا يخلصوا — تحب وصفة تستهلكهم؟",
                onOpenChat = {},
            )
        }
    }

    /**
     * التلات كروت دي بتحمل الألوان الشاردة: `#F9A825` كهرماني في PredictionCard
     * و EventsRadarCard، و`#FDECEA` في AiAlertBanner. اتلمّوا في لقطة واحدة عشان
     * المقارنة تشوفهم مع بعض على نفس الأرضية.
     */
    @Test
    fun captureScatteredColourCards() = capture("home_scattered_colour_cards") {
        Box(modifier = Modifier.background(background).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // predictedTotal بين 80% و100% من الميزانية = الفرع الكهرماني بالظبط
                PredictionCard(
                    prediction = AiExpensePrediction(predictedTotal = 4_500.0, confidence = 0.8),
                    budget = 5_000.0,
                )
                EventsRadarCard(
                    forecasts = listOf(
                        AiSeasonalForecast(eventId = "ramadan", slug = "ramadan", daysUntil = 12, predictedTotal = 1_800.0)
                    )
                )
                AiAlertBanner(
                    title = "تنبيه ميزانية",
                    description = "تجاوزت 90% من ميزانية الشهر ولسه فاضل 9 أيام.",
                )
            }
        }
    }
}
