package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDao
import java.time.LocalDate

private const val TAG_FLOW = "InventoryFlowEngine"

/**
 * محرك الدورة المغلقة للمخزون — Closed-Loop Inventory
 *
 * الدورة الكاملة:
 * 1. تصوير فاتورة/مخزون → حقن ذكي (منتج موجود؟ زوّد الكمية. جديد؟ أضفه)
 * 2. الحقن يشيل المنتج تلقائياً من قائمة النواقص/التسوق ✅
 * 3. الاستهلاك ينقص الكمية → وصل الحد الأدنى؟ ينزل تلقائياً في النواقص
 * 4. التعلم: يسجل تواريخ الشراء ويتنبأ بمعدل استهلاك كل منتج
 * 5. المتابعة الدورية: "هل خلص X؟" للمنتجات المتوقع نفادها
 */
object InventoryFlowEngine {

    // ─── مطابقة أسماء المنتجات ───────────────────────────────────

    /** توحيد الاسم للمطابقة: حليب المراعي = المراعي حليب = حليب */
    fun normalizeName(name: String): String =
        name.trim().lowercase()
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ة", "ه").replace("ى", "ي")
            .split(Regex("\\s+"))
            .joinToString(" ") { it.removePrefix("ال") }

    /** مطابقة مرنة: تطابق كامل أو احتواء كلمة أساسية */
    fun namesMatch(a: String, b: String): Boolean {
        val na = normalizeName(a)
        val nb = normalizeName(b)
        if (na == nb) return true
        val wordsA = na.split(" ").filter { it.length > 2 }.toSet()
        val wordsB = nb.split(" ").filter { it.length > 2 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val common = wordsA.intersect(wordsB)
        return common.size >= minOf(wordsA.size, wordsB.size).coerceAtMost(2)
    }

    // ─── فلترة أسماء غير منتجات (مسميات وظائف/ألقاب بدل منتجات) ───

    /**
     * كلمات وظائف/ألقاب ما بتوصفش منتج مخزون أبداً — لو الاسم يحتويها فده مش صنف حقيقي.
     * لازم تتعمل عليها نفس [normalizeName] المطبقة على المدخل (خصوصاً ة→ه) وإلا الصيغة
     * المؤنثة (مهندسة، دكتورة...) متتطابقش أبداً مع الاسم المُطبّع.
     */
    private val nonProductRoleWords = setOf(
        "مصمم", "مصممة", "مهندس", "مهندسة", "دكتور", "دكتورة", "طبيب", "طبيبة",
        "أستاذ", "أستاذة", "مدير", "مديرة", "محامي", "محامية",
        "كابتن", "شيخ", "بروفيسور", "مستشار", "مستشارة", "معلم", "معلمة",
        "فنان", "فنانة", "مذيع", "مذيعة", "لاعب", "لاعبة"
    ).map { normalizeName(it) }.toSet()

    /**
     * فحص عالي الدقة (زيرو false positive تقريباً): اسم فيه كلمة وظيفة/لقب معروفة
     * = مش منتج مخزون، أياً كان مصدره (شات AI أو حقن قديم). لا يحاول يخمّن أسماء أشخاص
     * عادية (زي "جمال كامل") لأن ده معرّض لحذف منتجات حقيقية غلط.
     */
    fun looksLikeNonProductName(name: String): Boolean {
        val words = normalizeName(name).split(" ").filter { it.isNotBlank() }
        return words.any { it in nonProductRoleWords }
    }

    // ─── نتيجة الحقن ─────────────────────────────────────────────

    data class InjectionResult(
        val addedNew: List<ZadInventory>,
        val updatedExisting: List<ZadInventory>,
        val removedFromShopping: List<ZadShoppingItem>
    ) {
        val summary: String
            get() = buildString {
                if (addedNew.isNotEmpty()) append("${addedNew.size} منتج جديد")
                if (updatedExisting.isNotEmpty()) {
                    if (isNotEmpty()) append(" + ")
                    append("تحديث كمية ${updatedExisting.size}")
                }
                if (removedFromShopping.isNotEmpty()) {
                    if (isNotEmpty()) append(" + ")
                    append("شُطب ${removedFromShopping.size} من النواقص ✅")
                }
                if (isEmpty()) append("لا تغييرات")
            }
    }

    /**
     * الحقن الذكي: منتجات مصوّرة (فاتورة أو رفّ مخزون) تدخل المخزون
     * - موجود بنفس الاسم؟ → تزويد الكمية (مش تكرار صف)
     * - على قائمة النواقص؟ → يتشال منها (الدورة اتقفلت)
     * - يسجل حدث شراء للتعلم والتنبؤ
     */
    suspend fun injectScannedItems(
        context: Context,
        dao: ZadDao,
        currentInventory: List<ZadInventory>,
        currentShopping: List<ZadShoppingItem>,
        scannedItems: List<ZadInventory>
    ): InjectionResult {
        val addedNew = mutableListOf<ZadInventory>()
        val updatedExisting = mutableListOf<ZadInventory>()
        val removedFromShopping = mutableListOf<ZadShoppingItem>()

        for (scanned in scannedItems) {
            val existing = currentInventory.firstOrNull { namesMatch(it.itemName, scanned.itemName) }
            if (existing != null) {
                val updated = existing.copy(quantity = existing.quantity + scanned.quantity.coerceAtLeast(1))
                dao.insertInventoryItem(updated) // REPLACE على نفس الـ id
                updatedExisting.add(updated)
                Log.d(TAG_FLOW, "Updated qty: ${existing.itemName} ${existing.quantity} → ${updated.quantity}")
            } else {
                dao.insertInventoryItem(scanned)
                addedNew.add(scanned)
                Log.d(TAG_FLOW, "Added new: ${scanned.itemName} x${scanned.quantity}")
            }

            // قفل الدورة: المنتج اتشترى → يتشال من النواقص
            currentShopping
                .filter { !it.isPurchased && namesMatch(it.itemName, scanned.itemName) }
                .forEach { shoppingItem ->
                    dao.setShoppingItemPurchased(shoppingItem.id, true)
                    removedFromShopping.add(shoppingItem)
                    Log.d(TAG_FLOW, "Closed loop: ${shoppingItem.itemName} marked purchased")
                }

            // تسجيل حدث الشراء للتعلم المحلي — الرفع للسيرفر (zad_record_observation) بقى
            // مسؤولية الـcaller دلوقتي (32.3)، مش هنا. كان فيه نداء سيرفر هنا بمصدر
            // "consumption_learner" — قيمة مش موجودة في zad_inventory_observations_source_check
            // (question_answer|camera_ocr|manual|purchase|chat_add بس)، فكل نداء منه كان بيفشل
            // بصمت (0 صف بمصدر ده في الجدول الحي، اتأكد بـ execute_sql) من غير أي SyncOutbox
            // fallback — خسارة تعلّم صامتة تامة. اتشال بدل ما يتصلح لأنه أصلاً مكرر: أي caller
            // بيعدّي على ZadViewModel.injectScannedItems() (شاشة الكاميرا) بيسجل observation
            // صح بمصدر "camera_ocr" فعلاً في الطبقة اللي فوق. الـcaller الوحيد اللي كان معتمد
            // على النداء الميت ده هو addGroceryPurchaseItem() — بقى بيسجل بنفسه دلوقتي.
            ConsumptionLearner.recordPurchase(context, scanned.itemName)
        }

        return InjectionResult(addedNew, updatedExisting, removedFromShopping)
    }

    // ─── الاستهلاك والنقص التلقائي ───────────────────────────────

    data class ConsumeResult(
        val updatedItem: ZadInventory,
        val hitLowStock: Boolean,
        val depleted: Boolean
    )

    /** استهلاك: ينقص الكمية — وصل الحد؟ بيرجع flag عشان النواقص والإشعار */
    suspend fun consumeItem(
        context: Context,
        dao: ZadDao,
        item: ZadInventory,
        amount: Int = 1
    ): ConsumeResult {
        val newQty = (item.quantity - amount).coerceAtLeast(0)
        val updated = item.copy(quantity = newQty)
        dao.insertInventoryItem(updated)
        ConsumptionLearner.recordConsumption(context, item.itemName)

        val threshold = item.lowStockThreshold ?: 2
        Log.d(TAG_FLOW, "Consumed: ${item.itemName} ${item.quantity} → $newQty (threshold=$threshold)")
        return ConsumeResult(
            updatedItem = updated,
            hitLowStock = newQty <= threshold,
            depleted = newQty == 0
        )
    }

    private const val STAGNANT_DAYS = 30L

    private fun parseToEpochDay(raw: String): Long? = try {
        java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    } catch (e: Exception) {
        try { LocalDate.parse(raw.take(10)).toEpochDay() } catch (e2: Exception) { null }
    }

    /**
     * Task 23 — راكد: مفيش نقص في الكمية آخر ٣٠ يوم ومفيش عينة استهلاك واحدة اتسجلت خالص.
     * آخر نشاط (شراء أو استهلاك) هو المرجع، مش تاريخ الإضافة وحده — عشان صنف بيتجدد شراؤه
     * بانتظام (تصوير فاتورة بيزوّد كميته من غير ما يستهلك حد منه لسه) ميتصنفش راكد غلط لمجرد
     * إن أول إضافة له كانت من شهر. لو مفيش أي نشاط خالص، تاريخ الإضافة نفسه هو المرجع.
     */
    fun isStagnant(context: Context, item: ZadInventory): Boolean {
        if (item.quantity <= 0) return false
        val today = LocalDate.now().toEpochDay()
        val referenceDay = ConsumptionLearner.lastActivityEpochDay(context, item.itemName)
            ?: item.createdAt?.let { parseToEpochDay(it) }
            ?: return false
        if (today - referenceDay < STAGNANT_DAYS) return false
        return !ConsumptionLearner.hasAnyConsumeEvent(context, item.itemName)
    }

    /**
     * التغذية التلقائية للنواقص: أي منتج تحت الحد الأدنى وغير موجود
     * في قائمة التسوق → ينزل فيها تلقائياً بأولوية حسب الحالة
     *
     * Task 23 — الأصناف الراكدة مستبعدة: لو المستخدم مش بيستخدمها أصلاً، اقتراح "اشتري منها
     * تاني" غلط بالكامل، بغض النظر عن الكمية الحالية.
     */
    suspend fun autoReplenish(
        context: Context,
        dao: ZadDao,
        currentInventory: List<ZadInventory>,
        currentShopping: List<ZadShoppingItem>
    ): List<ZadShoppingItem> {
        val added = mutableListOf<ZadShoppingItem>()
        val lowStock = currentInventory.filter {
            it.quantity <= (it.lowStockThreshold ?: 2) && !isStagnant(context, it)
        }

        for (item in lowStock) {
            val alreadyListed = currentShopping.any { !it.isPurchased && namesMatch(it.itemName, item.itemName) }
            if (alreadyListed) continue

            // بند 32.5 — سعر حقيقي من estimate_price (بحث ويب فعلي)، مش رقم مخترع. فشل الشبكة/
            // الذكاء الاصطناعي مايوقفش الإضافة — estimatedPrice بيفضل ٠.٠ (نفس الافتراضي
            // الموجود أصلاً في ZadShoppingItem)، وده حالة "مش معروف" مش سعر كاذب.
            val priceEstimate = try {
                com.example.data.ZadAiRepository.estimatePrice(item.itemName)
            } catch (e: Exception) {
                Log.w(TAG_FLOW, "estimatePrice(${item.itemName}) failed: ${e.message}")
                null
            }

            val shoppingItem = ZadShoppingItem(
                itemName = item.itemName,
                quantity = ((item.lowStockThreshold ?: 2) * 2).coerceAtLeast(1),
                priority = if (item.quantity == 0) "high" else "medium",
                predictedDaysLeft = ConsumptionLearner.predictDaysLeft(context, item.itemName, item.quantity),
                estimatedPrice = priceEstimate?.avgPrice ?: 0.0,
                store = priceEstimate?.store
            )
            dao.insertShoppingItem(shoppingItem)
            added.add(shoppingItem)
            Log.d(TAG_FLOW, "Auto-replenish: ${item.itemName} → shopping list (qty=${item.quantity}, priority=${shoppingItem.priority}, est.price=${shoppingItem.estimatedPrice})")
        }
        return added
    }

    // ─── المتابعة الدورية ────────────────────────────────────────

    data class CheckInCandidate(val item: ZadInventory, val predictedDaysLeft: Int)

    /**
     * مرشحو المتابعة: منتجات كميتها > 0 لكن التنبؤ يقول خلصت أو قربت —
     * التطبيق يسأل: "هل خلص X فعلاً؟" ويتعلم من الإجابة
     */
    fun getCheckInCandidates(context: Context, inventory: List<ZadInventory>): List<CheckInCandidate> =
        inventory.mapNotNull { item ->
            if (item.quantity <= 0) return@mapNotNull null
            // مرحلة ٣ (docs/agent/PLAN_2026_08_06_rebuild.md) — "لسه" بترجّي السؤال ٣ أيام
            // بدل ما يترجع فوراً في أول إعادة حساب جاية (أي تغيير مخزون تاني كان بيرجّعه).
            if (ConsumptionLearner.isSnoozed(context, item.itemName)) return@mapNotNull null
            val daysLeft = ConsumptionLearner.predictDaysLeft(context, item.itemName, item.quantity)
                ?: return@mapNotNull null
            if (daysLeft <= 1) CheckInCandidate(item, daysLeft) else null
        }.sortedBy { it.predictedDaysLeft }
}

/**
 * متعلّم الاستهلاك — يسجل الشراء والاستهلاك ويتنبأ بمعدل كل منتج
 * تخزين خفيف في SharedPreferences: "epochDay,epochDay,..." (آخر 8 أحداث)
 */
object ConsumptionLearner {

