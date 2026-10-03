package com.example.domain.engine

import com.example.data.model.DriverFilter
import com.example.data.model.RideOffer

data class FilterEvaluationResult(
    val isMatched: Boolean,
    val skipReason: String? = null
)

object FilterEngine {

    private val WHITESPACE_REGEX = "\\s+".toRegex()

    fun evaluate(
        offer: RideOffer,
        filter: DriverFilter,
        isAppEnabled: Boolean = true
    ): FilterEvaluationResult {
        // 1. App Enabled
        if (!isAppEnabled) {
            return FilterEvaluationResult(false, "App disabled in settings")
        }

        // 2. Allowed Ride Types
        if (filter.allowedRideTypes.isNotEmpty()) {
            val offerTypeClean = offer.rideType.trim().replace(WHITESPACE_REGEX, " ")
            val matchesType = filter.allowedRideTypes.any { allowed ->
                val allowedClean = allowed.trim().replace(WHITESPACE_REGEX, " ")
                offerTypeClean.equals(allowedClean, ignoreCase = true) ||
                        offerTypeClean.contains(allowedClean, ignoreCase = true) ||
                        allowedClean.contains(offerTypeClean, ignoreCase = true)
            }
            if (!matchesType) {
                return FilterEvaluationResult(false, "Ride type not allowed")
            }
        }

        // Calculate fare basis
        val evaluatedFare = if (filter.fareBasis == "base_only") offer.baseFare else offer.totalFare

        // 3. Fare Range
        if (evaluatedFare < filter.minFare) {
            return FilterEvaluationResult(false, "Fare below minimum (₹${evaluatedFare.toInt()} < ₹${filter.minFare.toInt()})")
        }
        if (!filter.isUnlimitedMaxFare && filter.maxFare != null && evaluatedFare > filter.maxFare) {
            return FilterEvaluationResult(false, "Fare above maximum (₹${evaluatedFare.toInt()} > ₹${filter.maxFare.toInt()})")
        }

        // 4. Fare per km (fare / drop distance)
        if (filter.minFarePerKm > 0) {
            val rate = if (offer.dropDistanceKm > 0) evaluatedFare / offer.dropDistanceKm else 0.0
            if (rate < filter.minFarePerKm) {
                return FilterEvaluationResult(false, "Fare per km too low (₹${"%.1f".format(rate)}/km < ₹${filter.minFarePerKm.toInt()}/km)")
            }
        }

        // 5. Pickup Distance
        if (filter.isDistanceFilterEnabled) {
            if (offer.pickupDistanceKm < filter.pickupDistanceMinKm) {
                return FilterEvaluationResult(false, "Pickup below min distance (${offer.pickupDistanceKm} km < ${filter.pickupDistanceMinKm} km)")
            }
            if (offer.pickupDistanceKm > filter.pickupDistanceMaxKm) {
                return FilterEvaluationResult(false, "Pickup outside range (${offer.pickupDistanceKm} km > ${filter.pickupDistanceMaxKm} km)")
            }
        }

        // 6. Drop Distance
        if (filter.isDistanceFilterEnabled) {
            if (offer.dropDistanceKm < filter.dropDistanceMinKm) {
                return FilterEvaluationResult(false, "Drop below min distance (${offer.dropDistanceKm} km < ${filter.dropDistanceMinKm} km)")
            }
            if (offer.dropDistanceKm > filter.dropDistanceMaxKm) {
                return FilterEvaluationResult(false, "Drop outside range (${offer.dropDistanceKm} km > ${filter.dropDistanceMaxKm} km)")
            }
        }

        // 7. Location Keywords (substring match against drop address)
        if (filter.isLocationFilterEnabled && filter.locationKeywords.isNotEmpty()) {
            val dropAddressLower = offer.dropAddress.lowercase()

            // Check any REJECT keyword match first
            val rejectKeywords = filter.locationKeywords.filter { !it.isAccept }
            for (rk in rejectKeywords) {
                if (dropAddressLower.contains(rk.keyword.lowercase())) {
                    return FilterEvaluationResult(false, "Location keyword rejected ('${rk.keyword}')")
                }
            }

            // Check ACCEPT keywords if any exist
            val acceptKeywords = filter.locationKeywords.filter { it.isAccept }
            if (acceptKeywords.isNotEmpty()) {
                val hasMatch = acceptKeywords.any { dropAddressLower.contains(it.keyword.lowercase()) }
                if (!hasMatch) {
                    return FilterEvaluationResult(false, "Location not in accepted keywords")
                }
            }
        }

        return FilterEvaluationResult(true, null)
    }
}
