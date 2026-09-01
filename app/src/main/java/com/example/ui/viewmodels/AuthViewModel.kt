package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object PasswordResetSent : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signUp(email: String, pass: String, name: String? = null) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // signUp() بيرجع null = نجح، أو رسالة الخطأ الحقيقية — مش Boolean بيتحوّل
            // لنص عام ثابت زي ما كان قبل كده. رسالة عامة زي "Sign up failed" مش هتظهر
            // إلا لو الاستثناء نفسه من غير .message خالص.
            val error = SupabaseRepo.signUp(email, pass, name)
            if (error == null) {
                com.example.data.CurrentUser.cache(getApplication(), SupabaseRepo.client.auth.currentUserOrNull()?.id)
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(error)
            }
        }
    }

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val error = SupabaseRepo.signIn(email, pass)
            if (error == null) {
                com.example.data.CurrentUser.cache(getApplication(), SupabaseRepo.client.auth.currentUserOrNull()?.id)
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(error)
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = SupabaseRepo.resetPassword(email)
            if (result) {
                _authState.value = AuthState.PasswordResetSent
            } else {
                _authState.value = AuthState.Error("Failed to send reset email. Check if the email is correct.")
            }
        }
    }
    
    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
