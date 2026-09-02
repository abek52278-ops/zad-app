package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Market
import com.example.data.MarketPrefs
import com.example.data.ZadPharmacyItem
import com.example.data.ZadSubscription
import com.example.ui.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * كروت الرئيسية كانت بتخترع بيانات لما الداتا الحقيقية تكون فاضية: ٣ اشتراكات
 * (نتفليكس/STC TV/الجيم) بإجمالي 294 ر.س، و٣ أدوية (باراسيتامول/فيتامين د/أنسولين)
 * ونسبة التزام 91% — كلها مكتوبة في الكود وبتتعرض لأي حساب جديد وكأنها بتاعته.
 * الاختبار ده بيقفل الباب ده: حساب فاضي لازم يقول "مفيش"، وحساب بمعاملات حقيقية
 * لازم يعرضها بعملة السوق المختار (مصر ← ج.م) مش بالريال.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1080dp-h2400dp-xxhdpi")
class GlanceCardsHonestyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptySubscriptionsShowEmptyStateNotInventedOnes() {
        composeTestRule.setContent {
            AppTheme {
                ZadSubscriptionsGlanceCard(
                    subscriptions = emptyList(),
                    onViewAllClick = {},
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
        composeTestRule.onNodeWithText("مفيش اشتراكات لسه").assertIsDisplayed()
        composeTestRule.onNodeWithText("نتفليكس").assertDoesNotExist()
        composeTestRule.onNodeWithText("STC TV").assertDoesNotExist()
        composeTestRule.onNodeWithText("اشتراك الجيم").assertDoesNotExist()
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "build/outputs/roborazzi/subs_empty.png")
    }

    @Test
    fun emptyPharmacyShowsEmptyStateAndNoInventedAdherence() {
        composeTestRule.setContent {
            AppTheme {
                ZadPharmacyGlanceCard(
                    pharmacyItems = emptyList(),
                    onViewAllClick = {},
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    adherencePercent = null
                )
            }
        }
        composeTestRule.onNodeWithText("الصيدلية فاضية").assertIsDisplayed()
        composeTestRule.onNodeWithText("مفيش أدوية مسجّلة").assertIsDisplayed()
        composeTestRule.onNodeWithText("باراسيتامول").assertDoesNotExist()
        composeTestRule.onNodeWithText("أنسولين").assertDoesNotExist()
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "build/outputs/roborazzi/pharmacy_empty.png")
    }

    @Test
    fun realSubscriptionsRenderInTheSelectedMarketCurrency() {
        MarketPrefs.setCurrentMarketForTest(Market.EGYPT)
        composeTestRule.setContent {
            AppTheme {
                Column {
                    ZadSubscriptionsGlanceCard(
                        subscriptions = listOf(
                            ZadSubscription(title = "شاهد", amount = 120.0),
                            ZadSubscription(title = "الجيم", amount = 300.0),
                        ),
                        onViewAllClick = {},
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
        }
        // العملة بتيجي من MarketPrefs مش من نص ثابت — الجنيه المصري هنا، مش الريال.
        composeTestRule.onNodeWithText("120 ج.م").assertIsDisplayed()
        composeTestRule.onNodeWithText("إجمالي شهري: 420 ج.م").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "build/outputs/roborazzi/subs_egp.png")
    }

    @Test
    fun realPharmacyItemsShowTheirOwnDosageAndRealAdherence() {
        composeTestRule.setContent {
            AppTheme {
                ZadPharmacyGlanceCard(
                    pharmacyItems = listOf(
                        ZadPharmacyItem(name = "كونكور", dosage = "قرص كل 12 ساعة"),
                    ),
                    onViewAllClick = {},
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    adherencePercent = 64
                )
            }
        }
        composeTestRule.onNodeWithText("كونكور").assertIsDisplayed()
        // الجرعة الحقيقية بتاعة الصف، مش "جرعة منتظمة" لكل دواء.
        composeTestRule.onNodeWithText("قرص كل 12 ساعة").assertIsDisplayed()
        // ٦٤٪ نسبة حقيقية منخفضة — لازم تتعرض زي ما هي، مش تتحول لـ"91% منتظم".
        composeTestRule.onNodeWithText("الالتزام بالجرعات: 64% • محتاج انتباه").assertIsDisplayed()
        composeTestRule.onRoot()
            .captureRoboImage(filePath = "build/outputs/roborazzi/pharmacy_real.png")
    }
}
