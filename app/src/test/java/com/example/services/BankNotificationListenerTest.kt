package com.example.services

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.data.SupabaseRepo
import com.example.data.ZadAiRepository
import com.example.data.ZadTransaction
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class BankNotificationListenerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var listener: BankNotificationListener

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkObject(ZadAiRepository)
        mockkObject(SupabaseRepo)
        
        listener = BankNotificationListener()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createMockSbn(title: String, text: String, packageName: String = "com.bank.app"): StatusBarNotification {
        val sbn = mockk<StatusBarNotification>()
        val notification = mockk<Notification>()
        val extras = mockk<Bundle>()

        every { sbn.notification } returns notification
        every { sbn.packageName } returns packageName
        every { notification.extras } returns extras
        every { extras.getString("android.title") } returns title
        every { extras.getCharSequence("android.text") } returns text

        return sbn
    }

    @org.junit.Ignore("Supabase initialization causes ExceptionInInitializerError in Robolectric")
    @Test
    fun `test parseNotification with 10 different banking models`() = runTest(testDispatcher) {
        val mockTransaction = ZadTransaction(title = "test", amount = 100.0, isExpense = true, category = "food", createdAt = "now")
        coEvery { ZadAiRepository.analyzeBankNotification(any(), any()) } returns mockTransaction
        coEvery { SupabaseRepo.addTransaction(any()) } just Runs

        val samples = listOf(
            Pair("CIB Bank", "خصم 500 ريال من حسابك لشراء من مطعم"),
            Pair("NBE Alert", "Payment of 200 SAR to Uber"),
            Pair("Banque Misr", "تم تحويل مبلغ 1000 ريال إليك"),
            Pair("Al Rajhi", "شراء بمبلغ 50.5 SAR من بنده"),
            Pair("SNB", "دفع 15.0 ريال فاتورة اتصالات"),
            Pair("Riyad Bank", "خصم 10 SAR رسوم بطاقة"),
            Pair("SABB", "Transfer of 500 SAR completed"),
            Pair("Alinma", "Purchase of 30 SAR at Starbucks"),
            Pair("ANB", "تم دفع 200 ريال لشركة المياه"),
            Pair("STC Pay", "You paid 45.0 SAR at McDonald's")
        )

        for ((title, text) in samples) {
            val sbn = createMockSbn(title, text)
            listener.onNotificationPosted(sbn)
        }
        
        // Wait for coroutines to finish
        testScheduler.advanceUntilIdle()

        // Verify that AI analysis was called 10 times
        coVerify(exactly = 10) { ZadAiRepository.analyzeBankNotification(any(), any()) }
        coVerify(exactly = 10) { SupabaseRepo.addTransaction(mockTransaction) }
    }

    @org.junit.Ignore("Supabase initialization causes ExceptionInInitializerError in Robolectric")
    @Test
    fun `test non-financial notification is ignored`() = runTest(testDispatcher) {
        val sbn = createMockSbn("WhatsApp", "Hello, how are you?")
        
        listener.onNotificationPosted(sbn)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { ZadAiRepository.analyzeBankNotification(any(), any()) }
    }
}
