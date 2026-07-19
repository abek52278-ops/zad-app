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

object ZadAiGeminiClient {
    private const val TAG = "ZadAiGemini"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)  // prevent hang on large image uploads
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val maxWidth = 800
        val maxHeight = 800
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

    suspend fun analyzeInventoryImage(apiKey: String, bitmap: Bitmap): AiInventoryScanResult? = withContext(Dispatchers.IO) {
        try {
            val base64 = encodeBitmap(bitmap)
            val byteSize = base64.length * 3 / 4
            Log.d(TAG, "analyzeInventoryImage: image base64 size = $byteSize bytes")
            val prompt = """
                You are an inventory tracking AI for a Saudi budget app called ZAD.
                Look at this image carefully and identify EVERY visible product, food item, or branded item.
                Even if the image shows a single bottle, can, box, or bag — list it.
                Output ONLY a valid JSON object (no markdown, no backticks, no explanation) with this EXACT structure:
                {
                  "items": [
                    { "name": "product name in Arabic or English", "quantity": 1.0, "unit": "قطعة", "category": "estimated category" }
                  ]
                }
                If you see ANY product at all, include it. Do not return empty items array if there are visible products.
            """.trimIndent()
            
            val payload = buildJsonObject {
                put("model", "meta-llama/llama-4-scout-17b-16e-instruct")
                put("response_format", buildJsonObject { put("type", "json_object") })
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:image/jpeg;base64,$base64")
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Groq request failed: ${response.code} - ${response.body?.string()}")
                return@withContext null
            }

            val responseStr = response.body?.string() ?: return@withContext null
            val responseJson = json.parseToJsonElement(responseStr).jsonObject
            val text = responseJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            
            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            json.decodeFromString<AiInventoryScanResult>(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeInventoryImage failed: ${e.message}")
            null
        }
    }

    suspend fun analyzeReceipt(apiKey: String, bitmap: Bitmap): AiParsedReceipt? = withContext(Dispatchers.IO) {
        try {
            val base64 = encodeBitmap(bitmap)
            val prompt = """
                You are a receipt parsing AI for a Saudi budget app called ZAD.
                Extract the data from this receipt into ONLY a JSON object (no markdown, no backticks) with this structure:
                {
                  "storeName": "Store Name in Arabic",
                  "total": 150.5,
                  "category": "grocery",
                  "items": [
                    { "name": "Item name", "quantity": 1.0, "price": 10.0, "unit": "حبة", "category": "grocery" }
                  ]
                }
            """.trimIndent()
            
            val byteSize = base64.length * 3 / 4
            Log.d(TAG, "analyzeReceipt: image base64 size = $byteSize bytes")
            val payload = buildJsonObject {
                put("model", "meta-llama/llama-4-scout-17b-16e-instruct")
                put("response_format", buildJsonObject { put("type", "json_object") })  // ensure JSON output
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", prompt)
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:image/jpeg;base64,$base64")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 2000)
                put("temperature", 0.2)
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseStr = response.body?.string() ?: return@withContext null
            Log.d(TAG, "analyzeReceipt RAW response: $responseStr")
            if (!response.isSuccessful) {
                Log.e(TAG, "Groq request failed: ${response.code}")
                return@withContext null
            }

            val responseJson = json.parseToJsonElement(responseStr).jsonObject
            val text = responseJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            Log.d(TAG, "analyzeReceipt model text: $text")
            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            json.decodeFromString<AiParsedReceipt>(cleanJson)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeReceipt failed: ${e.message}")
            null
        }
    }

    suspend fun generateText(apiKey: String, prompt: String, jsonFormat: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("model", "llama-3.3-70b-versatile")  // text model, not vision
                if (jsonFormat) {
                    put("response_format", buildJsonObject { put("type", "json_object") })
                }
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Groq Text request failed: ${response.code} - ${response.body?.string()}")
                return@withContext null
            }

            val responseStr = response.body?.string() ?: return@withContext null
            val responseJson = json.parseToJsonElement(responseStr).jsonObject
            val text = responseJson["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
            
            if (jsonFormat) {
                text.replace("```json", "").replace("```", "").trim()
            } else {
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateText failed: ${e.message}")
            null
        }
    }
}
