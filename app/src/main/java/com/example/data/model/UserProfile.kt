package com.example.data.model

data class UserProfile(
    val uid: String = "",
    val name: String = "Driver Partner",
    val email: String = "driver@trippilot.in",
    val avatarUri: String? = null,
    val plan: String = "Monthly Plan", // "Trial", "Monthly", "Yearly"
    val status: String = "Active", // "Active", "Expired"
    val expiresAt: Long = System.currentTimeMillis() + (14L * 24 * 3600 * 1000) // Default 14 days active
) {
    val isExpired: Boolean
        get() = status.equals("Expired", ignoreCase = true) || System.currentTimeMillis() > expiresAt
}

data class StrategyStats(
    val strategyName: String,
    val attempts: Int = 0,
    val verifiedSuccesses: Int = 0
) {
    val successRate: Double
        get() = if (attempts > 0) verifiedSuccesses.toDouble() / attempts else 0.0
}
