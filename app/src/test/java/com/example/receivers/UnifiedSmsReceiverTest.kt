package com.example.receivers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * اختبار وحدة نقي لتجميع أجزاء SMS الطويلة — بدون Robolectric لأن concatenateMultipartSms
 * مالهاش أي علاقة بـ android.telephony.SmsMessage (مصمّمة كده عمداً عشان تتختبر بسهولة).
 */
class UnifiedSmsReceiverTest {

    @Test
    fun `concatenates two parts from the same sender in order`() {
        val parts = listOf(
            "CIB" to "تم خصم مبلغ 250.00 ",
            "CIB" to "جنيه من حسابك لدى كارفور مصر الجديدة"
        )
        val result = concatenateMultipartSms(parts)
        assertEquals(1, result.size)
        assertEquals("تم خصم مبلغ 250.00 جنيه من حسابك لدى كارفور مصر الجديدة", result["CIB"])
    }

    @Test
    fun `single-part message passes through unchanged`() {
        val parts = listOf("alrajhi" to "تم خصم مبلغ 50 ريال من حسابك")
        val result = concatenateMultipartSms(parts)
        assertEquals("تم خصم مبلغ 50 ريال من حسابك", result["alrajhi"])
    }

    @Test
    fun `two different senders in the same broadcast stay separate`() {
        val parts = listOf(
            "CIB" to "part A1 ",
            "alrajhi" to "part B1",
            "CIB" to "part A2"
        )
        val result = concatenateMultipartSms(parts)
        assertEquals(2, result.size)
        assertEquals("part A1 part A2", result["CIB"])
        assertEquals("part B1", result["alrajhi"])
    }

    @Test
    fun `blank sender is dropped`() {
        val parts = listOf("" to "orphan text", "CIB" to "real text")
        val result = concatenateMultipartSms(parts)
        assertEquals(1, result.size)
        assertEquals("real text", result["CIB"])
    }

    @Test
    fun `blank concatenated result is dropped`() {
        val parts = listOf("CIB" to "", "CIB" to "")
        val result = concatenateMultipartSms(parts)
        assertEquals(0, result.size)
    }
}
