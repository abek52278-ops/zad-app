package com.example.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SessionHelper {
    suspend fun saveSession(context: Context) = withContext(Dispatchers.IO) {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
        if (session != null) {
            prefs.edit()
                .putString("access_token", session.accessToken)
                .putString("refresh_token", session.refreshToken)
                .apply()
        } else {
            prefs.edit().clear().apply()
        }
    }

    suspend fun loadSession(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        if (accessToken != null && refreshToken != null) {
            try {
                SupabaseRepo.client.auth.importAuthToken(accessToken, refreshToken)
                Log.d("SessionHelper", "Session restored successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("SessionHelper", "Failed to restore session, but keeping tokens in case of network error.")
            }
        }
    }
}
