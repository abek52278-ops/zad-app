package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Figure
import com.example.data.ZadInsight
import com.example.ui.components.GlassCard
import com.example.ui.components.ZadCanvasBackground
import com.example.ui.components.ZadCardHero
import com.example.ui.components.ZadChefCard
import com.example.ui.components.ZadDaysAndSafeSpendRow
import com.example.ui.components.ZadPageShortcutsGrid
import com.example.ui.components.ZadShortcutItem
import com.example.ui.components.ZadStatTile
import com.example.ui.widgets.ZadAmazonDealCard
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.theme.*
import com.example.ui.widgets.ZadQuestionCard
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1080dp-h2400dp-xxhdpi")
class PreviewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureOnboardingScreen() {
        composeTestRule.setContent {
            AppTheme {
                OnboardingScreen(
                    onNavigateToLogin = {},
                    onNavigateToSignUp = {}
                )
            }
        }
        
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/onboarding_screen.png"
        )
    }

    @Test
    fun captureZadQuestionCard_numberType() {
        composeTestRule.setContent {
            AppTheme {
                ZadQuestionCard(
                    insight = ZadInsight(
                        id = "preview-1",
                        kind = "question",
                        title = "كام كيلو رز فاضل؟",
                        body = "آخر مرة سجلت 5 كيلو، عايزين نحدث المخزون",
                        actionType = "number"
                    ),
                    onAnswer = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_question_card_number.png"
        )
    }

    @Test
    fun captureZadQuestionCard_yesNoType() {
        composeTestRule.setContent {
            AppTheme {
                ZadQuestionCard(
                    insight = ZadInsight(
                        id = "preview-2",
                        kind = "question",
                        title = "لسه بتاخد دوا الضغط؟",
                        body = "مبنيش على تذكير الصيدلية اللي فات",
                        actionType = "yes_no"
                    ),
                    onAnswer = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_question_card_yes_no.png"
        )
    }

    // Verbatim content of the first question zad-brain ever emitted from a real daily run
    // (zad_insights row 2312b7d4, dedupe_key=ask_eggs_qty) — kept as-is rather than
    // paraphrased so this capture proves what the user actually sees for that row.
    @Test
    fun captureZadQuestionCard_liveBrainQuestion() {
        composeTestRule.setContent {
            AppTheme {
                ZadQuestionCard(
                    insight = ZadInsight(
                        id = "2312b7d4-c87c-4a6f-97ca-e1a24f81c295",
                        kind = "question",
                        surface = "home_card",
                        title = "البيض",
                        body = "كم عدد البيض الذي لديك حاليًا؟",
                        actionType = "number",
                        aboutItem = "بيض"
                    ),
                    onAnswer = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_question_card_live_eggs.png"
        )
    }

    // Glassmorphism pass (iOS-design alignment task) — ZadCardHero is stateless (plain
    // params, no ViewModel), so it captures directly like the cards above.
    @Test
    fun captureZadCardHero_glassmorphism() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.padding(16.dp)) {
                    ZadCardHero(
                        budget = 3500.0,
                        spent = 1200.0,
                        remaining = 2300.0,
                        daysLeft = 12,
                        onDepositClick = {},
                        available = Figure(value = 2000.0, confident = true),
                        committed = 300.0,
                        nextObligationText = "إيجار بعد 4 أيام"
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_card_hero_glass.png"
        )
    }

    /**
     * The Home screen's mockup sequence, composed out of the same components HomeScreen
     * uses, on the same canvas MainScreen paints.
     *
     * `HomeScreen` itself takes a `ZadViewModel` and can't be captured here, but every
     * piece of the layout being aligned to "ZAD App.dc.html" is stateless — so this
     * renders the actual arrangement (canvas → hero → days/safe-spend → 6-icon grid →
     * insight glass row → 2×2 stat grid → chef card → Amazon rail) rather than checking
     * it by reading the code.
     */
    // Phone width on purpose, overriding the class-level w1080dp: the six-column
    // shortcut grid and the 2×2 stat tiles are the two things most likely to break on a
    // real handset, and at 1080dp they always fit. 402×874 is the mockup's own iOS frame.
    @Config(sdk = [33], qualifiers = "w402dp-h874dp-xxhdpi")
    @Test
    fun captureHomeMockupSequence() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZadCanvasBackground(modifier = Modifier.fillMaxSize())
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        ZadCardHero(
                            budget = 9000.0,
                            spent = 1120.0,
                            remaining = 3880.0,
                            daysLeft = 9,
                            onDepositClick = {},
                            available = Figure(value = 3240.0, confident = false),
                            committed = 640.0,
                            nextObligationText = "إيجار بعد 4 أيام"
                        )

                        ZadDaysAndSafeSpendRow(daysLeft = 9, available = 3240.0)

                        ZadPageShortcutsGrid(
                            items = listOf(
                                ZadShortcutItem(Icons.Default.Inventory2, "المخزون", primary) {},
                                ZadShortcutItem(Icons.Default.ShoppingCart, "التسوق", catDailyIcon) {},
                                ZadShortcutItem(Icons.Default.FamilyRestroom, "العائلة", kidsPrimary) {},
                                ZadShortcutItem(Icons.Default.Subscriptions, "الاشتراكات", tertiary) {},
                                ZadShortcutItem(Icons.Default.LocalPharmacy, "الصيدلية", dangerColor) {},
                                ZadShortcutItem(Icons.Default.Park, "التسبيح", secondaryDark) {},
                            )
                        )

                        // The insight row exactly as HomeScreen builds it: glass card,
                        // priority dot, title/body stack.
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White.copy(alpha = 0.85f),
                            contentPadding = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dangerColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("الحليب هيخلص بكرة", style = Typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                                    Text("باقي يوم واحد على المعدل الحالي", style = Typography.bodyMedium, color = onSurfaceVariant)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ZadStatTile(modifier = Modifier.weight(1f), label = "قوة الإنفاق", value = "82%")
                            ZadStatTile(modifier = Modifier.weight(1f), label = "الصحة المالية", value = "74/100")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ZadStatTile(modifier = Modifier.weight(1f), label = "الإنفاق الشهري", value = "4,120 ر.س")
                            ZadStatTile(modifier = Modifier.weight(1f), label = "اتجاه 7 أيام", value = "↓ 6%")
                        }

                        ZadChefCard(
                            suggestion = "اقتراح اليوم: طبق سريع بمكونات مخزونك الحالي.",
                            onClick = {}
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                listOf(
                                    "قدر ضغط كهربائي" to 189.0,
                                    "فيتامينات عائلية" to 85.0,
                                    "منظم مخزون مطبخ" to 49.0,
                                )
                            ) { (name, price) ->
                                ZadAmazonDealCard(
                                    product = com.example.data.AffiliateProduct(
                                        productNameAr = name,
                                        averagePriceSar = price
                                    ),
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/home_mockup_sequence.png"
        )
    }
}
