package com.example.domain.engine

data class FilterSnapshot(
    val minFare: Double = 0.0,
    val maxFare: Double? = null,
    val isUnlimitedMaxFare: Boolean = true,
    val fareBasis: String = "base_extra",
    val minFarePerKm: Double = 0.0,
    val isDistanceFilterEnabled: Boolean = false,
    val pickupDistanceMinKm: Double = 0.0,
    val pickupDistanceMaxKm: Double = 50.0,
    val dropDistanceMinKm: Double = 0.0,
    val dropDistanceMaxKm: Double = 150.0,
    val isLocationFilterEnabled: Boolean = false,
    val rejectLocationKeywords: List<String> = emptyList(), // Pre-lowercased & trimmed
    val acceptLocationKeywords: List<String> = emptyList(), // Pre-lowercased & trimmed
    val allowedRideTypes: Set<String> = emptySet(),
    val multipleMatchStrategy: String = "first_match",
    val mode: String = "auto_accept",
    val isBharatTaxiEnabled: Boolean = true,
    val isRapidoEnabled: Boolean = true,
    val resolvedBharatPackage: String? = null,
    val resolvedRapidoPackage: String? = null,
    val engineEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val alertOnAccept: Boolean = true,
    val processTestRequests: Boolean = true,
    val forcedTapMethod: String? = null,
    val turboMode: Boolean = true
)
