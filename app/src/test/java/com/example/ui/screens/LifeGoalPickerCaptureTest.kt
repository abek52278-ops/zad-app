package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.LifeGoalPreset
import com.example.ui.components.LifeGoalPickerContent
import com.example.ui.theme.AppTheme
import com.example.ui.theme.surface
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * لقطة محتوى شيت «أول هدف» في الوضعين — الشيت نفسه (ModalBottomSheet) مابيترسمش مستقر في
 * Robolectric، فبنصوّر المحتوى على نفس سطح الشيت. مختار «أوفّر مبلغ» بمبلغ مكتوب عشان حقل
 * المبلغ والزرار المفعّل يبانوا.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class LifeGoalPickerCaptureTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureLifeGoalPicker() {
        composeTestRule.setContent {
            AppTheme(darkTheme = dark) {
                Box(Modifier.background(surface)) {
                    LifeGoalPickerContent(
                        saving = false,
                        errorCode = null,
                        onSubmit = {},
                        initialPreset = LifeGoalPreset.SAVE_MONTHLY,
                        initialAmount = "500",
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/life_goal_picker${if (dark) "_dark" else ""}.png"
        )
    }
}
