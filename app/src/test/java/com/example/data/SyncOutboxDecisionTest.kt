package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOutboxDecisionTest {

    @Test
    fun `accepted server outcomes clear a notification retry`() {
        listOf("logged", "ignored", "ambiguous", "needs_classification", "awaiting_confirmation")
            .forEach { assertTrue(it, isAcceptedNotificationIngestStatus(it)) }
    }

    @Test
    fun `transport and server failures keep a notification retry`() {
        listOf<String?>(null, "", "rejected", "delivery_retry", "unexpected")
            .forEach { assertFalse(it, isAcceptedNotificationIngestStatus(it)) }
    }
}
