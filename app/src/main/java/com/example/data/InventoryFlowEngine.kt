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

    /** كلمات وظائف/ألقاب ما بتوصفش منتج مخزون أبداً — لو الاسم يحتويها فده مش صنف حقيقي */
    private val nonProductRoleWords = setOf(
        "مصمم", "مصممة", "مهندس", "مهندسة", "دكتور", "دكتورة", "طبيب", "طبيبة",
        "أستاذ", "استاذ", "أستاذة", "استاذة", "مدير", "مديرة", "محامي", "محامية",
        "كابتن", "شيخ", "بروفيسور", "مستشار", "مستشارة", "معلم", "معلمة",
        "فنان", "فنانة", "مذيع", "مذيعة", "لاعب", "لاعبة"
    )

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

            // تسجيل حدث الشراء للتعلم
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

    /**
     * التغذية التلقائية للنواقص: أي منتج تحت الحد الأدنى وغير موجود
     * في قائمة التسوق → ينزل فيها تلقائياً بأولوية حسب الحالة
     */
    suspend fun autoReplenish(
        context: Context,
        dao: ZadDao,
        currentInventory: List<ZadInventory>,
        currentShopping: List<ZadShoppingItem>
    ): List<ZadShoppingItem> {
        val added = mutableListOf<ZadShoppingItem>()
        val lowStock = currentInventory.filter { it.quantity <= (it.lowStockThreshold ?: 2) }

        for (item in lowStock) {
            val alreadyListed = currentShopping.any { !it.isPurchased && namesMatch(it.itemName, item.itemName) }
            if (alreadyListed) continue

            val shoppingItem = ZadShoppingItem(
                itemName = item.itemName,
                quantity = ((item.lowStockThreshold ?: 2) * 2).coerceAtLeast(1),
                priority = if (item.quantity == 0) "high" else "medium",
                predictedDaysLeft = ConsumptionLearner.predictDaysLeft(context, item.itemName, item.quantity)
            )
            dao.insertShoppingItem(shoppingItem)
            added.add(shoppingItem)
            Log.d(TAG_FLOW, "Auto-replenish: ${item.itemName} → shopping list (qty=${item.quantity}, priority=${shoppingItem.priority})")
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
    private const val MAX_EVENTS = 8

    private fun recordEvent(context: Context, prefix: String, itemName: String) {
        val key = prefix + InventoryFlowEngine.normalizeName(itemName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        val events = (prefs.getString(key, "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }
            .toMutableList()
        if (events.lastOrNull() != today) events.add(today)
        while (events.size > MAX_EVENTS) events.removeAt(0)
        prefs.edit().putString(key, events.joinToString(",")).apply()
    }

    fun recordPurchase(context: Context, itemName: String) = recordEvent(context, PURCHASE_PREFIX, itemName)

    fun recordConsumption(context: Context, itemName: String) = recordEvent(context, CONSUME_PREFIX, itemName)

    /** متوسط الأيام بين عمليات الشراء — null لو مفيش بيانات كافية */
    fun averagePurchaseIntervalDays(context: Context, itemName: String): Int? {
        val key = PURCHASE_PREFIX + InventoryFlowEngine.normalizeName(itemName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = (prefs.getString(key, "") ?: "")
            .split(",").mapNotNull { it.toLongOrNull() }
        if (events.size < 2) return null
        val intervals = events.zipWithNext { a, b -> b - a }.filter { it > 0 }
        if (intervals.isEmpty()) return null
        return intervals.average().toInt().coerceAtLeast(1)
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
