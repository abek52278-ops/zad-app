package com.example.voice

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.launch
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * الجلسة الحية بقت object، والتحويل ده معاه فخ مابيبانش في أي بناء ناجح.
 *
 * لما كانت class بتتعمل بـ `remember` جوه الشيت، كل فتحة كانت بتجيب نسخة جديدة
 * بـ scope جديد، فـ `release()` وهي بتنادي `scope.cancel()` مكانتش بتأذي حد.
 * كـ object، نفس السطر بيلغي الـscope الوحيد **للأبد**: CoroutineScope متلغي
 * مايترجعش، وأي `launch` بعد كده بيعدّي بلا أثر — يعني الفتحة التانية للشيت تبقى
 * صامتة تمامًا، من غير كراش ولا رسالة خطأ ولا أي أثر في اللوج.
 *
 * التستات دي بتمسك السيناريو ده بالظبط: افتح، اقفل، افتح تاني.
 */
@RunWith(AndroidJUnit4::class)
class LiveVoiceSessionLifecycleTest {

    @Before
    fun setUp() {
        ZadLiveVoiceSession.init(ApplicationProvider.getApplicationContext<Application>())
    }

    /** بيثبت إن الـscope مش بس "موجود" — بينفّذ شغل فعلاً. */
    private fun scopeActuallyRunsWork(): Boolean {
        val latch = CountDownLatch(1)
        ZadLiveVoiceSession.scope.launch { latch.countDown() }
        return latch.await(3, TimeUnit.SECONDS)
    }

    /**
     * السيناريو اللي البناء الأخضر مابيمسكوش: فتحة → قفلة → فتحة.
     * بالكود القديم (`private val scope = CoroutineScope(...)`) التأكيد التاني بيفشل.
     */
    @Test
    fun aSecondSessionStillRunsAfterTheFirstOneWasReleased() {
        assertTrue("الفتحة الأولى لازم تشتغل", scopeActuallyRunsWork())

        ZadLiveVoiceSession.release()

        assertTrue("الفتحة التانية بعد release لازم تشتغل برضه", scopeActuallyRunsWork())
    }

    /** قفل الشيت بينادي stop()، ودي المفروض ماتمسّش الـscope أصلاً. */
    @Test
    fun stopLeavesTheScopeUsable() {
        val before = ZadLiveVoiceSession.scope
        ZadLiveVoiceSession.stop()
        assertSame("stop مالهاش دعوة بالـscope", before, ZadLiveVoiceSession.scope)
        assertTrue(scopeActuallyRunsWork())
    }

    /** init بيتنادى من كل فتحة للشيت — لازم تكون آمنة للتكرار. */
    @Test
    fun initIsIdempotent() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        repeat(3) { ZadLiveVoiceSession.init(ctx) }
        assertTrue(scopeActuallyRunsWork())
    }
}
