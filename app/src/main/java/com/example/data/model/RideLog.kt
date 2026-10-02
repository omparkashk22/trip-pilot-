package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ride_logs")
data class RideLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appId: String,
    val timestamp: Long = System.currentTimeMillis(),
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
    val status: String, // "ACCEPTED", "SKIPPED", "TAP_FAILED"
    val skipReason: String? = null,
    val tapMethod: String? = null, // "NODE_CLICK", "PARENT_CLICK", "GESTURE_TAP"
    val tapLatencyMs: Long = 0,
    val isTest: Boolean = false,
    val rawTextHash: String = ""
)
