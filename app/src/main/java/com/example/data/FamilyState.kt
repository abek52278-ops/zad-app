package com.example.data

import android.content.Context
import com.example.data.local.ZadDao
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.flow.first

/**
 * Single-source-of-truth snapshot of a family's state, assembled once via [getFamilyContext]
 * instead of each caller (workers, brain engines) independently querying Room piecemeal.
 * Scoped to callers that genuinely need the whole picture (ZadCentralBrain's full analysis
 * and its AI tool-loop) — narrow ZadAiRepository actions (estimate_price, classifyBill, ...)
 * intentionally keep their own small payloads instead of carrying this around unused.
 */
data class ZadFamilyState(
    val budget: BudgetSnapshot,
    val inventory: InventorySnapshot,
    val pharmacy: PharmacySnapshot,
    val preferences: PreferencesSnapshot
)

data class BudgetSnapshot(
    val remaining: Double,
    val monthlySpend: Double,
    val categoryBreakdown: List<CategoryBudgetCard>
)

data class CategoryBudgetCard(val category: String, val budget: Double, val spent: Double)

data class InventorySnapshot(
    val items: List<ZadInventory>,
    val lowStockItems: List<ZadInventory>
)

data class PharmacySnapshot(
    val activeMeds: List<ZadPharmacyItem>,
    val schedules: List<ZadDoseLog>,
    val stockLevels: Map<String, Int>
)

data class PreferencesSnapshot(
    val frequentItems: List<String>,
    val preferredStores: List<String>
)

suspend fun getFamilyContext(context: Context): ZadFamilyState {
    val dao = ZadDatabase.getDatabase(context).zadDao()
    return buildZadFamilyState(dao, context)
}

suspend fun buildZadFamilyState(dao: ZadDao, context: Context): ZadFamilyState {
    val inventory = dao.getAllInventory().first()
    val transactions = dao.getAllTransactions().first()
    val pharmacyItems = dao.getAllPharmacyItemsOnce()
    val doseLogs = dao.getAllDoseLogs().first()

    val prefs = context.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
    // cached_budget دلوقتي مرآة لـ monthly_limit (ZadViewModel.loadBudget/updateBudget
    // بيحدّثوه) — مش عمود budget الميت. Task 19.0.
    // 0 = السقف لسه مش متسجل. الـ snapshot ده بيتبعت للعقل، و3500 هنا كانت بتتقرا كسقف
    // حقيقي للأسرة. BudgetMath.remaining بترجع 0 على أي سقف <= 0، فالنتيجة "مش معروف"
    // بدل "3500" — وده الفرق اللي بيمنع تقرير كامل مبني على رقم متأليف.
    val monthlyBudget = prefs.getFloat("cached_budget", 0f).toDouble()
    val remaining = BudgetMath.remaining(monthlyBudget, transactions)

    val lowStockItems = inventory.filter { it.quantity <= (it.lowStockThreshold ?: 0) }

    // Derived from real records, never invented: frequentItems from how often a transaction
    // title mentions an inventory item (same pattern as ZadViewModel.checkLowStockItems),
    // preferredStores from merchant_name frequency on parsed transactions.
    val frequentItems = inventory
        .map { item -> item.itemName to transactions.count { it.title.contains(item.itemName, ignoreCase = true) } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(5)
        .map { it.first }

    val preferredStores = transactions
        .mapNotNull { it.merchantName?.takeIf { name -> name.isNotBlank() } }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key }

    return ZadFamilyState(
        budget = BudgetSnapshot(
            remaining = remaining,
            monthlySpend = monthlyBudget - remaining,
            categoryBreakdown = BudgetTracker.getAllCategoryCards(context).map { (cat, budget, spent) ->
                CategoryBudgetCard(cat, budget, spent)
            }
        ),
        inventory = InventorySnapshot(items = inventory, lowStockItems = lowStockItems),
        pharmacy = PharmacySnapshot(
            activeMeds = pharmacyItems,
            schedules = doseLogs.filter { it.takenAt == null },
            stockLevels = pharmacyItems.associate { it.name to it.remainingQuantity }
        ),
        preferences = PreferencesSnapshot(frequentItems = frequentItems, preferredStores = preferredStores)
    )
}
