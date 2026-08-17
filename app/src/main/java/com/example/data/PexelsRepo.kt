package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "PexelsRepo"

/**
 * صور الأكل من Pexels — رابط واحد لكل مصطلح، متخزّن محلياً.
 *
 * ليه Pexels وليه اتغيّرت عن Unsplash: Unsplash كانت بترجّع `[]` على أي استعلام عربي،
 * فالوصفة كانت بتخسر صورتها بصمت لما النموذج ينسى `image_keyword_en`. Pexels بتفهم
 * العربي، فاسم الوصفة نفسه بقى استعلام شغّال — ودي مش تحسين تجميلي، دي الفرق بين كارت
 * فيه صورة وكارت فيه أيقونة.
 *
 * ### مسارين للمفتاح، وده مقصود
 *
 * لو `BuildConfig.PEXELS_API_KEY` فيه مفتاح حقيقي، التليفون بينده Pexels على طول. لو
 * فاضي أو لسه على القيمة الافتراضية بتاعة `.env.example`، بيروح على أكشن `pexels_image`
 * في `zad-core-intelligence` اللي عنده المفتاح كسر مشروع.
 *
 * الترتيب ده مطلوب صراحةً، بس الاحتياطي مش تزويق: قاعدة المشروع المكتوبة في
 * [LocationIqRepo] إن "المفتاح سر سيرفر فقط — أبداً في الـ APK، عشان محدش يقدر يفك
 * التطبيق ويستهلك حصة الحساب المجانية". أي حد يفك الـ APK يقدر يقرا المفتاح ويصرف
 * الحصة. المسار السيرفري بيخلي شحن APK من غير مفتاح خيار شغّال بالكامل، مش نسخة ناقصة.
 *
 * ### الكاش
 *
 * SharedPreferences مش Room — نفس منطق [AiLocalCache]: بضع مئات من الروابط القصيرة،
 * مفيش شكل علائقي ولا استعلامات فوقها، فجدول وmigration مش هيشتروا حاجة.
 *
 * الكاش **مش** مقسوم على المستخدم، عكس [AiLocalCache]. صورة "كشري" مش بيانات حد — هي
 * نفس الصورة لأي حساب على الجهاز، وتقسيمها كان هيخلي كل حساب يدفع نفس النداء تاني من
 * غير أي مكسب خصوصية.
 *
 * والغياب متخزّن زي الوجود. مصطلح Pexels مالهاش صورة ليه (اسم أكلة محلية نادرة مثلاً)
 * كان هيتسأل عنه مع كل recomposition لو خزّنا النجاح بس — يعني نداء شبكة على كل تمرير
 * للشاشة لأكلة معروف إنها مش هتلاقي. TTL الغياب أقصر، عشان صورة اتضافت لـPexels بعدين
 * تلاقي طريقها في النهاية.
 */
object PexelsRepo {

    private const val PREFS = "zad_pexels_cache"

    /** روابط Pexels ثابتة (CDN مالوش انتهاء)، فأسبوع مش تخمين — هو ببساطة أطول من أي
     *  جلسة، والغرض إن نفس الوصفة ماتكلفش نداء تاني بكرة. */
    private const val HIT_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** ١٢ ساعة للغياب — أقصر بكتير، عشان مصطلح مالوش صورة النهاردة ياخد فرصة تانية. */
    private const val MISS_TTL_MS = 12L * 60 * 60 * 1000

    /** القيمة اللي في `.env.example`. لو دي هي اللي اتجمّعت، يبقى مفيش مفتاح فعلي. */
    private const val PLACEHOLDER_KEY = "MY_PEXELS_API_KEY"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * "" معناها مفيش مفتاح متجمّع. القيمة الافتراضية بتتعامل كغياب عن قصد: بناء CI
     * مالوش `.env`، فالـSecrets plugin بيقع على `.env.example` وبيحط النص الحرفي
     * `MY_PEXELS_API_KEY` جوه الـAPK. من غير الفحص ده كل صورة في بناء CI كانت هتاخد
     * رحلة شبكة كاملة عشان ترجع 401.
     */
    private fun compiledKey(): String {
        val key = BuildConfig.PEXELS_API_KEY.trim()
        return if (key.isEmpty() || key == PLACEHOLDER_KEY) "" else key
    }

    /**
     * المفتاح بيتلوّر ويتشال منه التشكيل عشان "شاي بالحليب" و"شاي بالحليب " ما يبقوش
     * مدخلين مختلفين في الكاش. مش بنعمل أكتر من كده — تطبيع عربي أعمق (شيل الهمزات،
     * توحيد الياء) كان هيدمج مصطلحات المستخدم قصد يفرّق بينها.
     */
    private fun normalize(query: String): String = query.trim().lowercase()

    private fun cacheKeyFor(query: String) = "img:" + normalize(query)

