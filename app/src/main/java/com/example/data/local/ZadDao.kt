package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.ZadInventory
import com.example.data.ZadSubscription
import com.example.data.ZadTransaction
import com.example.data.ZadBehaviorPattern
import com.example.data.ZadShoppingItem
import com.example.data.AffiliateProduct
import com.example.data.ZadPharmacyItem
import com.example.data.ZadMaintenanceItem
import com.example.data.ZadDoseLog
import com.example.data.PendingSyncOp
import kotlinx.coroutines.flow.Flow

@Dao
interface ZadDao {
    @Query("SELECT * FROM zad_transactions ORDER BY createdAt DESC")
    fun getAllTransactions(): Flow<List<ZadTransaction>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM zad_transactions WHERE txnKind = 'expense' OR (txnKind IS NULL AND isExpense = 1)")
    fun getTotalExpenses(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM zad_transactions WHERE txnKind = 'income' OR (txnKind IS NULL AND isExpense = 0)")
    fun getTotalIncome(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<ZadTransaction>)

    @Query("SELECT * FROM zad_transactions ORDER BY createdAt DESC")
    suspend fun getAllTransactionsOnce(): List<ZadTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: ZadTransaction)

    @Query("SELECT * FROM zad_inventory ORDER BY createdAt DESC")
    fun getAllInventory(): Flow<List<ZadInventory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventory(inventory: List<ZadInventory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: ZadInventory)

    @Query("SELECT * FROM zad_subscriptions ORDER BY createdAt DESC")
    fun getAllSubscriptions(): Flow<List<ZadSubscription>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptions(subscriptions: List<ZadSubscription>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: ZadSubscription)
    
