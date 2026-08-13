package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * BYO-personal-key vision client for [com.example.ui.screens.CameraScreen] — tried before
 * the `zad-core-intelligence` edge function, so a user whose free-tier quota is exhausted
 * can keep scanning with their own key.
 *
 * As of 2026-08-01 this genuinely talks to Google Gemini (`generateContent`), which the
 * file name has always claimed and the code stopped doing at some point: every request
 * used to go to api.groq.com. Two reasons it moved back:
 *
 *  - **Groq cannot serve this use case.** It rejects JSON mode outright on any request
 *    carrying an image, so these two scans had to ask for free-form text and then dig the
 *    JSON back out of prose. Gemini's `response_mime_type: application/json` returns
 *    structured JSON *with* the image, which is what the scanner needed all along.
 *  - **Key rotation.** One key means one 429 kills the scanner. [splitKeys] accepts several
 *    keys in the single stored string, and every call walks the whole list before failing.
 *
 * Migration note: the stored SharedPreferences value (`gemini_api_key`) may still hold a
 * **Groq** key saved by an older build. Gemini will reject it (400/401), this client
 * returns null, and [ZadAiRepository] falls through to the edge function exactly as it does
 * when no key is set — degraded, never broken. CameraScreen's dialog now asks for a Gemini
 * key so the stale value gets replaced on next use.
 */
