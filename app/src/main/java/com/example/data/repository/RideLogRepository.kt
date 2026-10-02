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
        // Deduplicate identical offers within 10 seconds
        val tenSecondsAgo = System.currentTimeMillis() - 10_000
        val recent = rideLogDao.getRecentByRawHash(rideLog.rawTextHash, tenSecondsAgo)
        if (recent != null) {
            return@withContext recent.id
        }
        rideLogDao.insertRideLog(rideLog)
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
        sb.append("Pickup Address,Destination Address,Destination Truncated,Status,Skip Reason,Tap Method,Tap Latency (ms),Is Test\n")

        for (log in logs) {
            val timeStr = dateFormat.format(Date(log.timestamp))
            val cleanPickup = "\"" + log.pickupAddress.replace("\"", "\"\"") + "\""
            val cleanDrop = "\"" + log.dropAddress.replace("\"", "\"\"") + "\""
            val cleanReason = "\"" + (log.skipReason ?: "").replace("\"", "\"\"") + "\""
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
            sb.append("${log.isTest}\n")
        }
        return sb.toString()
    }
}
