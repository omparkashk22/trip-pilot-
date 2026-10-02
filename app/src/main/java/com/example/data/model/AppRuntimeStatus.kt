package com.example.data.model

enum class AppRuntimeState {
    NOT_INSTALLED,
    PAUSED,
    IDLE,
    FOREGROUND,
    OFFER_DETECTED,
    NOT_RECOGNIZED
}

data class AppRuntimeStatus(
    val appId: String,
    val displayName: String,
    val state: AppRuntimeState = AppRuntimeState.NOT_INSTALLED,
    val resolvedPackage: String? = null,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val lastEventAt: Long? = null,
    val eventsLast60s: Int = 0,
    val lastOfferParsedAt: Long? = null,
    val lastDecision: String? = null // "Accepted", "Skipped", or null
)
