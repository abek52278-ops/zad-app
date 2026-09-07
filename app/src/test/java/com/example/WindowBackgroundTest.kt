package com.example

import android.app.Application
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * تغطية P0-4 — «شاشة سودا عند اختيار البلد».
 *
 * السبب الجذري كان **بره Compose خالص**، وده اللي خلّى الإصلاح اللي قبله
 * (`36429d2`، غطاء `resolvingMarket`) مايلمسش العطل: الغطاء ده جوه شجرة Compose
 * اللي بتتهدّ أصلاً، وعلى مسار اختيار البلد `resolvingMarket` عمره ما بيتحط true
 * (لأن `setMarket` بيسبق `onContinue`، فـ`hasSelectedMarket` بتبقى true والبلوك
 * كله بيتخطى).
 *
 * اللي بيتعرض في الفجوة دي هو `android:windowBackground`. الثيم وارث من
 * `Theme.DeviceDefault` وهي النسخة **الداكنة** (مش `.Light`)، والقياس وقت التشخيص
 * طلّع `#FF1A1B20` — أسود مايل للرمادي — **في الوضعين**، تحت واجهة كلها بالتة
 * فاتحة `#F8F9FA`. والفجوة بتطول لأن `recreate()` (اللي ٦ شاشات بتناديه لتطبيق
 * اللغة) بيعيد ١٨٠ سطر شغل متزامن في `onCreate` قبل `setContent`.
 *
 * التست ده بيقفل القيمة على لون كانفاس التطبيق في الوضعين. لو اتكسر، يبقى إما
 * الثيم اتغيّر أو البالتة اتغيّرت من غير ما خلفية النافذة تتحدّث معاها — وده
 * بيرجّع الفلاش، وهو عطل **مابيبانش في أي تست تاني ولا في اللينت**.
 */
@RunWith(AndroidJUnit4::class)
class WindowBackgroundTest {

    private fun windowBackgroundColor(): Int {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val theme = ctx.resources.newTheme()
        theme.applyStyle(R.style.Theme_MyApplication, true)
        val tv = TypedValue()
        assertTrue(
            "android:windowBackground لم يُحل من الثيم إطلاقاً",
            theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
        )
        assertTrue(
            "windowBackground لازم يكون لون صريح، مش drawable — القيمة: type=${tv.type}",
            tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT
        )
        return tv.data
    }

    /** لازم تساوي ZadIosBackground في ui/theme/Color.kt */
    @Test
    @Config(qualifiers = "notnight")
    fun `light window background matches the app canvas, not the framework near-black`() {
        assertEquals("#FFF8F9FA", "#%08X".format(windowBackgroundColor()))
    }

    /** لازم تساوي ZadIosBackgroundDark في ui/theme/Color.kt */
    @Test
    @Config(qualifiers = "night")
    fun `dark window background follows the app dark canvas`() {
        assertEquals("#FF10130F", "#%08X".format(windowBackgroundColor()))
    }

    /**
     * `values-v29` بيعيد تعريف الستايل بالكامل عشان `forceDarkAllowed`، فلو حد ضاف
     * الحقل هناك ونسي خلفية النافذة، كل جهاز API 29+ يرجع لأسود الإطار وهو بالظبط
     * العطل الأصلي — من غير ما أي تست تاني يلاحظ.
     */
    @Test
    @Config(qualifiers = "notnight", sdk = [29])
    fun `api 29+ keeps the window background it would otherwise lose`() {
        assertEquals("#FFF8F9FA", "#%08X".format(windowBackgroundColor()))
    }

    @Test
    @Config(qualifiers = "night", sdk = [29])
    fun `api 29+ dark keeps both the dark canvas and force-dark opt-out`() {
        assertEquals("#FF10130F", "#%08X".format(windowBackgroundColor()))
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val theme = ctx.resources.newTheme()
        theme.applyStyle(R.style.Theme_MyApplication, true)
        val tv = TypedValue()
        assertTrue(
            "forceDarkAllowed اختفى — مؤهّل الليل كسب على values-v29",
            theme.resolveAttribute(android.R.attr.forceDarkAllowed, tv, true)
        )
        assertEquals("forceDarkAllowed لازم يفضل false", 0, tv.data)
    }
}
