package com.example.ui.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.RideLog
import com.example.data.repository.RideLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

data class TodayRideStats(
    val acceptedCount: Int = 0,
    val skippedCount: Int = 0,
    val totalAcceptedFare: Double = 0.0
)

class HistoryViewModel(
    private val rideLogRepository: RideLogRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val showDebugInfo: StateFlow<Boolean> = preferencesManager.showDebugInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedStatusFilter = MutableStateFlow("ALL") // "ALL", "ACCEPTED", "SKIPPED", "TAP_FAILED"
    val selectedAppFilter = MutableStateFlow("ALL") // "ALL", "bharat_taxi", "rapido"
    val searchQuery = MutableStateFlow("")

    val allLogs: StateFlow<List<RideLog>> = rideLogRepository.allRideLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredLogs: StateFlow<List<RideLog>> = combine(
        allLogs,
        selectedStatusFilter,
        selectedAppFilter,
        searchQuery
    ) { logs, status, app, query ->
        logs.filter { log ->
            val matchStatus = if (status == "ALL") true else log.status.equals(status, ignoreCase = true)
            val matchApp = if (app == "ALL") true else log.appId.equals(app, ignoreCase = true)
            val matchQuery = if (query.isBlank()) true else {
                log.pickupAddress.contains(query, ignoreCase = true) ||
                        log.dropAddress.contains(query, ignoreCase = true) ||
                        log.rideType.contains(query, ignoreCase = true)
            }
            matchStatus && matchApp && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayStats: StateFlow<TodayRideStats> = allLogs.combine(MutableStateFlow(Unit)) { logs, _ ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis

        var accepted = 0
        var skipped = 0
        var totalFare = 0.0

        logs.filter { it.timestamp >= startOfDay }.forEach { log ->
            when (log.status.uppercase()) {
                "ACCEPTED" -> {
                    accepted++
                    totalFare += log.totalFare
                }
                "SKIPPED" -> skipped++
            }
        }
        TodayRideStats(accepted, skipped, totalFare)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayRideStats())

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            rideLogRepository.deleteById(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            rideLogRepository.clearAll()
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            val logs = allLogs.value
            val csvText = rideLogRepository.exportToCsv(logs)
            val file = File(context.cacheDir, "TripPilot_RideHistory_${System.currentTimeMillis()}.csv")
            file.writeText(csvText)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "TripPilot Ride History Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Export Ride History").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                // Fallback direct text share
                val textIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, csvText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try { context.startActivity(textIntent) } catch (_: Exception) {}
            }
        }
    }
}
