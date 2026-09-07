package com.example.data

import com.example.ui.components.ZadCategoryType
import com.example.ui.components.categoryDisplayOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * منتقي الأقسام السريع — صف أفقي، والمساحة الظاهرة منه محدودة.
 *
 * الكروت عرضها 100dp وبينها 12dp، يعني على شاشة ~400dp بيبان منها تلاتة كاملة.
 * TASBIHA كانت رقم ١١ من ١١ (~820dp ورا سحب أفقي بلا أي إشارة) — ودي اللي
 * اتسجلت في اختبار الجهاز كـ«زر التسبيح مختفي».
 */
class CategoryDisplayOrderTest {

    @Test
    fun `every category appears exactly once`() {
        // لو حد ضاف فئة جديدة للـenum، لازم تفضل ظاهرة في الصف مش تتنسي.
        assertEquals(ZadCategoryType.entries.size, categoryDisplayOrder.size)
        assertEquals(ZadCategoryType.entries.toSet(), categoryDisplayOrder.toSet())
        assertEquals("مفيش فئة تتكرر", categoryDisplayOrder.size, categoryDisplayOrder.distinct().size)
    }

    @Test
    fun `tasbiha is reachable without scrolling`() {
        // تلات كروت كاملة بتبان على شاشة ~400dp، فالفهرس لازم يكون أقل من 3.
        val index = categoryDisplayOrder.indexOf(ZadCategoryType.TASBIHA)
        assertTrue("التسبيح في الموضع $index — ورا السحب الأفقي تاني", index in 0..2)
    }

    @Test
    fun `categories with their own screen come before the ones that all open shopping`() {
        // ستة تصنيفات بيروحوا نفس شاشة التسوق؛ ماينفعش ياخدوا كل المساحة الظاهرة.
        val ownScreen = setOf(
            ZadCategoryType.PHARMACY, ZadCategoryType.FAMILY, ZadCategoryType.TASBIHA,
            ZadCategoryType.SUBSCRIPTIONS, ZadCategoryType.MAINTENANCE,
        )
        val lastOwnScreen = categoryDisplayOrder.indexOfLast { it in ownScreen }
        val firstShared = categoryDisplayOrder.indexOfFirst { it !in ownScreen }
        assertTrue("أقسام التطبيق لازم تسبق تصنيفات المقاضي", lastOwnScreen < firstShared)
    }
}
