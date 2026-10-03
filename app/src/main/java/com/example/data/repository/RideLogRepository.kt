package com.example.data.repository

import com.example.data.local.RideLogDao
import com.example.data.model.RideLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideLogRepository(private val rideLogDao: RideLogDao) {

    val allRideLogs: Flow<List<RideLog>> = rideLogDao.getAllRideLogs()

    fun getTodayRideLogs(startOfDayMs: Long): Flow<List<RideLog>> {
        return rideLogDao.getTodayRideLogs(startOfDayMs)
    }

    suspend fun insertRideLog(rideLog: RideLog): Long = withContext(Dispatchers.IO) {
        val threeMinutesAgo = System.currentTimeMillis() - (3 * 60 * 1000L)
        val recentLogs = rideLogDao.getLogsSince(threeMinutesAgo)

        // Find existing match with same fingerprint within 3 minutes that was NOT accepted
        val existing = recentLogs.firstOrNull { it.fingerprint.isNotEmpty() && it.fingerprint == rideLog.fingerprint }

        if (existing != null && !existing.status.equals("ACCEPTED", ignoreCase = true)) {
            // Update row instead of inserting
            val oldConfScore = confidenceScore(existing.parseConfidence)
            val newConfScore = confidenceScore(rideLog.parseConfidence)
            val updateFares = newConfScore > oldConfScore

            val updated = existing.copy(
                seenCount = existing.seenCount + 1,
                timestamp = System.currentTimeMillis(),
                status = if (rideLog.status.equals("ACCEPTED", ignoreCase = true)) rideLog.status else existing.status,
                skipReason = if (rideLog.status.equals("ACCEPTED", ignoreCase = true)) null else (rideLog.skipReason ?: existing.skipReason),
                tapMethod = rideLog.tapMethod ?: existing.tapMethod,
                tapLatencyMs = if (rideLog.tapLatencyMs > 0) rideLog.tapLatencyMs else existing.tapLatencyMs,
                baseFare = if (updateFares) rideLog.baseFare else existing.baseFare,
                extraFare = if (updateFares) rideLog.extraFare else existing.extraFare,
                totalFare = if (updateFares) rideLog.totalFare else existing.totalFare,
                appFarePerKm = if (updateFares) rideLog.appFarePerKm else existing.appFarePerKm,
                rawCard = if (updateFares) (rideLog.rawCard ?: existing.rawCard) else existing.rawCard,
                parseConfidence = if (updateFares) rideLog.parseConfidence else existing.parseConfidence,
                parseReason = if (updateFares) rideLog.parseReason else existing.parseReason,
                layoutVariant = if (updateFares) rideLog.layoutVariant else existing.layoutVariant
            )
            rideLogDao.updateRideLog(updated)
            rideLogDao.pruneOldRawCards()
            return@withContext updated.id
        }

        // Insert new row
        val id = rideLogDao.insertRideLog(rideLog)
        rideLogDao.pruneOldRawCards()
        id
    }

    private fun confidenceScore(conf: String): Int = when (conf.uppercase()) {
        "HIGH" -> 3
        "MEDIUM" -> 2
        "LOW" -> 1
        else -> 0
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        rideLogDao.deleteRideLogById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
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
        sb.append("App Fare/km,Layout,Confidence,Seen Count,Raw Card\n")

        for (log in logs) {
            val timeStr = dateFormat.format(Date(log.timestamp))
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
            sb.append("${cleanRawCard}\n")
        }
        return sb.toString()
    }
}
