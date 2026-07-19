package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ZadAiProxyClient
import com.example.data.ZadAiRepository
import com.example.data.ZadTransaction
import com.example.data.local.ZadDao
import com.example.data.local.ZadDatabase
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIntegrationTest {

    private lateinit var db: ZadDatabase
    private lateinit var dao: ZadDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, ZadDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.zadDao()
        
        mockkObject(ZadAiProxyClient)
    }

    @After
    fun closeDb() {
        db.close()
        unmockkAll()
    }

    @Test
    fun `test Room Database INSERT UPDATE DELETE`() = runBlocking {
        val tx = ZadTransaction(id = "1", title = "Test DB", amount = 100.0, isExpense = true, category = "Food", createdAt = "Now")
        
        // INSERT
        dao.insertTransaction(tx)
        val afterInsert = dao.getAllTransactions().first()
        assertTrue(afterInsert.contains(tx))
        
        // UPDATE (Assuming insert with same ID replaces or we update it)
        val updatedTx = tx.copy(amount = 200.0)
        dao.insertTransaction(updatedTx) // OnConflictStrategy.REPLACE is used in Dao
        val afterUpdate = dao.getAllTransactions().first()
        assertEquals(200.0, afterUpdate.find { it.id == "1" }?.amount)
        
        // DELETE
        dao.deleteTransaction(updatedTx)
        val afterDelete = dao.getAllTransactions().first()
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `test data flows from Notification to Room with Mock Supabase Edge Function`() = runBlocking {
        // Mock the Edge Function Response
        val mockTx = ZadTransaction(id = "tx_mock_1", title = "Mocked AI Response", amount = 50.0, isExpense = true, category = "Test", createdAt = "Now")
        coEvery { ZadAiProxyClient.analyzeBankNotification(any(), any()) } returns mockTx
        
        // Simulate NotificationListener extracting text
        val title = "Bank"
        val text = "Payment of 50 SAR"
        
        // Call the repository just like NotificationListener does
        val parsedTx = ZadAiRepository.analyzeBankNotification(title, text)
        
        // Simulate Supabase Repo inserting into local DB
        if (parsedTx != null) {
            dao.insertTransaction(parsedTx)
        }
        
        // Verify ViewModel / DB layer flow
        val flowData = dao.getAllTransactions().first()
        assertTrue(flowData.any { it.title == "Mocked AI Response" && it.amount == 50.0 })
    }
}
