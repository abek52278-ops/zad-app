package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ZadInsight
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
}
