package com.example.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Figure
import com.example.data.ZadInsight
import com.example.ui.components.ZadCardHero
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.theme.AppTheme
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
                androidx.compose.foundation.layout.Box(modifier = Modifier.padding(16.dp)) {
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
}
