package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.RejectedBankMessage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تحقق من إن سجل الرسائل المرفوضة (Task 4) فعلاً مقفول على ٢٠٠ صف — مش بينمو من غير حد.
 * ده أهم اختبار هنا: لو الـ trim مبيشتغلش، الجدول ده بيكبر بلا نهاية على جهاز المستخدم.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RejectedBankMessageDaoTest {

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
    fun tearDown() {
        db.close()
    }

    @Test
    fun `trimRejectedBankMessages caps table at 200 rows, keeping the newest`() = runTest {
        // 205 صف، توقيت متزايد — نتوقع الأقدم ٥ يتشالوا والأحدث ٢٠٠ يفضلوا
        repeat(205) { i ->
            dao.insertRejectedBankMessage(
                RejectedBankMessage(
                    reason = "OTP",
                    source = "test",
                    rawText = "message #$i",
                    createdAt = i.toString().padStart(10, '0')
                )
            )
        }
        dao.trimRejectedBankMessages()

        val remaining = dao.getRejectedBankMessages()
        assertEquals(200, remaining.size)
        // الأحدث (أعلى createdAt) لازم يكون موجود، الأقدم (٠٠٠..٠٠٠٤) لازم يكون اتشال
        assertTrue(remaining.any { it.rawText == "message #204" })
        assertTrue(remaining.none { it.rawText == "message #0" })
        assertTrue(remaining.none { it.rawText == "message #4" })
    }

    @Test
    fun `insertRejectedBankMessage below cap keeps everything`() = runTest {
        repeat(5) { i ->
            dao.insertRejectedBankMessage(
                RejectedBankMessage(reason = "DECLINED", source = "test", rawText = "msg $i", createdAt = i.toString())
            )
        }
        dao.trimRejectedBankMessages()
        assertEquals(5, dao.getRejectedBankMessages().size)
    }
}