    @Query("DELETE FROM zad_subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)

    @Query("DELETE FROM zad_inventory WHERE id = :id")
    suspend fun deleteInventory(id: String)

    // Pharmacy
    @Query("SELECT * FROM zad_pharmacy_items ORDER BY createdAt DESC")
    fun getAllPharmacyItems(): Flow<List<ZadPharmacyItem>>

    @Query("SELECT * FROM zad_pharmacy_items ORDER BY createdAt DESC")
    suspend fun getAllPharmacyItemsOnce(): List<ZadPharmacyItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPharmacyItems(items: List<ZadPharmacyItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPharmacyItem(item: ZadPharmacyItem)

    @Query("DELETE FROM zad_pharmacy_items WHERE id = :id")
    suspend fun deletePharmacyItem(id: String)

    // Maintenance
    @Query("SELECT * FROM zad_maintenance_items ORDER BY createdAt DESC")
    fun getAllMaintenanceItems(): Flow<List<ZadMaintenanceItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceItems(items: List<ZadMaintenanceItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceItem(item: ZadMaintenanceItem)

    @Query("DELETE FROM zad_maintenance_items WHERE id = :id")
    suspend fun deleteMaintenanceItem(id: String)

    // Dose log (pharmacy adherence)
    @Query("SELECT * FROM zad_dose_log ORDER BY scheduledAt DESC")
    fun getAllDoseLogs(): Flow<List<ZadDoseLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(log: ZadDoseLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLogs(logs: List<ZadDoseLog>)

    @Query("SELECT * FROM zad_dose_log WHERE id = :id LIMIT 1")
    suspend fun getDoseLogById(id: String): ZadDoseLog?

    // Shopping List
    @Query("SELECT * FROM zad_shopping_list ORDER BY createdAt DESC")
    fun getAllShoppingItems(): Flow<List<ZadShoppingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ZadShoppingItem)

    @Query("UPDATE zad_shopping_list SET is_purchased = :purchased WHERE id = :id")
    suspend fun setShoppingItemPurchased(id: String, purchased: Boolean)

    @Query("DELETE FROM zad_shopping_list WHERE id = :id")
    suspend fun deleteShoppingItem(id: String)

    /**
     * الحاجات اللي اتشالت من السيرفر (أو اتدمجت في صف واحد) لازم تختفي من الجهاز كمان.
     * المزامنة كانت insert-only، فالصف المحلي كان بيفضل عايش للأبد — وده اللي خلّى
     * "مياه إيلانو" تفضل ظاهرة أربع مرات على الشاشة والجدول على السيرفر فيه صف واحد.
     * بتتنادى بعد فلاش الطابور بس، عشان صف اتعمل أوفلاين ولسه ما اترفعش ما يتمسحش.
     */
    @Query("DELETE FROM zad_shopping_list WHERE id NOT IN (:remoteIds)")
    suspend fun pruneShoppingItemsNotIn(remoteIds: List<String>)

    @Query("DELETE FROM zad_shopping_list")
    suspend fun clearShoppingItems()

    @Query("DELETE FROM zad_pharmacy_items WHERE id NOT IN (:remoteIds)")
    suspend fun prunePharmacyItemsNotIn(remoteIds: List<String>)

    @Query("DELETE FROM zad_pharmacy_items")
    suspend fun clearPharmacyItems()

    @Query("DELETE FROM zad_inventory WHERE id NOT IN (:remoteIds)")
    suspend fun pruneInventoryNotIn(remoteIds: List<String>)

    @Query("DELETE FROM zad_inventory")
    suspend fun clearInventory()

    @Query("DELETE FROM zad_subscriptions WHERE id NOT IN (:remoteIds)")
    suspend fun pruneSubscriptionsNotIn(remoteIds: List<String>)

    @Query("DELETE FROM zad_subscriptions")
    suspend fun clearSubscriptions()

    @Query("DELETE FROM zad_maintenance_items WHERE id NOT IN (:remoteIds)")
    suspend fun pruneMaintenanceItemsNotIn(remoteIds: List<String>)

    @Query("DELETE FROM zad_maintenance_items")
    suspend fun clearMaintenanceItems()

    @Query("DELETE FROM zad_transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM zad_dose_log")
    suspend fun clearDoseLogs()

    @Query("DELETE FROM zad_behavior_patterns")
    suspend fun clearBehaviorPatterns()

    @Query("DELETE FROM zad_pending_sync_ops")
    suspend fun clearPendingSyncOps()

    @Query("DELETE FROM zad_rejected_bank_messages")
    suspend fun clearRejectedBankMessages()

    @Query("DELETE FROM affiliate_clicks")
    suspend fun clearAffiliateClicks()

    @Query("DELETE FROM affiliate_catalog_requests")
    suspend fun clearAffiliateCatalogRequests()

    /**
     * Room isn't partitioned per account, so every user-owned row must leave together.
     * Keeping this transactional prevents another account from seeing a half-cleared cache.
     */
    @Transaction
    suspend fun clearAccountData() {
        clearTransactions()
        clearInventory()
        clearSubscriptions()
        clearPharmacyItems()
        clearDoseLogs()
        clearMaintenanceItems()
        clearBehaviorPatterns()
        clearShoppingItems()
        clearChatMessages()
        clearPendingSyncOps()
        clearRejectedBankMessages()
        clearAffiliateClicks()
        clearAffiliateCatalogRequests()
    }

    @Query("DELETE FROM zad_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    // Offline sync outbox (see PendingSyncOp)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSyncOp(op: PendingSyncOp)

    @Query("SELECT * FROM zad_pending_sync_ops ORDER BY createdAt ASC")
    suspend fun getAllPendingSyncOps(): List<PendingSyncOp>

    @Query("DELETE FROM zad_pending_sync_ops WHERE id = :id")
    suspend fun deletePendingSyncOp(id: String)

    // Behavior Patterns
    @Query("SELECT * FROM zad_behavior_patterns")
    suspend fun getBehaviorPatterns(): List<ZadBehaviorPattern>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBehaviorPattern(pattern: ZadBehaviorPattern)

    // Affiliate Products
    @Query("SELECT * FROM affiliate_products WHERE is_active = 1")
    suspend fun getAffiliateProducts(): List<AffiliateProduct>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAffiliateProducts(products: List<AffiliateProduct>)

    // Zad Chat — ذاكرة الشات الدائمة
    @Query("SELECT * FROM zad_chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<com.example.data.ZadChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: com.example.data.ZadChatMessage)

    @Query("DELETE FROM zad_chat_messages")
    suspend fun clearChatMessages()

    @Query("SELECT COUNT(*) FROM zad_chat_messages")
    suspend fun getChatMessageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRejectedBankMessage(message: com.example.data.RejectedBankMessage)

    @Query("SELECT * FROM zad_rejected_bank_messages ORDER BY createdAt DESC")
    suspend fun getRejectedBankMessages(): List<com.example.data.RejectedBankMessage>

    @Query("""
        DELETE FROM zad_rejected_bank_messages WHERE id NOT IN (
            SELECT id FROM zad_rejected_bank_messages ORDER BY createdAt DESC LIMIT 200
        )
    """)
    suspend fun trimRejectedBankMessages()
}
