package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A cache that appears to work but never hits is worse than no cache: it costs the same
 * four round-trips per Home-screen open while looking solved. These assertions are about
 * the two things that decide that — that identical inputs actually produce a hit, and that
 * changed inputs actually produce a miss.
 */
@RunWith(RobolectricTestRunner::class)
class AiLocalCacheTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val user = "user-1"

    @After
    fun tearDown() = AiLocalCache.clear(ctx)

    private val payload = mapOf("budget" to 10000.0, "tx" to "قهوة:50")

    @Test
    fun `same inputs hit`() {
        AiLocalCache.put(ctx, user, "agent_summary", payload, mapOf("text" to "ملخصك جاهز", "score" to 87))
        val hit = AiLocalCache.get(ctx, user, "agent_summary", payload)
        assertNotNull("نفس المدخلات المفروض ترجع من الكاش", hit)
        assertEquals("ملخصك جاهز", hit!!["text"])
        assertEquals(87, hit["score"])
    }

    @Test
    fun `a changed payload misses — this is the invalidation`() {
        AiLocalCache.put(ctx, user, "agent_summary", payload, mapOf("text" to "قديم"))
        // معاملة جديدة اتسجلت → الـ payload اتغير → مفتاح جديد → نداء حقيقي.
        // ده هو الإبطال كله؛ مفيش timer بيقرر إن الرقم بقى قديم.
        val afterNewTransaction = payload + ("tx" to "قهوة:50,بنزين:300")
        assertNull(AiLocalCache.get(ctx, user, "agent_summary", afterNewTransaction))
    }

    @Test
    fun `another user never reads the first user's summary`() {
        AiLocalCache.put(ctx, user, "agent_summary", payload, mapOf("text" to "فلوس حد تاني"))
        assertNull(AiLocalCache.get(ctx, "user-2", "agent_summary", payload))
    }

    @Test
    fun `key order in the payload map does not change the fingerprint`() {
        // Map iteration order isn't a contract; if the fingerprint depended on it the cache
        // would miss at random and nobody would be able to tell why.
        AiLocalCache.put(ctx, user, "auto_suggest", linkedMapOf("a" to 1, "b" to 2), mapOf("ok" to true))
        val hit = AiLocalCache.get(ctx, user, "auto_suggest", linkedMapOf("b" to 2, "a" to 1))
        assertNotNull(hit)
        assertEquals(true, hit!!["ok"])
    }

    @Test
    fun `actions outside the home set are never cached`() {
        // Scans, chat and search must always hit the network — caching a receipt scan would
        // return the previous receipt for a new photo.
        AiLocalCache.put(ctx, user, "analyze_receipt", payload, mapOf("total" to 295.25))
        assertNull(AiLocalCache.get(ctx, user, "analyze_receipt", payload))
    }

    @Test
    fun `an empty response is not stored`() {
        // callAction returns emptyMap() on failure. Storing that would serve a transient
        // network error back for the next six hours.
        AiLocalCache.put(ctx, user, "agent_summary", payload, emptyMap())
        assertNull(AiLocalCache.get(ctx, user, "agent_summary", payload))
    }

    @Test
    fun `nested structures survive the round trip`() {
        val response = mapOf(
            "suggestions" to listOf(
                mapOf("title" to "وفّر في البقالة", "amount" to 120.5),
                mapOf("title" to "راجع الاشتراكات", "amount" to 60),
            ),
            "confident" to false,
        )
        AiLocalCache.put(ctx, user, "auto_suggest", payload, response)
        val hit = AiLocalCache.get(ctx, user, "auto_suggest", payload)
        assertNotNull(hit)
        @Suppress("UNCHECKED_CAST")
        val out = hit!!["suggestions"] as List<Map<String, Any?>>
        assertEquals(2, out.size)
        assertEquals("وفّر في البقالة", out[0]["title"])
        assertEquals(120.5, out[0]["amount"])
        assertEquals(false, hit["confident"])
    }

    @Test
    fun `clear wipes everything`() {
        AiLocalCache.put(ctx, user, "agent_summary", payload, mapOf("text" to "x"))
        AiLocalCache.clear(ctx)
        assertNull(AiLocalCache.get(ctx, user, "agent_summary", payload))
    }
}
