package com.settleup.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.settleup.android.data.remote.ApiService
import com.settleup.android.data.remote.LoginRequest
import com.settleup.android.data.remote.RegisterRequest
import com.settleup.android.data.remote.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState { 
    data object Idle: AuthUiState 
    data object Loading: AuthUiState 
    data object Success: AuthUiState 
    data class Error(val msg: String): AuthUiState 
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenProvider: TokenProvider
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state = _state.asStateFlow()

    fun login(email: String, password: String) = viewModelScope.launch {
        _state.value = AuthUiState.Loading
        try {
            val res = api.login(LoginRequest(email, password))
            tokenProvider.save(res.token, res.refreshToken, res.userId)
            _state.value = AuthUiState.Success
        } catch (e: Exception) { _state.value = AuthUiState.Error(e.message ?: "Login failed") }
    }
    fun register(name: String, email: String, password: String) = viewModelScope.launch {
        _state.value = AuthUiState.Loading
        try {
            val res = api.register(RegisterRequest(name, email, password))
            tokenProvider.save(res.token, res.refreshToken, res.userId)
            _state.value = AuthUiState.Success
        } catch (e: Exception) { _state.value = AuthUiState.Error(e.message ?: "Registration failed") }
    }
    fun reset() { _state.value = AuthUiState.Idle }
}
