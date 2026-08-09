package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تغطية للفيكس: قراءة أوامر الشات كانت `Regex.find` — أول [[ACTION]] بس. المستخدم كتب
 * مشترياته الأسبوعية في رسالة واحدة ("فراخ ولحمة وطماطم ومكرونة")، الموديل كتب أربع
 * أوامر، والتطبيق نفّذ واحدة وشال التلاتة الباقيين من النص من غير تنفيذ — فالرد كان
 * بيقرا كإن كله اتسجّل والمخزون فيه صنف واحد. دلوقتي `findAll`.
 *
 * Robolectric لأن [ChatActionParser] بيستخدم `org.json.JSONObject`، واللي في android.jar
 * بتاع اختبارات الـ JVM مجرد stub بيرمي استثناء عند أي استدعاء — من غير الـ runner ده كل
 * عملية قراءة كانت هترجع صفر أوامر والاختبار يعدي على سبب غلط.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatActionParserTest {

    @Test
    fun `no action tag returns the response untouched`() {
        val raw = "عندك ٣ علب لبن في المخزون."
        val result = ChatActionParser.parse(raw)

        assertEquals(raw, result.cleanText)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `all four grocery items in one message are parsed, not just the first`() {
        val raw = """
            تمام، دي مشتريات الأسبوع.
            [[ACTION:{"type":"add","item":"فراخ","amount":2,"unit":"كيلو","category":"بقالة"}]]
            [[ACTION:{"type":"add","item":"لحمة","amount":1,"unit":"كيلو","category":"بقالة"}]]
            [[ACTION:{"type":"add","item":"طماطم","amount":3,"unit":"كيلو","category":"خضار"}]]
            [[ACTION:{"type":"add","item":"مكرونة","amount":4,"unit":"باكو","category":"بقالة"}]]
        """.trimIndent()

        val result = ChatActionParser.parse(raw)

        assertEquals(4, result.actions.size)
        assertEquals(listOf("فراخ", "لحمة", "طماطم", "مكرونة"), result.actions.map { it.itemName })
        assertEquals(listOf(2, 1, 3, 4), result.actions.map { it.amount })
        assertEquals("كيلو", result.actions[0].unit)
        assertEquals("بقالة", result.actions[0].category)
    }

    @Test
    fun `every action tag is stripped from the visible reply`() {
        val raw = """
            ضفتهم في قايمة الانتظار.
            [[ACTION:{"type":"add","item":"فراخ","amount":2}]]
            [[ACTION:{"type":"add","item":"لحمة","amount":1}]]
        """.trimIndent()

        val result = ChatActionParser.parse(raw)

        assertEquals("ضفتهم في قايمة الانتظار.", result.cleanText)
        assertTrue("مفيش وسم ACTION يوصل للمستخدم", !result.cleanText.contains("ACTION"))
    }

    @Test
    fun `mixed action types in one reply all survive`() {
        val raw = """
            [[ACTION:{"type":"consume","item":"لبن","amount":1}]]
            [[ACTION:{"type":"add","item":"عيش","amount":2}]]
            [[ACTION:{"type":"pharmacy_dose","item":"كونكور"}]]
        """.trimIndent()

        val result = ChatActionParser.parse(raw)

        assertEquals(
            listOf(
                ChatActionParser.Type.CONSUME,
                ChatActionParser.Type.ADD,
                ChatActionParser.Type.PHARMACY_DOSE
            ),
            result.actions.map { it.type }
        )
    }

    @Test
    fun `one malformed action is skipped without losing the others`() {
        val raw = """
            [[ACTION:{"type":"add","item":"فراخ","amount":2}]]
            [[ACTION:{"type":"add","item":"","amount":1}]]
            [[ACTION:{"type":"nonsense","item":"لحمة"}]]
            [[ACTION:{"type":"add","item":"طماطم","amount":3}]]
        """.trimIndent()

        val result = ChatActionParser.parse(raw)

        assertEquals(listOf("فراخ", "طماطم"), result.actions.map { it.itemName })
    }

    @Test
    fun `amount is clamped to a sane range`() {
        val raw = """
            [[ACTION:{"type":"add","item":"أرز","amount":99999}]]
            [[ACTION:{"type":"add","item":"سكر","amount":-5}]]
            [[ACTION:{"type":"add","item":"شاي"}]]
        """.trimIndent()

        val result = ChatActionParser.parse(raw)

        assertEquals(999, result.actions[0].amount)
        assertEquals(1, result.actions[1].amount)
        assertEquals("الافتراضي واحد لو مفيش كمية", 1, result.actions[2].amount)
    }

    @Test
    fun `pharmacy schedule fields are carried through`() {
        val raw = """[[ACTION:{"type":"add_pharmacy","item":"كونكور","dosage":"قرص كل 8 ساعات","daily_dose_count":3,"dose_times":"08:00,16:00,00:00","unit":"قرص","amount":20,"category":"مزمن"}]]"""

        val action = ChatActionParser.parse(raw).actions.single()

        assertEquals(ChatActionParser.Type.ADD_PHARMACY, action.type)
        assertEquals("كونكور", action.itemName)
        assertEquals("قرص كل 8 ساعات", action.dosage)
        assertEquals(3, action.dailyDoseCount)
        assertEquals("08:00,16:00,00:00", action.doseTimes)
        assertEquals("مزمن", action.category)
        assertEquals(20, action.amount)
    }

    @Test
    fun `daily dose count is clamped to twelve`() {
        val raw = """[[ACTION:{"type":"add_pharmacy","item":"دوا","daily_dose_count":50}]]"""

        assertEquals(12, ChatActionParser.parse(raw).actions.single().dailyDoseCount)
    }

    @Test
    fun `blank optional fields become null rather than empty strings`() {
        val raw = """[[ACTION:{"type":"add","item":"خبز","amount":1,"unit":"  ","category":""}]]"""

        val action = ChatActionParser.parse(raw).actions.single()

        assertEquals(null, action.unit)
        assertEquals(null, action.category)
    }
}
