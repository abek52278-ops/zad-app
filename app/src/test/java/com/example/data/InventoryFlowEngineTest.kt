package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تغطية لفلتر [InventoryFlowEngine.looksLikeNonProductName] — الدفاع اللي بيشيل صفوف
 * مخزون قديمة اسمها لقب/وظيفة (زي "مصمم كوسموس") بدل منتج حقيقي، بعد ما اتلقت مباشرة
 * من رد شات AI غير مُتحقق منه قبل الفيكس.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InventoryFlowEngineTest {

    @Test
    fun `flags a name containing a job-title word`() {
        assertTrue(InventoryFlowEngine.looksLikeNonProductName("مصمم كوسموس"))
        assertTrue(InventoryFlowEngine.looksLikeNonProductName("دكتور أحمد"))
        assertTrue(InventoryFlowEngine.looksLikeNonProductName("مهندسة سارة"))
    }

    @Test
    fun `does not flag real grocery product names`() {
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("بيض"))
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("حليب المراعي"))
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("زيت زيتون"))
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("كريم شعر"))
    }

    @Test
    fun `does not flag a plain person name with no job-title word (known filter limitation)`() {
        // فلتر عالي الدقة بيمسك ألقاب وظيفية بس، مش أي اسم شخص — عشان يتجنب حذف
        // منتجات حقيقية غلط. أسماء زي دي المفروض تتمنع أصلاً بتأكيد الشات (Commit 1)
        // مش بالفلتر الدفاعي ده.
        assertFalse(InventoryFlowEngine.looksLikeNonProductName("جمال كامل"))
    }

    @Test
    fun `namesMatch still treats Arabic definite article and variants as equal`() {
        assertTrue(InventoryFlowEngine.namesMatch("الحليب", "حليب"))
        assertTrue(InventoryFlowEngine.namesMatch("حليب المراعي", "المراعي حليب"))
    }
}
