package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reading Pexels' response shape, which is the one part of the image path that can be
 * checked without a device, a network or a key.
 *
 * It is worth pinning because the shape is not the one it replaced. Unsplash answered
 * `{results:[{urls:{regular,thumb}}]}`; Pexels answers `{photos:[{src:{landscape,large,…}}]}`.
 * A silent mismatch here does not throw — it returns null, and null is indistinguishable
 * from "no photo exists for this dish", so the cards would quietly fall back to icons
 * forever and look like a content problem rather than a parsing one.
 *
 * Robolectric because [PexelsRepo] uses `org.json.JSONObject`, which is a stub in the JVM
 * android.jar and throws on every call — the same reason [ChatActionParserTest] needs it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PexelsRepoTest {

    @Test
    fun `prefers the landscape crop, which is the shape the cards render`() {
        val body = """
            {"photos":[{"src":{
              "original":"https://images.pexels.com/o.jpg",
              "large":"https://images.pexels.com/l.jpg",
              "landscape":"https://images.pexels.com/ls.jpg"
            }}]}
        """.trimIndent()
        assertEquals("https://images.pexels.com/ls.jpg", PexelsRepo.parseFirstPhotoUrl(body))
    }

    @Test
    fun `falls back through large to original when there is no landscape crop`() {
        val noLandscape = """{"photos":[{"src":{"original":"https://x/o.jpg","large":"https://x/l.jpg"}}]}"""
        assertEquals("https://x/l.jpg", PexelsRepo.parseFirstPhotoUrl(noLandscape))

        val onlyOriginal = """{"photos":[{"src":{"original":"https://x/o.jpg"}}]}"""
        assertEquals("https://x/o.jpg", PexelsRepo.parseFirstPhotoUrl(onlyOriginal))
    }

    /**
     * The common real answer for a dish Pexels has never heard of. It must read as "no
     * image" and not as a failure, because the caller caches the two differently.
     */
    @Test
    fun `an empty result set is no image rather than an error`() {
        assertNull(PexelsRepo.parseFirstPhotoUrl("""{"photos":[],"total_results":0}"""))
    }

    @Test
    fun `a malformed or error body yields null instead of throwing`() {
        assertNull(PexelsRepo.parseFirstPhotoUrl(""))
        assertNull(PexelsRepo.parseFirstPhotoUrl("not json at all"))
        assertNull(PexelsRepo.parseFirstPhotoUrl("""{"error":"Unauthorized"}"""))
        // The Unsplash shape, which is exactly what a half-finished migration would leave
        // behind. It must not accidentally parse.
        assertNull(PexelsRepo.parseFirstPhotoUrl("""{"results":[{"urls":{"regular":"https://x/r.jpg"}}]}"""))
    }

    @Test
    fun `an entry whose src carries empty strings is treated as absent`() {
        val body = """{"photos":[{"src":{"landscape":"","large":"","original":"https://x/o.jpg"}}]}"""
        assertEquals("https://x/o.jpg", PexelsRepo.parseFirstPhotoUrl(body))
    }

    /**
     * The reason the parser stopped taking `photos[0]` on faith. Pexels does not answer
     * "nothing" — it answers with the closest thing it has, so "مطبخ" came back as an
     * empty kitchen and "لبن ومية" as a lake, and the card rendered scenery as dinner.
     */
    @Test
    fun `a scenery photo is skipped in favour of a later food one`() {
        val body = """
            {"photos":[
              {"alt":"Snow covered mountain under blue sky","src":{"landscape":"https://x/mountain.jpg"}},
              {"alt":"Cooked food on a white ceramic plate","src":{"landscape":"https://x/food.jpg"}}
            ]}
        """.trimIndent()
        assertEquals("https://x/food.jpg", PexelsRepo.parseFirstPhotoUrl(body))
    }

    /**
     * Null, not the first result. The caller reads null as "use the curated food
     * placeholder", which is the honest outcome when the query itself has gone astray.
     */
    @Test
    fun `results that are all scenery yield null rather than the wrong picture`() {
        val body = """
            {"photos":[
              {"alt":"Green forest during daytime","src":{"landscape":"https://x/forest.jpg"}},
              {"alt":"Body of water near a mountain","src":{"landscape":"https://x/lake.jpg"}}
            ]}
        """.trimIndent()
        assertNull(PexelsRepo.parseFirstPhotoUrl(body))
    }

    /** Most real Pexels photos carry no alt at all — absence must not read as rejection. */
    @Test
    fun `a photo with no alt text is still accepted`() {
        val body = """{"photos":[{"src":{"landscape":"https://x/ls.jpg"}}]}"""
        assertEquals("https://x/ls.jpg", PexelsRepo.parseFirstPhotoUrl(body))
    }
}
