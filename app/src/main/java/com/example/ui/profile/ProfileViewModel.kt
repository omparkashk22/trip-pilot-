package com.example.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CountdownTime(
    val days: Long = 0,
    val hours: Long = 0,
    val minutes: Long = 0,
    val seconds: Long = 0,
    val isExpired: Boolean = false,
    val isUnderSevenDays: Boolean = false
)

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _countdown = MutableStateFlow(CountdownTime())
    val countdown: StateFlow<CountdownTime> = _countdown.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Ticking countdown timer updating every second
        viewModelScope.launch {
            while (true) {
                val profile = currentUser.value
                if (profile != null) {
                    val remainingMs = profile.expiresAt - System.currentTimeMillis()
                    if (remainingMs <= 0 || profile.status.equals("Expired", ignoreCase = true)) {
                        _countdown.value = CountdownTime(0, 0, 0, 0, isExpired = true, isUnderSevenDays = true)
                    } else {
                        val secondsTotal = remainingMs / 1000
                        val days = secondsTotal / 86400
                        val hours = (secondsTotal % 86400) / 3600
                        val minutes = (secondsTotal % 3600) / 60
                        val seconds = secondsTotal % 60
                        _countdown.value = CountdownTime(
                            days = days,
                            hours = hours,
                            minutes = minutes,
                            seconds = seconds,
                            isExpired = false,
                            isUnderSevenDays = days < 7
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        authRepository.updateLocalAvatar(uri.toString())
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun refreshProfile() {
        viewModelScope.launch {
            authRepository.refreshProfile()
        }
    }

    fun changeEmail(newEmail: String) {
        _statusMessage.value = "Email change request submitted for $newEmail"
    }

    fun changePassword() {
        viewModelScope.launch {
            val email = currentUser.value?.email ?: return@launch
            authRepository.sendPasswordReset(email)
            _statusMessage.value = "Password reset instructions sent to $email"
        }
    }
}
