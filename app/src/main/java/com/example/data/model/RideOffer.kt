package com.example.data.model

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class RideOffer(
    val appId: String,
    val rideType: String,
    val baseFare: Double,
    val extraFare: Double = 0.0,
    val totalFare: Double = baseFare + extraFare,
    val pickupDistanceKm: Double,
    val pickupEtaMin: Int? = null,
    val dropDistanceKm: Double,
    val dropEtaMin: Int? = null,
    val pickupAddress: String,
    val dropAddress: String,
    val dropAddressTruncated: Boolean = false,
    val isTest: Boolean = false,
    val rawTextHash: String = "",
    val acceptNodeBounds: Rect? = null,
    val acceptNode: AccessibilityNodeInfo? = null,
    val isActionable: Boolean = true,
    val candidateIndex: Int = 0
) {
    val fingerprint: String
        get() = "${rideType}_${baseFare}_${extraFare}_${pickupAddress.take(20)}_${dropAddress.take(20)}_${pickupDistanceKm}"

    fun farePerKm(fareBasis: String = "base_extra"): Double {
        val fare = if (fareBasis == "base_only") baseFare else totalFare
        return if (dropDistanceKm > 0) fare / dropDistanceKm else 0.0
    }
}
