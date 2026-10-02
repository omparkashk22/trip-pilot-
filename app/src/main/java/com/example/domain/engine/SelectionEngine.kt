package com.example.domain.engine

import com.example.data.model.DriverFilter
import com.example.data.model.RideOffer

object SelectionEngine {

    fun selectBestOffer(
        candidates: List<RideOffer>,
        filter: DriverFilter
    ): RideOffer? {
        if (candidates.isEmpty()) return null

        val fareBasis = filter.fareBasis

        return when (filter.multipleMatchStrategy) {
            "highest_fare" -> {
                candidates.maxByOrNull { offer ->
                    if (fareBasis == "base_only") offer.baseFare else offer.totalFare
                }
            }
            "highest_per_km" -> {
                candidates.maxByOrNull { offer ->
                    offer.farePerKm(fareBasis)
                }
            }
            "nearest_pickup" -> {
                candidates.minByOrNull { offer ->
                    offer.pickupDistanceKm
                }
            }
            else -> {
                // "first_match" from top
                candidates.firstOrNull()
            }
        }
    }
}
