package com.example.domain.engine

import com.example.data.local.RideLogDao
import com.example.data.model.RideLog
import com.example.data.model.RideOffer
import java.util.LinkedHashMap

data class DedupeSighting(
    val rowId: Long,
    var status: String,
    val firstSeenAt: Long,
    var lastSeenAt: Long,
    var seenCount: Int,
    var parseConfidence: String,
    var totalFare: Double
)

class RideDedupeManager(
    private val rideLogDao: RideLogDao,
    private val windowMs: Long = 3 * 60 * 1000L
) {
    // In-memory LRU map: fingerprint -> DedupeSighting
    private val lruMap = object : LinkedHashMap<String, DedupeSighting>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DedupeSighting>?): Boolean {
            return size > 300
        }
    }

    @Synchronized
    fun getSighting(fingerprint: String, now: Long = System.currentTimeMillis()): DedupeSighting? {
        val entry = lruMap[fingerprint] ?: return null
        if (now - entry.lastSeenAt > windowMs) {
            lruMap.remove(fingerprint)
            return null
        }
        return entry
    }

    @Synchronized
    fun isAcceptedWithinWindow(fingerprint: String, now: Long = System.currentTimeMillis()): Boolean {
        val sighting = getSighting(fingerprint, now)
        return sighting != null && sighting.status.equals("ACCEPTED", ignoreCase = true)
    }

    @Synchronized
    fun extendWindowOnly(fingerprint: String, now: Long = System.currentTimeMillis()) {
        val sighting = getSighting(fingerprint, now)
        if (sighting != null) {
            sighting.lastSeenAt = now
        }
    }

    suspend fun recordOffer(
        offer: RideOffer,
        status: String,
        skipReason: String?,
        tapMethod: String? = null,
        tapLatencyMs: Long = 0,
        eventLagMs: Long = 0,
        parseMs: Long = 0,
        decideMs: Long = 0,
        totalToTapMs: Long = 0,
        outcome: String? = null,
        now: Long = System.currentTimeMillis()
    ): Long {
        val fp = offer.fingerprint
        val existing = synchronized(this) { getSighting(fp, now) }

        if (existing != null) {
            if (existing.status.equals("ACCEPTED", ignoreCase = true)) {
                // If the row is ACCEPTED: never overwrite it and never tap the same fingerprint again within 3 minutes.
                // Each new sighting extends the window to 3 minutes after the last sighting.
                synchronized(this) {
                    existing.lastSeenAt = now
                }
                return existing.rowId
            }

            // Fingerprint is in window and not ACCEPTED: update that row
            val newSeenCount = existing.seenCount + 1
            val oldConfScore = confidenceScore(existing.parseConfidence)
            val newConfScore = confidenceScore(offer.parseConfidence)
            val decisionChanged = !existing.status.equals(status, ignoreCase = true)
            val shouldUpdateDecisionAndFare = decisionChanged || (newConfScore >= oldConfScore)

            val updatedRowId = existing.rowId

            // Update database row
            val currentLog = rideLogDao.getById(updatedRowId)
            if (currentLog != null) {
                val updatedLog = currentLog.copy(
                    seenCount = newSeenCount,
                    lastSeenAt = now,
                    status = if (shouldUpdateDecisionAndFare) status else currentLog.status,
                    skipReason = if (shouldUpdateDecisionAndFare) skipReason else currentLog.skipReason,
                    baseFare = if (shouldUpdateDecisionAndFare) offer.baseFare else currentLog.baseFare,
                    extraFare = if (shouldUpdateDecisionAndFare) offer.extraFare else currentLog.extraFare,
                    totalFare = if (shouldUpdateDecisionAndFare) offer.totalFare else currentLog.totalFare,
                    appFarePerKm = if (shouldUpdateDecisionAndFare) offer.appFarePerKm else currentLog.appFarePerKm,
                    tapMethod = if (status.equals("ACCEPTED", ignoreCase = true)) (tapMethod ?: currentLog.tapMethod) else currentLog.tapMethod,
                    tapLatencyMs = if (status.equals("ACCEPTED", ignoreCase = true) && tapLatencyMs > 0) tapLatencyMs else currentLog.tapLatencyMs,
                    eventLagMs = if (eventLagMs > 0) eventLagMs else currentLog.eventLagMs,
                    parseMs = if (parseMs > 0) parseMs else currentLog.parseMs,
                    decideMs = if (decideMs > 0) decideMs else currentLog.decideMs,
                    totalToTapMs = if (totalToTapMs > 0) totalToTapMs else currentLog.totalToTapMs,
                    outcome = outcome ?: currentLog.outcome,
                    parseConfidence = if (shouldUpdateDecisionAndFare) offer.parseConfidence else currentLog.parseConfidence,
                    parseReason = if (shouldUpdateDecisionAndFare) offer.parseReason else currentLog.parseReason,
                    rawCard = if (shouldUpdateDecisionAndFare) (offer.rawCard ?: currentLog.rawCard) else currentLog.rawCard
                )
                rideLogDao.updateRideLog(updatedLog)
            }

            synchronized(this) {
                existing.lastSeenAt = now
                existing.seenCount = newSeenCount
                if (shouldUpdateDecisionAndFare) {
                    existing.status = status
                    existing.parseConfidence = offer.parseConfidence
                    existing.totalFare = offer.totalFare
                }
            }
            return updatedRowId
        } else {
            // Insert new row
            val log = RideLog(
                appId = offer.appId,
                timestamp = now,
                firstSeenAt = now,
                lastSeenAt = now,
                seenCount = 1,
                rideType = offer.rideType,
                baseFare = offer.baseFare,
                extraFare = offer.extraFare,
                totalFare = offer.totalFare,
                appFarePerKm = offer.appFarePerKm,
                pickupDistanceKm = offer.pickupDistanceKm,
                pickupEtaMin = offer.pickupEtaMin,
                dropDistanceKm = offer.dropDistanceKm,
                dropEtaMin = offer.dropEtaMin,
                pickupAddress = offer.pickupAddress,
                dropAddress = offer.dropAddress,
                dropAddressTruncated = offer.dropAddressTruncated,
                status = status,
                skipReason = skipReason,
                tapMethod = tapMethod,
                tapLatencyMs = tapLatencyMs,
                eventLagMs = eventLagMs,
                parseMs = parseMs,
                decideMs = decideMs,
                totalToTapMs = totalToTapMs,
                outcome = outcome,
                isTest = offer.isTest,
                rawTextHash = offer.rawTextHash,
                parseConfidence = offer.parseConfidence,
                parseReason = offer.parseReason,
                layoutVariant = offer.layoutVariant,
                rawCard = offer.rawCard,
                fingerprint = fp
            )
            val newId = rideLogDao.insertRideLog(log)
            rideLogDao.pruneOldRawCards()

            synchronized(this) {
                lruMap[fp] = DedupeSighting(
                    rowId = newId,
                    status = status,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    seenCount = 1,
                    parseConfidence = offer.parseConfidence,
                    totalFare = offer.totalFare
                )
            }
            return newId
        }
    }

    private fun confidenceScore(conf: String): Int = when (conf.uppercase()) {
        "HIGH" -> 3
        "MEDIUM" -> 2
        "LOW" -> 1
        else -> 0
    }

    @Synchronized
    fun clear() {
        lruMap.clear()
    }
}
