package com.example.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DriverFilter
import com.example.data.model.LocationKeyword
import com.example.data.repository.FilterRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FiltersViewModel(private val filterRepository: FilterRepository) : ViewModel() {

    val filter: StateFlow<DriverFilter> = filterRepository.filter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverFilter())

    // Editable form state
    val isDistanceFilterEnabled = MutableStateFlow(true)
    val pickupDistMin = MutableStateFlow("0.0")
    val pickupDistMax = MutableStateFlow("5.0")
    val dropDistMin = MutableStateFlow("0.0")
    val dropDistMax = MutableStateFlow("150.0")

    val fareBasis = MutableStateFlow("base_extra") // "base_only", "base_extra"
    val minFarePerKm = MutableStateFlow("")

    val isLocationFilterEnabled = MutableStateFlow(false)
    val newLocationKeyword = MutableStateFlow("")
    val isNewKeywordAccept = MutableStateFlow(true) // true = Accept, false = Reject
    val locationKeywords = MutableStateFlow<List<LocationKeyword>>(emptyList())

    val allowedRideTypes = MutableStateFlow<Set<String>>(emptySet())
    val multipleMatchStrategy = MutableStateFlow("first_match")

    private val _isSavedRecently = MutableStateFlow(false)
    val isSavedRecently: StateFlow<Boolean> = _isSavedRecently.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val availableRideTypes = listOf("Bike", "Bike Boost", "Auto", "Cab", "Bike Lite", "Bharat Taxi")

    init {
        viewModelScope.launch {
            filter.collect { f ->
                isDistanceFilterEnabled.value = f.isDistanceFilterEnabled
                pickupDistMin.value = f.pickupDistanceMinKm.toString()
                pickupDistMax.value = f.pickupDistanceMaxKm.toString()
                dropDistMin.value = f.dropDistanceMinKm.toString()
                dropDistMax.value = f.dropDistanceMaxKm.toString()

                fareBasis.value = f.fareBasis
                minFarePerKm.value = if (f.minFarePerKm > 0) f.minFarePerKm.toString() else ""

                isLocationFilterEnabled.value = f.isLocationFilterEnabled
                locationKeywords.value = f.locationKeywords

                allowedRideTypes.value = f.allowedRideTypes
                multipleMatchStrategy.value = f.multipleMatchStrategy
            }
        }
    }

    fun addLocationKeyword() {
        val kw = newLocationKeyword.value.trim()
        if (kw.isEmpty()) return
        val current = locationKeywords.value.toMutableList()
        current.removeAll { it.keyword.equals(kw, ignoreCase = true) }
        current.add(LocationKeyword(keyword = kw, isAccept = isNewKeywordAccept.value))
        locationKeywords.value = current
        newLocationKeyword.value = ""
    }

    fun removeLocationKeyword(item: LocationKeyword) {
        val current = locationKeywords.value.toMutableList()
        current.remove(item)
        locationKeywords.value = current
    }

    fun toggleRideType(type: String) {
        val current = allowedRideTypes.value.toMutableSet()
        if (current.contains(type)) {
            current.remove(type)
        } else {
            current.add(type)
        }
        allowedRideTypes.value = current
    }

    fun saveFilters() {
        val pMin = pickupDistMin.value.toDoubleOrNull() ?: 0.0
        val pMax = pickupDistMax.value.toDoubleOrNull() ?: 5.0
        val dMin = dropDistMin.value.toDoubleOrNull() ?: 0.0
        val dMax = dropDistMax.value.toDoubleOrNull() ?: 150.0

        if (pMin > pMax) {
            _errorMessage.value = "Pickup min distance cannot exceed max distance."
            return
        }
        if (dMin > dMax) {
            _errorMessage.value = "Drop min distance cannot exceed max distance."
            return
        }
        _errorMessage.value = null

        val fPerKm = minFarePerKm.value.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            val updated = filter.value.copy(
                isDistanceFilterEnabled = isDistanceFilterEnabled.value,
                pickupDistanceMinKm = pMin,
                pickupDistanceMaxKm = pMax,
                dropDistanceMinKm = dMin,
                dropDistanceMaxKm = dMax,
                fareBasis = fareBasis.value,
                minFarePerKm = fPerKm,
                isLocationFilterEnabled = isLocationFilterEnabled.value,
                locationKeywords = locationKeywords.value,
                allowedRideTypes = allowedRideTypes.value,
                multipleMatchStrategy = multipleMatchStrategy.value
            )
            filterRepository.saveFilter(updated)
            _isSavedRecently.value = true
            delay(2000)
            _isSavedRecently.value = false
        }
    }

    fun resetFilters() {
        viewModelScope.launch {
            filterRepository.resetFilters()
        }
    }
}
