package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/**
 * On-device cache for the AI actions the Home screen fires on every open.
 *
 * `zad-core-intelligence` already caches these server-side on a payload fingerprint, so
 * repeat opens cost no model quota. What that cache cannot remove is the trip itself: four
 * HTTPS round-trips to an edge function every time the customer opens the app, each one a
 * billed invocation and a visible wait on a cold function. This is the half that lives on
 * the phone.
 *
 * **Keyed on a fingerprint of the request, not on a timer.** That is the same choice the
 * server made and for the same reason: a pure TTL would keep showing yesterday's summary
 * after a transaction was recorded, which is worse than spending the call. Same inputs →
 * same key → cache hit; the moment a transaction, an inventory item or the budget changes,
 * the payload changes, the key changes, and a real call happens. The TTL below is only a
 * backstop for inputs that drift without the payload changing (prices, the date rolling
 * over), never the primary invalidation.
 *
 * SharedPreferences rather than Room: these are a handful of small JSON blobs with no
 * relational shape and no queries over them, so a table and a migration would buy nothing.
 * Scoped per user id, because a shared device must not serve one account's summary to
 * another — the same rule the server's cache key follows.
 */
object AiLocalCache {

    private const val TAG = "AiLocalCache"
    private const val PREFS = "zad_ai_cache"

    /** Matches HOME_CACHED_ACTIONS in zad-core-intelligence/index.ts. Keep the two in step:
     *  an action cached on one side only is a cache that appears to work and doesn't. */
    val CACHED_ACTIONS = setOf("agent_summary", "auto_suggest", "expense_prediction", "brain_evaluate")

    /** Six hours, same as the server's AI_CACHE_TTL_MS. Deliberately identical so the two
     *  layers cannot disagree about how stale is too stale. */
    private const val TTL_MS = 6L * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fingerprint(userId: String?, action: String, payload: Map<String, Any?>): String {
        // The payload is stringified in sorted key order — a Map's iteration order is not a
        // contract, and a key order that shifts between calls would silently miss every time.
        val body = payload.entries.sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        val raw = "$action:${userId ?: "anon"}:$body"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return "$action:${userId ?: "anon"}:" + digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** Cached response, or null on a miss / expiry / anything unreadable. Never throws:
     *  a broken cache must degrade to a network call, not to a crash on the Home screen. */
    fun get(context: Context, userId: String?, action: String, payload: Map<String, Any?>): Map<String, Any?>? {
        if (action !in CACHED_ACTIONS) return null
        return try {
            val key = fingerprint(userId, action, payload)
            val p = prefs(context)
            val stored = p.getString(key, null) ?: return null
            val savedAt = p.getLong("$key.at", 0L)
            if (System.currentTimeMillis() - savedAt > TTL_MS) {
                p.edit().remove(key).remove("$key.at").apply()
                return null
            }
            Log.d(TAG, "hit action=$action")
            json.parseToJsonElement(stored).jsonObject.toAnyMap()
        } catch (e: Exception) {
            Log.w(TAG, "get($action) failed, treating as miss: ${e.message}")
            null
        }
    }

    fun put(context: Context, userId: String?, action: String, payload: Map<String, Any?>, response: Map<String, Any?>) {
        if (action !in CACHED_ACTIONS || response.isEmpty()) return
        try {
            val key = fingerprint(userId, action, payload)
            prefs(context).edit()
                .putString(key, json.encodeToString(JsonObject.serializer(), response.toJsonObject()))
                .putLong("$key.at", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "put($action) failed: ${e.message}")
        }
    }

    /**
     * Wipe everything. Called on sign-out — entries are keyed by user id so they could not
     * be *served* to the next account, but leaving one account's financial summaries on
     * disk after they log out is not something to do on a shared phone.
     */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // ─── Map <-> JsonObject, kept local ────────────────────────────────────────
    // callAction's contract is Map<String, Any?> holding whatever the edge function
    // returned, so the conversion is by runtime type. Anything unrecognised is stored as
    // its toString(): a cache that drops a field it doesn't understand would hand callers a
    // response subtly different from the live one, which is far harder to notice than a miss.

    private fun Map<String, Any?>.toJsonObject(): JsonObject =
        JsonObject(mapValues { (_, v) -> v.toJsonElement() })

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
        is Iterable<*> -> kotlinx.serialization.json.JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    private fun JsonObject.toAnyMap(): Map<String, Any?> = mapValues { (_, v) -> v.toAny() }

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> boolean
            intOrNull != null -> intOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
        is JsonObject -> mapValues { (_, v) -> v.toAny() }
        is kotlinx.serialization.json.JsonArray -> map { it.toAny() }
    }
}
