package com.example.workers

import com.example.data.ZadSubscription
import com.example.data.ZadTransaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * تغطية للفيكس: قبل كده الـ worker كان بيسجل خصم الاشتراك تلقائي من غير أي فحص — لو البنك
 * بعت SMS/إشعار بنفس الخصم ده (السيناريو العادي فعلاً لاشتراك auto_deduct حقيقي)، العميل
 * كان بيتخصم منه مرتين في التطبيق لنفس الفلوس الحقيقية.
 */
class SubscriptionAutoDeductWorkerTest {

    private val renewal = LocalDate.of(2026, 7, 5)
    private val sub = ZadSubscription(title = "نتفلكس", amount = 45.0, provider = "Netflix", renewalDate = "2026-07-05")

    private fun tx(amount: Double, title: String, day: String, merchant: String? = null) =
        ZadTransaction(amount = amount, title = title, isExpense = true, createdAt = "${day}T09:00:00Z", merchantName = merchant)

    @Test
    fun `bank-reported transaction with matching title same month is already charged`() {
        val bankTx = tx(45.0, "مصرف الراجحي: نتفلكس", "2026-07-06")
        assertTrue(isSubscriptionAlreadyCharged(bankTx, sub, renewal))
    }

    @Test
    fun `matching provider as merchantName is already charged even with unrelated title`() {
        val bankTx = tx(45.0, "شراء", "2026-07-10", merchant = "Netflix")
        assertTrue(isSubscriptionAlreadyCharged(bankTx, sub, renewal))
    }

    @Test
    fun `different subscription in the same month is not a match`() {
        val bankTx = tx(45.0, "سبوتيفاي", "2026-07-06")
        assertFalse(isSubscriptionAlreadyCharged(bankTx, sub, renewal))
    }

    @Test
    fun `same subscription but different month is not a match (legitimate next cycle)`() {
        val bankTx = tx(45.0, "نتفلكس", "2026-06-05")
        assertFalse(isSubscriptionAlreadyCharged(bankTx, sub, renewal))
    }

    @Test
    fun `same title same month but different amount is not a match`() {
        val bankTx = tx(99.0, "نتفلكس", "2026-07-06")
        assertFalse(isSubscriptionAlreadyCharged(bankTx, sub, renewal))
    }

    @Test
    fun `amount within 0-005 tolerance still matches`() {
        val bankTx = tx(44.996, "نتفلكس", "2026-07-06")
        assertTrue(isSubscriptionAlreadyCharged(bankTx, sub, renewal))
    }
}
