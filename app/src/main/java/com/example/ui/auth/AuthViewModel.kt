package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val agreeTerms: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser

    fun setMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            errorMessage = null,
            successMessage = null
        )
    }

    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(name = value, errorMessage = null) }
    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null) }
    fun onAgreeTermsChange(value: Boolean) { _uiState.value = _uiState.value.copy(agreeTerms = value, errorMessage = null) }
    fun togglePasswordVisibility() { _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible) }

    fun submit() {
        val s = _uiState.value
        if (s.email.isBlank() || !s.email.contains("@")) {
            _uiState.value = s.copy(errorMessage = "Please enter a valid email address.")
            return
        }

        when (s.mode) {
            AuthMode.SIGN_IN -> {
                if (s.password.length < 6) {
                    _uiState.value = s.copy(errorMessage = "Password must be at least 6 characters.")
                    return
                }
                signIn(s.email.trim(), s.password)
            }
            AuthMode.SIGN_UP -> {
                if (s.name.isBlank()) {
                    _uiState.value = s.copy(errorMessage = "Please enter your name.")
                    return
                }
                if (s.password.length < 6) {
                    _uiState.value = s.copy(errorMessage = "Password must be at least 6 characters.")
                    return
                }
                if (s.password != s.confirmPassword) {
                    _uiState.value = s.copy(errorMessage = "Passwords do not match.")
                    return
                }
                if (!s.agreeTerms) {
                    _uiState.value = s.copy(errorMessage = "Please agree to the Terms & Privacy Policy.")
                    return
                }
                signUp(s.name.trim(), s.email.trim(), s.password)
            }
            AuthMode.FORGOT_PASSWORD -> {
                forgotPassword(s.email.trim())
            }
        }
    }

    private fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = authRepository.signIn(email, pass)
            _uiState.value = _uiState.value.copy(isLoading = false)
            res.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Sign in failed.")
            }
        }
    }

    private fun signUp(name: String, email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = authRepository.signUp(name, email, pass)
            _uiState.value = _uiState.value.copy(isLoading = false)
            res.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Registration failed.")
            }
        }
    }

    private fun forgotPassword(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = authRepository.sendPasswordReset(email)
            _uiState.value = _uiState.value.copy(isLoading = false)
            res.onSuccess {
                _uiState.value = _uiState.value.copy(successMessage = "Password reset instructions sent to $email")
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Failed to send reset link.")
            }
        }
    }

    fun continueInOfflineMode() {
        authRepository.enterOfflineMode()
    }
}
