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

    // Ride Types grouped by app
    val bharatTaxiRideTypes = MutableStateFlow(
        listOf("Cab Economy", "Cab Premium", "Economy Intercity", "Premium Intercity")
    )
    val rapidoRideTypes = MutableStateFlow(
        listOf("Bike Boost", "Bike", "Auto", "Cab")
    )

    val customRideTypeInput = MutableStateFlow("")
    val selectedAppForCustomType = MutableStateFlow("bharat_taxi") // "bharat_taxi" or "rapido"

    val allowedRideTypes = MutableStateFlow<Set<String>>(emptySet())
    val multipleMatchStrategy = MutableStateFlow("first_match")

    private val _isSavedRecently = MutableStateFlow(false)
    val isSavedRecently: StateFlow<Boolean> = _isSavedRecently.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

                // If user has saved custom ride types that aren't in defaults, add them
                val extraBharat = f.allowedRideTypes.filter { t ->
                    t.contains("Cab", ignoreCase = true) || t.contains("Intercity", ignoreCase = true) || t.contains("Economy", ignoreCase = true)
                }
                if (extraBharat.isNotEmpty()) {
                    bharatTaxiRideTypes.value = (bharatTaxiRideTypes.value + extraBharat).distinct()
                }
                val extraRapido = f.allowedRideTypes.filter { !bharatTaxiRideTypes.value.contains(it) }
                if (extraRapido.isNotEmpty()) {
                    rapidoRideTypes.value = (rapidoRideTypes.value + extraRapido).distinct()
                }
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

    fun addCustomRideType() {
        val type = customRideTypeInput.value.trim()
        if (type.isEmpty()) return

        if (selectedAppForCustomType.value == "bharat_taxi") {
            if (!bharatTaxiRideTypes.value.contains(type)) {
                bharatTaxiRideTypes.value = bharatTaxiRideTypes.value + type
            }
        } else {
            if (!rapidoRideTypes.value.contains(type)) {
                rapidoRideTypes.value = rapidoRideTypes.value + type
            }
        }
        // Auto-select newly added custom ride type
        val current = allowedRideTypes.value.toMutableSet()
        current.add(type)
        allowedRideTypes.value = current
        customRideTypeInput.value = ""
    }

    fun recordDiscoveredRideType(appId: String, rideType: String) {
        val clean = rideType.trim()
        if (clean.isEmpty()) return
        if (appId == "bharat_taxi") {
            if (!bharatTaxiRideTypes.value.any { it.equals(clean, ignoreCase = true) }) {
                bharatTaxiRideTypes.value = bharatTaxiRideTypes.value + clean
            }
        } else {
            if (!rapidoRideTypes.value.any { it.equals(clean, ignoreCase = true) }) {
                rapidoRideTypes.value = rapidoRideTypes.value + clean
            }
        }
    }

    fun saveFilters() {
        val pMin = pickupDistMin.value.toDoubleOrNull() ?: 0.0
        val pMax = pickupDistMax.value.toDoubleOrNull() ?: 5.0
        val dMin = dropDistMin.value.toDoubleOrNull() ?: 0.0
        val dMax = dropDistMax.value.toDoubleOrNull() ?: 150.0

        if (pMin > pMax) {
            _errorMessage.value = "Min pickup distance cannot exceed Max pickup distance"
            return
        }
        if (dMin > dMax) {
            _errorMessage.value = "Min drop distance cannot exceed Max drop distance"
            return
        }

        _errorMessage.value = null
        val minRate = minFarePerKm.value.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            val updated = filter.value.copy(
                isDistanceFilterEnabled = isDistanceFilterEnabled.value,
                pickupDistanceMinKm = pMin,
                pickupDistanceMaxKm = pMax,
                dropDistanceMinKm = dMin,
                dropDistanceMaxKm = dMax,
                fareBasis = fareBasis.value,
                minFarePerKm = minRate,
                isLocationFilterEnabled = isLocationFilterEnabled.value,
                locationKeywords = locationKeywords.value,
                allowedRideTypes = emptySet(),
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
            _isSavedRecently.value = false
            _errorMessage.value = null
        }
    }
}
