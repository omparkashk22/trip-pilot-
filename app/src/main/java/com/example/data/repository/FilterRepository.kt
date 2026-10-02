package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.model.DriverFilter
import kotlinx.coroutines.flow.Flow

class FilterRepository(private val preferencesManager: PreferencesManager) {
    val filter: Flow<DriverFilter> = preferencesManager.driverFilter

    suspend fun saveFilter(filter: DriverFilter) {
        preferencesManager.saveDriverFilter(filter)
    }

    suspend fun resetFilters() {
        preferencesManager.resetAllFilters()
    }
}
