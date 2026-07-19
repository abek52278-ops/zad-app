package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.theme.AppTheme
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
}
