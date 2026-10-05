package com.example.data.model

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class RideOffer(
    val appId: String,
    val rideType: String,
    val baseFare: Double,
    val extraFare: Double = 0.0,
    val totalFare: Double = baseFare + extraFare,
    val appFarePerKm: Double? = null,
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
    val candidateIndex: Int = 0,
    val parseConfidence: String = "HIGH", // "HIGH", "MEDIUM", "LOW"
    val parseReason: String? = null,
    val layoutVariant: String = "LIST", // "LIST", "SINGLE", "RAPIDO"
    val rawCard: String? = null
) {
    companion object {
        fun normalizeForFingerprint(text: String): String {
            return text.lowercase()
                .replace("\\p{Punct}".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }

    val fingerprint: String
        get() {
            val normPickup = normalizeForFingerprint(pickupAddress)
            val normDrop = normalizeForFingerprint(dropAddress)
            val pDist = "%.1f".format(java.util.Locale.US, pickupDistanceKm)
            val dDist = "%.1f".format(java.util.Locale.US, dropDistanceKm)
            return "${appId}_${normPickup}_${normDrop}_${pDist}_${dDist}"
        }

    fun farePerKm(fareBasis: String = "base_extra"): Double {
        val fare = if (fareBasis == "base_only") baseFare else totalFare
        return if (dropDistanceKm > 0) fare / dropDistanceKm else 0.0
    }
}
