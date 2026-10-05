package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ride_logs",
    indices = [
        Index("fingerprint"),
        Index("timestamp")
    ]
)
data class RideLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appId: String,
    val timestamp: Long = System.currentTimeMillis(),
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
    val status: String, // "ACCEPTED", "SKIPPED", "TAP_FAILED"
    val skipReason: String? = null,
    val tapMethod: String? = null, // "NODE_CLICK", "PARENT_CLICK", "GESTURE_TAP"
    val tapLatencyMs: Long = 0,
    val isTest: Boolean = false,
    val rawTextHash: String = "",
    val parseConfidence: String = "HIGH", // "HIGH", "MEDIUM", "LOW"
    val parseReason: String? = null,
    val layoutVariant: String = "LIST", // "LIST", "SINGLE", "RAPIDO"
    val firstSeenAt: Long = timestamp,
    val lastSeenAt: Long = timestamp,
    val seenCount: Int = 1,
    val eventLagMs: Long = 0,
    val parseMs: Long = 0,
    val decideMs: Long = 0,
    val totalToTapMs: Long = 0,
    val outcome: String? = null, // "ACCEPTED", "SKIPPED", "TAP_FAILED", "MISSED"
    val rawCard: String? = null,
    val fingerprint: String = ""
)
