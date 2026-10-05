package com.example.domain.engine

object RideTypeSanitizer {

    val BHARAT_TAXI_KNOWN = listOf(
        "Economy Intercity",
        "Premium Intercity",
        "Cab Economy",
        "Cab Premium"
    )

    val RAPIDO_KNOWN = listOf(
        "Cab Economy",
        "Cab Premium",
        "Bike Boost",
        "Auto Boost",
        "Cab Boost",
        "Cab XL",
        "Bike",
        "Auto",
        "Cab"
    )

    private val NOTIFICATION_REGEX = Regex("(?i)notification\\s*:?\\s*$")

    private val REJECTED_APP_NAMES = listOf(
        "YouTube",
        "Rapido Captain",
        "Bharat Taxi",
        "TripPilot",
        "WhatsApp",
        "Google",
        "System UI",
        "Android System"
    )

    fun isRejectedCandidate(text: String, installedAppNames: List<String> = emptyList()): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.length > 24) return true
        if (trimmed.contains("₹")) return true
        if (trimmed.any { it.isDigit() }) return true
        if (NOTIFICATION_REGEX.containsMatchIn(trimmed)) return true
        if (trimmed.contains("Notification:", ignoreCase = true)) return true
        if (trimmed.contains("Notification", ignoreCase = true)) return true

        for (appName in REJECTED_APP_NAMES) {
            if (trimmed.contains(appName, ignoreCase = true)) return true
        }
        for (installed in installedAppNames) {
            if (installed.isNotBlank() && trimmed.contains(installed, ignoreCase = true)) return true
        }

        return false
    }

    /**
     * Sanitizes a candidate string into a valid ride type or "Unknown".
     *
     * @param candidate Raw text from node
     * @param appId "bharat_taxi", "rapido" or empty
     * @param isAboveFareNode true if the node is physically located above the fare node in the same card
     */
    fun sanitize(
        candidate: String?,
        appId: String = "",
        isAboveFareNode: Boolean = false,
        installedAppNames: List<String> = emptyList()
    ): String {
        if (candidate.isNullOrBlank()) return "Unknown"
        val trimmed = candidate.trim()

        if (isRejectedCandidate(trimmed, installedAppNames)) {
            return "Unknown"
        }

        val knownList = when (appId) {
            "bharat_taxi" -> BHARAT_TAXI_KNOWN
            "rapido" -> RAPIDO_KNOWN
            else -> (BHARAT_TAXI_KNOWN + RAPIDO_KNOWN).distinct()
        }

        // (a) known list in the registry (longest match wins; case-insensitive; trimmed)
        val sortedKnown = knownList.sortedByDescending { it.length }
        for (known in sortedKnown) {
            if (trimmed.equals(known, ignoreCase = true) || trimmed.contains(known, ignoreCase = true)) {
                return known
            }
        }

        // (b) short text (<= 24 chars) located above the fare node in the same card
        if (isAboveFareNode && trimmed.length <= 24 && !isRejectedCandidate(trimmed, installedAppNames)) {
            return trimmed
        }

        return "Unknown"
    }

    /**
     * Validates whether a ride type is legitimate for Allowed Ride Types in filters.
     */
    fun isValidAllowedRideType(type: String): Boolean {
        val trimmed = type.trim()
        if (trimmed.equals("Unknown", ignoreCase = true)) return false
        if (isRejectedCandidate(trimmed)) return false
        val allKnown = (BHARAT_TAXI_KNOWN + RAPIDO_KNOWN).distinct()
        if (allKnown.any { it.equals(trimmed, ignoreCase = true) }) return true
        return trimmed.length <= 24
    }
}
