package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.ZadDatabase

/** Removes private, account-scoped state before another user can use this device. */
object LocalAccountData {
    private const val TAG = "LocalAccountData"

    suspend fun clear(context: Context) {
        val appContext = context.applicationContext
        PharmacyReminderScheduler.cancelAll(appContext)
        ZadDatabase.getDatabase(appContext).zadDao().clearAccountData()
        AiLocalCache.clear(appContext)
        KidsModePin.clearAccountState(appContext)
        CurrentUser.clear(appContext)
        Log.d(TAG, "Account-scoped Room, alarm, identity, and AI state cleared")
    }
}