object ZadAiGeminiClient {
    private const val TAG = "ZadAiGemini"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)  // prevent hang on large image uploads
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Vision/OCR model, in one place so the two scans can't drift apart again.
     *
     * Must stay in step with `zad-core-intelligence`'s `ZAD_MODEL_ROUTINE`: on 2026-08-01
     * `gemini-2.5-flash` started returning 404 "no longer available to new users" — it is
     * still listed by the models endpoint, it simply cannot be called — which is what
     * killed the server-side scanner. The server is configurable by secret; this constant
     * is the one place the client can be retargeted, so it is the line to change when a
     * slug is retired.
     */
    private const val VISION_MODEL = "gemini-3.5-flash"

    /** Text-only model for [generateText]. Same key, same slug caveat as [VISION_MODEL]. */
    private const val TEXT_MODEL = "gemini-3.5-flash"

    private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Cap matches the server pool (`ZAD_API_KEY_1..5`) — more keys than that is a typo, not intent. */
    private const val MAX_KEYS = 5

    /**
     * One stored string, up to five keys. Users paste keys separated by commas, spaces or
     * newlines depending on where they copied them from, so all three delimit here.
     */
    internal fun splitKeys(raw: String): List<String> =
        raw.split(',', '\n', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_KEYS)

    /**
     * Pulls the outermost `{...}` out of a model reply.
     *
     * Still needed even with `response_mime_type: application/json`: that config is honored
     * for the JSON *shape*, but a model can still prefix a stray token, and this path also
     * runs when a caller asks for free-form text. Lenient extraction costs nothing and the
     * strip-backticks-and-hope approach it replaced threw on any leading sentence.
     */
    private fun extractJsonObject(raw: String): String? {
        val stripped = raw.replace("```json", "").replace("```", "").trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        // Kept in sync with ZadAiRepository.encodeBitmap's 1024px spec (same reasoning: receipt/
        // medicine-bottle OCR needs more resolution than 800px was giving the vision model).
        val maxWidth = 1024
        val maxHeight = 1024
        val ratio = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val resizedBitmap = if (ratio < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * One `generateContent` call against one key. Returns the concatenated text parts, or
     * null plus the HTTP status so the caller can tell a quota bounce (429, worth retrying
     * on the next key) from a bad request (worth logging).
     */
    private fun callOnce(
        apiKey: String,
        model: String,
        prompt: String,
        imageBase64: String?,
        jsonMode: Boolean,
        maxTokens: Int,
    ): Pair<String?, Int> {
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", prompt) })
            if (imageBase64 != null) {
                add(buildJsonObject {
                    put("inline_data", buildJsonObject {
                        put("mime_type", "image/jpeg")
                        put("data", imageBase64)
                    })
                })
            }
        }
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", parts)
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.2)
                put("maxOutputTokens", maxTokens)
                if (jsonMode) put("response_mime_type", "application/json")
            })
        }
        // Key goes in the query string, the shape Gemini's REST API documents. It never
        // leaves the device except to Google, and it is the user's own key.
        val request = Request.Builder()
            .url("$API_BASE/$model:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Gemini request failed: ${response.code} - $body")
                    return@use null to response.code
                }
                val text = json.parseToJsonElement(body).jsonObject["candidates"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                    ?.takeIf { it.isNotBlank() }
                text to response.code
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini request threw: ${e.message}")
            null to 0
        }
    }

    /**
     * Walks every key in [rawKeys] before giving up. A 429 on key N retries the *same*
     * request on key N+1 immediately — this is quota failover within one scan, not
     * per-scan round-robin, because the user is standing there holding up a receipt.
     */
    private suspend fun generate(
        rawKeys: String,
        model: String,
        prompt: String,
        imageBase64: String? = null,
        jsonMode: Boolean = true,
        maxTokens: Int = 2000,
    ): String? = withContext(Dispatchers.IO) {
        val keys = splitKeys(rawKeys)
        if (keys.isEmpty()) return@withContext null
        for ((i, key) in keys.withIndex()) {
            val (text, status) = callOnce(key, model, prompt, imageBase64, jsonMode, maxTokens)
            if (text != null) return@withContext text
            if (status == 429) {
                Log.w(TAG, "Gemini key ${i + 1}/${keys.size} hit quota, trying next key")
            } else {
                Log.e(TAG, "Gemini key ${i + 1}/${keys.size} failed (status $status)")
            }
        }
        Log.e(TAG, "All ${keys.size} Gemini key(s) exhausted — falling back to edge function")
        null
    }

    suspend fun analyzeInventoryImage(apiKey: String, bitmap: Bitmap): AiInventoryScanResult? {
        val base64 = encodeBitmap(bitmap)
        Log.d(TAG, "analyzeInventoryImage: image base64 size = ${base64.length * 3 / 4} bytes")
        val prompt = """
            You are an inventory tracking AI for a Saudi budget app called ZAD.
            Look at this image carefully and identify EVERY visible product, food item, or branded item.
            Even if the image shows a single bottle, can, box, or bag — list it.
            `category` MUST be exactly one of these Arabic values, never anything else: البقالة، الخضار، الفواكه، اللحوم، الألبان، المشروبات، العناية، أخرى.
            Milk, cheese, yogurt, laban -> الألبان. Fresh vegetables -> الخضار. Fresh fruit -> الفواكه.
            Raw/frozen meat, chicken, fish -> اللحوم. Juice, soda, water -> المشروبات.
            Soap, shampoo, cleaning supplies -> العناية. Packaged/canned/dry goods -> البقالة.
            Output ONLY a valid JSON object (no markdown, no backticks, no explanation) with this EXACT structure:
            {
              "items": [
                { "name": "product name in Arabic or English", "quantity": 1.0, "unit": "قطعة", "category": "الألبان" }
              ]
            }
            Only list items that are actually grocery/household products visible in the image. If the image shows
            no such products (e.g. it's a document, a person, text, or unrelated scene), return an empty items array —
            never invent or guess a product to avoid an empty list.
        """.trimIndent()

        val text = generate(apiKey, VISION_MODEL, prompt, imageBase64 = base64) ?: return null
        val cleanJson = extractJsonObject(text) ?: run {
            Log.e(TAG, "analyzeInventoryImage: no JSON object in model reply: $text")
            return null
        }
        return try {
            json.decodeFromString<AiInventoryScanResult>(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeInventoryImage decode failed: ${e.message}")
            null
        }
    }

    suspend fun analyzeReceipt(apiKey: String, bitmap: Bitmap): AiParsedReceipt? {
        val base64 = encodeBitmap(bitmap)
        Log.d(TAG, "analyzeReceipt: image base64 size = ${base64.length * 3 / 4} bytes")
        val prompt = """
            You are a receipt parsing AI for a Saudi budget app called ZAD.
            Receipts are usually in Arabic, sometimes bilingual, and amounts are in SAR.
            Read every line item with its own price and keep item names exactly as printed.
            `total` is the final amount actually paid (after VAT and any discount), a number with no currency symbol.
            If a field is genuinely unreadable, leave it empty or 0 rather than guessing.
            Also classify `receiptType`: "pharmacy" if this is a pharmacy/drugstore receipt
            (medicine names, dosages like "500mg", tablet/syrup/capsule units), "general" for
            non-grocery non-pharmacy receipts (restaurants, fuel, services), otherwise "grocery".
            Extract the data into ONLY a JSON object (no markdown, no backticks) with this structure:
            {
              "storeName": "Store Name in Arabic",
              "total": 150.5,
              "category": "grocery",
              "receiptType": "grocery",
              "items": [
                { "name": "Item name", "quantity": 1.0, "price": 10.0, "unit": "حبة", "category": "grocery" }
              ]
            }
        """.trimIndent()

        val text = generate(apiKey, VISION_MODEL, prompt, imageBase64 = base64) ?: return null
        val cleanJson = extractJsonObject(text) ?: run {
            Log.e(TAG, "analyzeReceipt: no JSON object in model reply: $text")
            return null
        }
        return try {
            json.decodeFromString<AiParsedReceipt>(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeReceipt decode failed: ${e.message}")
            null
        }
    }

    suspend fun generateText(apiKey: String, prompt: String, jsonFormat: Boolean = false): String? {
        val text = generate(
            rawKeys = apiKey,
            model = TEXT_MODEL,
            prompt = prompt,
            jsonMode = jsonFormat,
            maxTokens = 1500,
        ) ?: return null
        return if (jsonFormat) text.replace("```json", "").replace("```", "").trim() else text
    }
}
