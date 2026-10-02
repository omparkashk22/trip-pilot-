package com.example.data.repository

import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(private val firebaseManager: FirebaseManager) {

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    init {
        val uid = firebaseManager.getCurrentUserUid()
        if (uid != null) {
            _currentUser.value = UserProfile(
                uid = uid,
                name = "Driver Partner",
                email = "driver@trippilot.in",
                plan = "Pro Monthly",
                status = "Active",
                expiresAt = System.currentTimeMillis() + (28L * 24 * 3600 * 1000)
            )
        }
    }

    suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        val result = firebaseManager.signIn(email, pass)
        result.onSuccess { _currentUser.value = it }
        return result
    }

    suspend fun signUp(name: String, email: String, pass: String): Result<UserProfile> {
        val result = firebaseManager.signUp(name, email, pass)
        result.onSuccess { _currentUser.value = it }
        return result
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return firebaseManager.sendPasswordReset(email)
    }

    fun signOut() {
        firebaseManager.signOut()
        _currentUser.value = null
    }

    fun enterOfflineMode() {
        _currentUser.value = UserProfile(
            uid = "offline_driver_demo",
            name = "Offline Driver (Trial)",
            email = "offline@trippilot.local",
            plan = "Pro Driver Trial",
            status = "Active",
            expiresAt = System.currentTimeMillis() + (14L * 24 * 3600 * 1000)
        )
    }

    suspend fun refreshProfile(): UserProfile? {
        val user = _currentUser.value ?: return null
        val updated = firebaseManager.fetchUserProfile(user.uid, user.email)
        _currentUser.value = updated
        return updated
    }

    fun updateLocalAvatar(uriString: String) {
        val current = _currentUser.value ?: return
        _currentUser.value = current.copy(avatarUri = uriString)
    }
}