    /**
     * رابط صورة للمصطلح ده.
     *
     * بيرجع من الكاش من غير أي شبكة لو المدخل لسه صالح. بقى بيرجّع صورة أكل عامة بدل
     * `null` لما البحث ميجيبش حاجة مناسبة — `null` فاضل بس للمصطلح الفاضي. الشاشات لسه
     * عندها البديل بتاعها (أيقونة/إيموجي) لو الرابط نفسه فشل يحمّل.
     */
    suspend fun imageUrlFor(context: Context, query: String): String? {
        val q = normalize(query)
        if (q.isEmpty()) return null

        readCache(context, q)?.let { return it.url }

        // الاسم زي ما العميل قاله مش استعلام بحث. "مطبخ" كانت بترجّع مطبخ فاضي و"لبن
        // ومية" منظر طبيعي — شوف FoodImageQuery لتفصيل التنضيف والترجمة والمؤهِّل.
        val term = FoodImageQuery.toSearchTerm(q)
        val fetched = if (term.isEmpty()) {
            null
        } else if (compiledKey().isNotEmpty()) {
            fetchDirect(term)
        } else {
            fetchViaEdgeFunction(term)
        }

        // بديل متأكدين إنه أكل بدل `null`. الكارت كان بيرسم أيقونة شوكة وسكينة على مربع
        // فاضي، وده بيبان كأنه بيحمّل ومش هيخلص. صورة أكل عامة أصدق بصرياً من كارت مكسور،
        // وبتفضل ثابتة لنفس الأكلة عشان مافيش رقص بين تمريرتين.
        val result = fetched ?: FoodImageQuery.fallbackUrl(q)
        writeCache(context, q, result)
        return result
    }

    /** null = مفيش مدخل صالح. Entry.url ممكن تكون null، ودي "متأكدين إن مفيش صورة". */
    private data class Entry(val url: String?)

    private fun readCache(context: Context, q: String): Entry? {
        val raw = prefs(context).getString(cacheKeyFor(q), null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val savedAt = obj.optLong("ts", 0L)
            val url = if (obj.isNull("url")) null else obj.optString("url").takeIf { it.isNotEmpty() }
            val ttl = if (url == null) MISS_TTL_MS else HIT_TTL_MS
            if (System.currentTimeMillis() - savedAt > ttl) null else Entry(url)
        } catch (e: Exception) {
            // مدخل تالف مايستاهلش يفشّل عرض الصورة — نتعامل معاه كأنه مش موجود.
            Log.w(TAG, "readCache() dropped a malformed entry for \"$q\": ${e.message}")
            null
        }
    }

    private fun writeCache(context: Context, q: String, url: String?) {
        val obj = JSONObject().apply {
            if (url == null) put("url", JSONObject.NULL) else put("url", url)
            put("ts", System.currentTimeMillis())
        }
        prefs(context).edit().putString(cacheKeyFor(q), obj.toString()).apply()
    }

    /**
     * نداء مباشر لـPexels. الترويسة بتاخد المفتاح **خام** — مش `Bearer` ولا `Client-ID`
     * زي Unsplash؛ أي بادئة بترجّع 401.
     */
    private suspend fun fetchDirect(q: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(
                "https://api.pexels.com/v1/search?per_page=8&orientation=landscape&query=" +
                    URLEncoder.encode(q, "UTF-8")
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", compiledKey())
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "pexels ${conn.responseCode} for \"$q\"")
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseFirstPhotoUrl(body)
        } catch (e: Exception) {
            Log.w(TAG, "fetchDirect(\"$q\") FAILED: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * مرئي للاختبار: فصل قراءة الرد عن الشبكة، عشان شكل رد Pexels يتأكد من غير جهاز
     * ولا مفتاح. `landscape` هو المقصوص للعرض اللي الكروت محتاجاه؛ `large` احتياطي.
     */
    internal fun parseFirstPhotoUrl(body: String): String? = try {
        val photos = JSONObject(body).optJSONArray("photos")
        // كان بياخد أول صورة أياً كانت. Pexels مبترجّعش فاضي — بترجّع أقرب حاجة عندها،
        // فاستعلام مش دقيق كان بيرجّع جبل أو بحيرة والكارت يعرضه كأنه الأكلة. بنعدّي على
        // النتايج ونقف عند أول واحدة وصفها مش بيقول صراحةً إنها حاجة تانية.
        val count = photos?.length() ?: 0
        var chosen: String? = null
        for (i in 0 until count) {
            val photo = photos?.optJSONObject(i) ?: continue
            val src = photo.optJSONObject("src")
            val candidate = listOf("landscape", "large", "original")
                .firstNotNullOfOrNull { src?.optString(it)?.takeIf { u -> u.isNotEmpty() } }
                ?: continue
            if (FoodImageQuery.looksLikeFood(photo.optString("alt"))) {
                chosen = candidate
                break
            }
        }
        // كل النتايج اتوصفت بحاجة مش أكل = الاستعلام نفسه ضايع. بنرجّع `null` عشان
        // الكولر يروح للبديل المضمون، مش عشان نعرض أول صورة غلط.
        if (chosen == null && count > 0) {
            Log.w(TAG, "pexels returned $count photos, none of them read as food")
        }
        chosen
    } catch (e: Exception) {
        Log.w(TAG, "parseFirstPhotoUrl() FAILED: ${e.message}")
        null
    }

    /** المسار من غير مفتاح في الـAPK — نفس بحث Pexels، بس المفتاح فاضل على السيرفر. */
    private suspend fun fetchViaEdgeFunction(q: String): String? {
        return try {
            val userId = SupabaseRepo.client.auth.currentUserOrNull()?.id.orEmpty()
            val response = SupabaseRepo.callEdgeFunction(
                "zad-core-intelligence",
                mapOf("action" to "pexels_image", "user_id" to userId, "payload" to mapOf("query" to q))
            )
            (response["image_url"] as? String)?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "fetchViaEdgeFunction(\"$q\") FAILED: ${e.message}")
            null
        }
    }
}
