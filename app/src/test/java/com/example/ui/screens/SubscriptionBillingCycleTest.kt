package com.example.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ZadSubscription
import com.example.ui.theme.AppTheme
import com.example.workers.nextRenewalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * تغطية بند P2 — توحيد فورم الاشتراكات/الأقساط.
 *
 * العطل اللي التستات دي بتقفله: `billing_cycle` ماكانش ليه ولا كاتب واحد في التطبيق
 * (`grep "billingCycle ="` = صفر نتيجة)، فكل اشتراك كان بيفضل MONTHLY — قيمة الموديل
 * الافتراضية — للأبد. `nextRenewalDate` كان بيدعم YEARLY/WEEKLY من الأساس، والفورم بس
 * هو اللي مكانش يقدر ينتجهم. فاشتراك سنوي كان بيتخصم ١٢ مرة في السنة وبيتعرض بتكلفة
 * سنوية ١٢×.
 *
 * التستات في وضع **التعديل** بقصد: في وضع الإضافة الفورم بينادي `classifyBill`
 * (طلب AI بعد debounce) وده بيجرّ تهيئة Supabase جوه Robolectric. وضع التعديل بيعمل
 * `return@LaunchedEffect` قبلها، فالتست بيقيس الفورم نفسه مش الشبكة.
 */
@RunWith(AndroidJUnit4::class)
// اللغة مثبّتة بقصد: Robolectric بيشتغل بـen-US افتراضياً و`values-en` موجود، فالنصوص
// بتترجم للإنجليزي. من غير التثبيت ده التست بيدوّر على نص عربي مش موجود في الشجرة.
@Config(instrumentedPackages = ["androidx.loader.content"], qualifiers = "en")
class SubscriptionBillingCycleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val monthlySub = ZadSubscription(
        id = "sub-1",
        title = "نتفلكس",
        amount = 45.0,
        renewalDate = "2026-10-05",
        provider = "Netflix",
        category = "اشتراك",
        billingCycle = "MONTHLY"
    )

    @Test
    fun `choosing yearly reaches onSave instead of the model default`() {
        var savedCycle: String? = null
        composeTestRule.setContent {
            AppTheme {
                AddEditSubscriptionDialog(
                    subscription = monthlySub,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, billingCycle, _ -> savedCycle = billingCycle }
                )
            }
        }

        // شرايح الدورة تحت حقل تاريخ التجديد جوه Column بـverticalScroll — من غير
        // scroll الضغطة بتتبعت لنقطة بره الشاشة فماتوصلش للشريحة.
        composeTestRule.onNodeWithText("Yearly").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals("YEARLY", savedCycle)
    }

    @Test
    fun `existing cycle is prefilled so an edit does not silently reset it`() {
        var savedCycle: String? = null
        composeTestRule.setContent {
            AppTheme {
                AddEditSubscriptionDialog(
                    subscription = monthlySub.copy(billingCycle = "WEEKLY"),
                    onDismiss = {},
                    onSave = { _, _, _, _, _, billingCycle, _ -> savedCycle = billingCycle }
                )
            }
        }

        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals("WEEKLY", savedCycle)
    }

    @Test
    fun `save is disabled on a blank title instead of failing silently`() {
        composeTestRule.setContent {
            AppTheme {
                AddEditSubscriptionDialog(
                    subscription = monthlySub,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Save").assertIsEnabled()
        composeTestRule.onNodeWithText("نتفلكس").performTextClearance()
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `save is disabled on an unparseable renewal date`() {
        composeTestRule.setContent {
            AppTheme {
                AddEditSubscriptionDialog(
                    subscription = monthlySub,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _ -> }
                )
            }
        }

        // الـworker بيعمل LocalDate.parse جوه try/catch وبيتخطى الاشتراك لو فشل — من غير
        // خصم ولا ترحيل ولا أي إشارة للعميل. الفورم كان بيقبل أي نص هنا.
        composeTestRule.onNodeWithText("2026-10-05").performTextReplacement("خمستاشر يناير")
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `default renewal date follows the chosen cycle, not a hardcoded month`() {
        val today = LocalDate.of(2026, 9, 7)
        assertEquals(LocalDate.of(2027, 9, 7), nextRenewalDate(today, "YEARLY"))
        assertEquals(LocalDate.of(2026, 9, 14), nextRenewalDate(today, "WEEKLY"))
        assertEquals(LocalDate.of(2026, 10, 7), nextRenewalDate(today, "MONTHLY"))
    }
}
