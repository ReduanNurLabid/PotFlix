package com.potflix.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potflix.data.remote.FirebaseSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseSyncManager: FirebaseSyncManager
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isLoginSuccess = MutableStateFlow(false)
    val isLoginSuccess: StateFlow<Boolean> = _isLoginSuccess

    fun onEmailChanged(email: String) {
        _email.value = email
    }

    fun onPasswordChanged(password: String) {
        _password.value = password
    }

    fun login() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _errorMessage.value = "Email and password cannot be empty."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = firebaseSyncManager.login(_email.value.trim(), _password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _isLoginSuccess.value = true
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Login failed."
            }
        }
    }

    fun signUp() {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _errorMessage.value = "Email and password cannot be empty."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = firebaseSyncManager.signUp(_email.value.trim(), _password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _isLoginSuccess.value = true
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Signup failed."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
