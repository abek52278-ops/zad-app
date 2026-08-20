package com.example.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheReconciliationTest {
    @Test
    fun `successful empty snapshot clears stale cache`() {
        assertEquals(
            CacheReconciliationAction.CLEAR,
            cacheReconciliationAction(authoritative = true, itemCount = 0, outboxDrained = true)
        )
    }

    @Test
    fun `failed empty snapshot never clears cache`() {
        assertEquals(
            CacheReconciliationAction.SKIP,
            cacheReconciliationAction(authoritative = false, itemCount = 0, outboxDrained = true)
        )
    }

    @Test
    fun `pending offline writes prevent reconciliation`() {
        assertEquals(
            CacheReconciliationAction.SKIP,
            cacheReconciliationAction(authoritative = true, itemCount = 3, outboxDrained = false)
        )
    }

    @Test
    fun `complete non-empty snapshot prunes missing rows`() {
        assertEquals(
            CacheReconciliationAction.PRUNE,
            cacheReconciliationAction(authoritative = true, itemCount = 999, outboxDrained = true)
        )
    }

    @Test
    fun `possibly truncated server page never prunes cache`() {
        assertEquals(
            CacheReconciliationAction.SKIP,
            cacheReconciliationAction(authoritative = true, itemCount = 1000, outboxDrained = true)
        )
    }
}
