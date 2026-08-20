package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AffiliateProduct
import com.example.data.PendingSyncOp
import com.example.data.RejectedBankMessage
import com.example.data.ZadBehaviorPattern
import com.example.data.ZadChatMessage
import com.example.data.ZadDoseLog
import com.example.data.ZadInventory
import com.example.data.ZadMaintenanceItem
import com.example.data.ZadPharmacyItem
import com.example.data.ZadShoppingItem
import com.example.data.ZadSubscription
import com.example.data.ZadTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AccountDataClearDaoTest {
    private lateinit var db: ZadDatabase
    private lateinit var dao: ZadDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZadDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.zadDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `clearAccountData removes private rows but keeps public catalog`() = runTest {
        dao.insertTransaction(ZadTransaction(amount = 10.0, title = "private transaction"))
        dao.insertInventoryItem(ZadInventory(itemName = "private inventory"))
        dao.insertSubscription(ZadSubscription(title = "private subscription", amount = 10.0))
        dao.insertPharmacyItem(ZadPharmacyItem(name = "private medicine"))
        dao.insertDoseLog(
            ZadDoseLog(
                pharmacyItemId = "medicine-id",
                itemName = "private medicine",
                scheduledAt = "2026-08-20T08:00:00Z"
            )
        )
        dao.insertMaintenanceItem(ZadMaintenanceItem(name = "private maintenance"))
        dao.insertBehaviorPattern(ZadBehaviorPattern(category = "private behavior"))
        dao.insertShoppingItem(ZadShoppingItem(itemName = "private shopping"))
        dao.insertChatMessage(ZadChatMessage(text = "private chat", isUser = true))
        dao.insertPendingSyncOp(
            PendingSyncOp(
                opType = "add_transaction",
                payloadJson = "{}",
                createdAt = "2026-08-20T08:00:00Z"
            )
        )
        dao.insertRejectedBankMessage(
            RejectedBankMessage(
                reason = "UNPARSED",
                source = "bank",
                rawText = "private bank message",
                createdAt = "2026-08-20T08:00:00Z"
            )
        )
        dao.insertAffiliateProducts(listOf(AffiliateProduct(productNameAr = "public catalog")))

        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO affiliate_clicks (id, product_id, source_screen) VALUES ('click', 'product', 'shopping')"
        )
        sqlite.execSQL(
            "INSERT INTO affiliate_catalog_requests (id, searched_term) VALUES ('request', 'private search')"
        )

        dao.clearAccountData()

        assertEquals(0, dao.getAllTransactionsOnce().size)
        assertEquals(0, dao.getAllInventory().first().size)
        assertEquals(0, dao.getAllSubscriptions().first().size)
        assertEquals(0, dao.getAllPharmacyItemsOnce().size)
        assertEquals(0, dao.getAllDoseLogs().first().size)
        assertEquals(0, dao.getAllMaintenanceItems().first().size)
        assertEquals(0, dao.getBehaviorPatterns().size)
        assertEquals(0, dao.getAllShoppingItems().first().size)
        assertEquals(0, dao.getChatMessageCount())
        assertEquals(0, dao.getAllPendingSyncOps().size)
        assertEquals(0, dao.getRejectedBankMessages().size)
        assertEquals(0, rowCount("affiliate_clicks"))
        assertEquals(0, rowCount("affiliate_catalog_requests"))
        assertEquals(1, dao.getAffiliateProducts().size)
    }

    private fun rowCount(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
