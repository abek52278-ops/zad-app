package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.viewmodels.ZadViewModel
import com.example.ui.viewmodels.FamilyViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @org.junit.Ignore("Supabase initialization causes ExceptionInInitializerError in Robolectric")
    @Test
    fun homeScreen_displaysMainElements() {
        // Given
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ZadViewModel(context)
        val familyViewModel = FamilyViewModel()

        // When
        composeTestRule.setContent {
            HomeScreen(
                viewModel = viewModel,
                familyViewModel = familyViewModel,
                onNavigateToAssistant = {},
                onOpenDrawer = {}
            )
        }

        // Then
        // Check if "ZAD / زاد" logo title is present
        composeTestRule.onNodeWithText("ZAD / زاد").assertIsDisplayed()
        
        // Check if "تحليل المصروفات" (Expense Analysis) is present
        composeTestRule.onNodeWithText("تحليل المصروفات").assertIsDisplayed()
    }
}