    private const val PREFS = "zad_consumption"
    private const val PURCHASE_PREFIX = "buy_"
    private const val CONSUME_PREFIX = "use_"
    private const val SMOOTHED_INTERVAL_PREFIX = "smoothed_interval_"
    private const val SNOOZE_UNTIL_PREFIX = "checkin_snooze_until_"
    private const val MAX_EVENTS = 8
    private const val INTERVAL_ALPHA = 0.3 // weight on the newest interval — higher = more reactive to a recently-changed buying pattern
    private const val SNOOZE_DAYS = 3L

    private fun recordEvent(context: Context, prefix: String, itemName: String) {
        val key = prefix + InventoryFlowEngine.normalizeName(itemName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        val events = (prefs.getString(key, "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }
            .toMutableList()
        val previousLast = events.lastOrNull()
        if (previousLast != today) {
            events.add(today)
            // Incremental exponential smoothing: fold in only the ONE new interval here, at
            // record-time — averagePurchaseIntervalDays() then just reads the persisted level
            // instead of recomputing an average over the stored event list on every call.
            if (prefix == PURCHASE_PREFIX && previousLast != null) {
                val newInterval = (today - previousLast).toDouble()
                if (newInterval > 0) updateSmoothedInterval(context, itemName, newInterval)
            }
        }
        while (events.size > MAX_EVENTS) events.removeAt(0)
        prefs.edit().putString(key, events.joinToString(",")).apply()
    }

    private fun updateSmoothedInterval(context: Context, itemName: String, newInterval: Double) {
        val key = SMOOTHED_INTERVAL_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getFloat(key, -1f)
        val updated = if (previous < 0) newInterval else INTERVAL_ALPHA * newInterval + (1 - INTERVAL_ALPHA) * previous
        prefs.edit().putFloat(key, updated.toFloat()).apply()
    }

    fun recordPurchase(context: Context, itemName: String) = recordEvent(context, PURCHASE_PREFIX, itemName)

    fun recordConsumption(context: Context, itemName: String) = recordEvent(context, CONSUME_PREFIX, itemName)

    /**
     * مرحلة ٣ — المستخدم رد "لسه" على سؤال "هل خلص X؟": مش تصحيح استهلاك (مفيش حدث use_
     * أو buy_ يتسجل، عشان "لسه" مش معلومة عن معدل الاستهلاك نفسه)، بس تأجيل السؤال —
     * وإلا ده كان هيرجع يسأل تاني في أي إعادة حساب جاية (فتح شاشة، تحديث مخزون تاني...).
     */
    fun snoozeCheckIn(context: Context, itemName: String) {
        val key = SNOOZE_UNTIL_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val until = LocalDate.now().plusDays(SNOOZE_DAYS).toEpochDay()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(key, until).apply()
    }

    fun isSnoozed(context: Context, itemName: String): Boolean {
        val key = SNOOZE_UNTIL_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, -1L)
        return until >= 0 && LocalDate.now().toEpochDay() <= until
    }

    private fun eventDays(context: Context, prefix: String, itemName: String): List<Long> {
        val key = prefix + InventoryFlowEngine.normalizeName(itemName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (prefs.getString(key, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
    }

    /** Task 23 — "لا عينات استهلاك" يعني مفيش أي حدث use_ اتسجل خالص للصنف ده */
    fun hasAnyConsumeEvent(context: Context, itemName: String): Boolean =
        eventDays(context, CONSUME_PREFIX, itemName).isNotEmpty()

    /** Task 23 — آخر يوم اتسجل فيه أي نشاط (شراء أو استهلاك) للصنف ده، null لو مفيش خالص */
    fun lastActivityEpochDay(context: Context, itemName: String): Long? =
        (eventDays(context, PURCHASE_PREFIX, itemName) + eventDays(context, CONSUME_PREFIX, itemName)).maxOrNull()

    /** متوسط الأيام بين عمليات الشراء (بتنعيم أسي تراكمي) — null لو مفيش بيانات كافية */
    fun averagePurchaseIntervalDays(context: Context, itemName: String): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val smoothedKey = SMOOTHED_INTERVAL_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val smoothed = prefs.getFloat(smoothedKey, -1f)
        if (smoothed >= 0) return smoothed.toInt().coerceAtLeast(1)

        // No smoothed value yet (item bought only once so far, or data predates this upgrade) —
        // fall back to a plain average over the stored raw events, same as before, and seed the
        // smoothed value from it so the next call onward is an O(1) read instead of this scan.
        val rawKey = PURCHASE_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val events = (prefs.getString(rawKey, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
        if (events.size < 2) return null
        val intervals = events.zipWithNext { a, b -> b - a }.filter { it > 0 }
        if (intervals.isEmpty()) return null
        val avg = intervals.average()
        prefs.edit().putFloat(smoothedKey, avg.toFloat()).apply()
        return avg.toInt().coerceAtLeast(1)
    }

    /**
     * التنبؤ بالأيام المتبقية قبل النفاد:
     * آخر شراء + متوسط الدورة − اليوم، متناسب مع الكمية الحالية
     */
    fun predictDaysLeft(context: Context, itemName: String, currentQuantity: Int): Int? {
        val interval = averagePurchaseIntervalDays(context, itemName) ?: return null
        val key = PURCHASE_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastPurchase = (prefs.getString(key, "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }.lastOrNull() ?: return null

        val today = LocalDate.now().toEpochDay()
        val daysSincePurchase = (today - lastPurchase).toInt()
        val remaining = interval - daysSincePurchase
        return remaining.coerceIn(-30, 365)
    }
}
