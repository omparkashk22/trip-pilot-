package com.example.data.model

data class LocationKeyword(
    val keyword: String,
    val isAccept: Boolean // true = ACCEPT (✓), false = REJECT (✗)
)

data class DriverFilter(
    val minFare: Double = 80.0,
    val maxFare: Double? = null,
    val isUnlimitedMaxFare: Boolean = true,
    val isDistanceFilterEnabled: Boolean = true,
    val pickupDistanceMinKm: Double = 0.0,
    val pickupDistanceMaxKm: Double = 5.0,
    val dropDistanceMinKm: Double = 0.0,
    val dropDistanceMaxKm: Double = 150.0,
    val fareBasis: String = "base_extra", // "base_only", "base_extra"
    val minFarePerKm: Double = 0.0, // optional min rate
    val isLocationFilterEnabled: Boolean = false,
    val locationKeywords: List<LocationKeyword> = emptyList(),
    val allowedRideTypes: Set<String> = emptySet(), // empty means allow all
    val multipleMatchStrategy: String = "first_match" // "first_match", "highest_fare", "highest_per_km", "nearest_pickup"
) {
    fun generateRuleSummary(): String {
        val parts = mutableListOf<String>()
        parts.add("Fare ≥ ₹${minFare.toInt()}")
        if (!isUnlimitedMaxFare && maxFare != null) {
            parts.add("Fare ≤ ₹${maxFare.toInt()}")
        }
        if (minFarePerKm > 0) {
            parts.add("Rate ≥ ₹${minFarePerKm.toInt()}/km")
        }
        if (isDistanceFilterEnabled) {
            parts.add("Pickup ≤ ${pickupDistanceMaxKm} km")
            parts.add("Drop ≤ ${dropDistanceMaxKm} km")
        }
        if (isLocationFilterEnabled && locationKeywords.isNotEmpty()) {
            val acceptCount = locationKeywords.count { it.isAccept }
            val rejectCount = locationKeywords.count { !it.isAccept }
            parts.add("Loc: +$acceptCount / -$rejectCount")
        }
        return "Accept rides with " + parts.joinToString(", ") + "."
    }
}
