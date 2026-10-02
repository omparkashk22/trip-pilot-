package com.example.data.remote

import android.content.Context
import com.example.data.model.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseManager(private val context: Context) {

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private val auth: FirebaseAuth?
        get() = if (isFirebaseAvailable) {
            try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
        } else null

    private val firestore: FirebaseFirestore?
        get() = if (isFirebaseAvailable) {
            try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
        } else null

    fun getCurrentUserUid(): String? = auth?.currentUser?.uid

    fun isUserSignedIn(): Boolean = auth?.currentUser != null

    suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase not configured. Please use Offline Mode or configure google-services."))
        return try {
            val authResult = authInstance.signInWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: return Result.failure(Exception("Authentication returned no user"))
            val profile = fetchUserProfile(user.uid, user.email ?: email)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(name: String, email: String, pass: String): Result<UserProfile> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase not configured. Please use Offline Mode or configure google-services."))
        return try {
            val authResult = authInstance.createUserWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: return Result.failure(Exception("Registration returned no user"))
            val profile = UserProfile(
                uid = user.uid,
                name = name,
                email = email,
                plan = "Trial (14 Days)",
                status = "Active",
                expiresAt = System.currentTimeMillis() + (14L * 24 * 3600 * 1000)
            )
            saveUserProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase not configured."))
        return try {
            authInstance.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth?.signOut()
    }

    suspend fun fetchUserProfile(uid: String, fallbackEmail: String = ""): UserProfile {
        val db = firestore ?: return getLocalDefaultProfile(uid, fallbackEmail)
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                UserProfile(
                    uid = uid,
                    name = doc.getString("name") ?: "Driver Partner",
                    email = doc.getString("email") ?: fallbackEmail,
                    plan = doc.getString("plan") ?: "Trial (14 Days)",
                    status = doc.getString("status") ?: "Active",
                    expiresAt = doc.getLong("expiresAt") ?: (System.currentTimeMillis() + 14L * 24 * 3600 * 1000)
                )
            } else {
                val profile = getLocalDefaultProfile(uid, fallbackEmail)
                saveUserProfile(profile)
                profile
            }
        } catch (_: Exception) {
            getLocalDefaultProfile(uid, fallbackEmail)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val db = firestore ?: return
        try {
            val data = hashMapOf(
                "name" to profile.name,
                "email" to profile.email,
                "plan" to profile.plan,
                "status" to profile.status,
                "expiresAt" to profile.expiresAt
            )
            db.collection("users").document(profile.uid).set(data).await()
        } catch (_: Exception) {}
    }

    private fun getLocalDefaultProfile(uid: String, email: String): UserProfile {
        return UserProfile(
            uid = uid.ifEmpty { "driver_local_01" },
            name = "Driver Partner",
            email = email.ifEmpty { "driver@trippilot.in" },
            plan = "Pro Driver Monthly",
            status = "Active",
            expiresAt = System.currentTimeMillis() + (30L * 24 * 3600 * 1000) // 30 days active
        )
    }
}
