package com.example.data.repository

import com.example.data.local.RideLogDao
import com.example.data.model.RideLog
import com.example.data.model.RideOffer
import com.example.domain.engine.RideDedupeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideLogRepository(private val rideLogDao: RideLogDao) {

    val dedupeManager = RideDedupeManager(rideLogDao)

    val allRideLogs: Flow<List<RideLog>> = rideLogDao.getAllRideLogs()

    fun getTodayRideLogs(startOfDayMs: Long): Flow<List<RideLog>> {
        return rideLogDao.getTodayRideLogs(startOfDayMs)
    }

    suspend fun recordOffer(
        offer: RideOffer,
        status: String,
        skipReason: String? = null,
        tapMethod: String? = null,
        tapLatencyMs: Long = 0,
        eventLagMs: Long = 0,
        parseMs: Long = 0,
        decideMs: Long = 0,
        totalToTapMs: Long = 0,
        outcome: String? = null,
        now: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        dedupeManager.recordOffer(
            offer = offer,
            status = status,
            skipReason = skipReason,
            tapMethod = tapMethod,
            tapLatencyMs = tapLatencyMs,
            eventLagMs = eventLagMs,
            parseMs = parseMs,
            decideMs = decideMs,
            totalToTapMs = totalToTapMs,
            outcome = outcome,
            now = now
        )
    }

    suspend fun markLatestOfferMissed(appId: String, withinMs: Long = 20_000L): Boolean = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - withinMs
        val latest = rideLogDao.getLatestOfferSince(appId, cutoff)
        if (latest != null) {
            rideLogDao.updateOutcome(latest.id, "MISSED")
            true
        } else false
    }

    suspend fun updateOutcome(id: Long, outcome: String) = withContext(Dispatchers.IO) {
        rideLogDao.updateOutcome(id, outcome)
    }

    suspend fun insertRideLog(rideLog: RideLog): Long = withContext(Dispatchers.IO) {
        if (rideLog.fingerprint.isNotEmpty()) {
            val offer = RideOffer(
                appId = rideLog.appId,
                rideType = rideLog.rideType,
                baseFare = rideLog.baseFare,
                extraFare = rideLog.extraFare,
                totalFare = rideLog.totalFare,
                appFarePerKm = rideLog.appFarePerKm,
                pickupDistanceKm = rideLog.pickupDistanceKm,
                pickupEtaMin = rideLog.pickupEtaMin,
                dropDistanceKm = rideLog.dropDistanceKm,
                dropEtaMin = rideLog.dropEtaMin,
                pickupAddress = rideLog.pickupAddress,
                dropAddress = rideLog.dropAddress,
                dropAddressTruncated = rideLog.dropAddressTruncated,
                isTest = rideLog.isTest,
                rawTextHash = rideLog.rawTextHash,
                parseConfidence = rideLog.parseConfidence,
                parseReason = rideLog.parseReason,
                layoutVariant = rideLog.layoutVariant,
                rawCard = rideLog.rawCard
            )
            dedupeManager.recordOffer(
                offer = offer,
                status = rideLog.status,
                skipReason = rideLog.skipReason,
                tapMethod = rideLog.tapMethod,
                tapLatencyMs = rideLog.tapLatencyMs,
                now = rideLog.timestamp
            )
        } else {
            val id = rideLogDao.insertRideLog(rideLog)
            rideLogDao.pruneOldRawCards()
            id
        }
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        rideLogDao.deleteRideLogById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dedupeManager.clear()
        rideLogDao.clearAll()
    }

    suspend fun updateStatus(id: Long, status: String, tapLatencyMs: Long) = withContext(Dispatchers.IO) {
        rideLogDao.updateStatus(id, status, tapLatencyMs)
    }

    fun exportToCsv(logs: List<RideLog>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("ID,App,Timestamp,Ride Type,Base Fare (INR),Extra Fare (INR),Total Fare (INR),")
        sb.append("Pickup Dist (km),Pickup ETA (min),Drop Dist (km),Drop ETA (min),")
        sb.append("Pickup Address,Destination Address,Destination Truncated,Status,Skip Reason,Tap Method,Tap Latency (ms),Is Test,")
        sb.append("App Fare/km,Layout,Confidence,Seen Count,Raw Card,First Seen,Last Seen,")
        sb.append("Event Lag (ms),Parse (ms),Decide (ms),Total To Tap (ms),Outcome\n")

        for (log in logs) {
            val timeStr = dateFormat.format(Date(log.timestamp))
            val firstSeenStr = dateFormat.format(Date(if (log.firstSeenAt > 0) log.firstSeenAt else log.timestamp))
            val lastSeenStr = dateFormat.format(Date(if (log.lastSeenAt > 0) log.lastSeenAt else log.timestamp))
            val cleanPickup = "\"" + log.pickupAddress.replace("\"", "\"\"") + "\""
            val cleanDrop = "\"" + log.dropAddress.replace("\"", "\"\"") + "\""
            val cleanReason = "\"" + (log.skipReason ?: "").replace("\"", "\"\"") + "\""
            val cleanRawCard = "\"" + (log.rawCard ?: "").replace("\"", "\"\"") + "\""
            sb.append("${log.id},")
            sb.append("${log.appId},")
            sb.append("${timeStr},")
            sb.append("${log.rideType},")
            sb.append("${log.baseFare},")
            sb.append("${log.extraFare},")
            sb.append("${log.totalFare},")
            sb.append("${log.pickupDistanceKm},")
            sb.append("${log.pickupEtaMin ?: ""},")
            sb.append("${log.dropDistanceKm},")
            sb.append("${log.dropEtaMin ?: ""},")
            sb.append("${cleanPickup},")
            sb.append("${cleanDrop},")
            sb.append("${log.dropAddressTruncated},")
            sb.append("${log.status},")
            sb.append("${cleanReason},")
            sb.append("${log.tapMethod ?: ""},")
            sb.append("${log.tapLatencyMs},")
            sb.append("${log.isTest},")
            sb.append("${log.appFarePerKm ?: ""},")
            sb.append("${log.layoutVariant},")
            sb.append("${log.parseConfidence},")
            sb.append("${log.seenCount},")
            sb.append("${cleanRawCard},")
            sb.append("${firstSeenStr},")
            sb.append("${lastSeenStr},")
            sb.append("${log.eventLagMs},")
            sb.append("${log.parseMs},")
            sb.append("${log.decideMs},")
            sb.append("${log.totalToTapMs},")
            sb.append("${log.outcome ?: ""}\n")
        }
        return sb.toString()
    }
}
