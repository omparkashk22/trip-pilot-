package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.RideLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RideLogDao {
    @Query("SELECT * FROM ride_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllRideLogs(): Flow<List<RideLog>>

    @Query("SELECT * FROM ride_logs WHERE timestamp >= :startOfDayMs ORDER BY timestamp DESC")
    fun getTodayRideLogs(startOfDayMs: Long): Flow<List<RideLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRideLog(rideLog: RideLog): Long

    @Query("DELETE FROM ride_logs WHERE id = :id")
    suspend fun deleteRideLogById(id: Long)

    @Query("DELETE FROM ride_logs")
    suspend fun clearAll()

    @Query("SELECT * FROM ride_logs WHERE rawTextHash = :rawHash AND timestamp >= :sinceTimestamp LIMIT 1")
    suspend fun getRecentByRawHash(rawHash: String, sinceTimestamp: Long): RideLog?

    @Query("UPDATE ride_logs SET status = :status, tapLatencyMs = :tapLatencyMs WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, tapLatencyMs: Long)

    @androidx.room.Update
    suspend fun updateRideLog(rideLog: RideLog)

    @Query("SELECT * FROM ride_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getLogsSince(sinceTimestamp: Long): List<RideLog>

    @Query("UPDATE ride_logs SET rawCard = NULL WHERE id NOT IN (SELECT id FROM ride_logs ORDER BY timestamp DESC LIMIT 100)")
    suspend fun pruneOldRawCards()
}
