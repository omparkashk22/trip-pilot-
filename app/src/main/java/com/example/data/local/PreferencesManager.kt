package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DriverFilter
import com.example.data.model.LocationKeyword
import com.example.data.model.StrategyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trip_pilot_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val KEY_ENGINE_ENABLED = booleanPreferencesKey("engine_enabled")
        val KEY_SERVICE_MODE = stringPreferencesKey("service_mode") // "auto_accept", "notify_only"
        val KEY_ALERT_ON_ACCEPT = booleanPreferencesKey("alert_on_accept")
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val KEY_VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")

        // Filters
        val KEY_MIN_FARE = doublePreferencesKey("min_fare")
        val KEY_MAX_FARE = doublePreferencesKey("max_fare")
        val KEY_UNLIMITED_MAX_FARE = booleanPreferencesKey("unlimited_max_fare")
        val KEY_DIST_FILTER_ENABLED = booleanPreferencesKey("dist_filter_enabled")
        val KEY_PICKUP_DIST_MIN = doublePreferencesKey("pickup_dist_min")
        val KEY_PICKUP_DIST_MAX = doublePreferencesKey("pickup_dist_max")
        val KEY_DROP_DIST_MIN = doublePreferencesKey("drop_dist_min")
        val KEY_DROP_DIST_MAX = doublePreferencesKey("drop_dist_max")
        val KEY_FARE_BASIS = stringPreferencesKey("fare_basis")
        val KEY_MIN_FARE_PER_KM = doublePreferencesKey("min_fare_per_km")
        val KEY_LOC_FILTER_ENABLED = booleanPreferencesKey("loc_filter_enabled")
        val KEY_LOC_KEYWORDS_JSON = stringPreferencesKey("loc_keywords_json")
        val KEY_ALLOWED_RIDE_TYPES = stringSetPreferencesKey("allowed_ride_types")
        val KEY_MATCH_STRATEGY = stringPreferencesKey("match_strategy")

        // App targets
        val KEY_BHARAT_TAXI_ENABLED = booleanPreferencesKey("bharat_taxi_enabled")
        val KEY_RAPIDO_ENABLED = booleanPreferencesKey("rapido_enabled")
        val KEY_BHARAT_TAXI_PACKAGE = stringPreferencesKey("bharat_taxi_pkg")
        val KEY_RAPIDO_PACKAGE = stringPreferencesKey("rapido_pkg")

        // Settings & Dev tools
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_APP_THEME = stringPreferencesKey("app_theme")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_PROCESS_TEST_REQUESTS = booleanPreferencesKey("process_test_requests")
        val KEY_TAP_METHOD = stringPreferencesKey("tap_method")
        val KEY_SHOW_DEBUG_INFO = booleanPreferencesKey("show_debug_info")
        val KEY_CAPTURE_ON_NEXT_OFFER = booleanPreferencesKey("capture_on_next_offer")
        val KEY_STRATEGY_STATS_JSON = stringPreferencesKey("strategy_stats_json")
    }

    val isDisclaimerAccepted: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_DISCLAIMER_ACCEPTED] ?: false
    }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        context.dataStore.edit { it[KEY_DISCLAIMER_ACCEPTED] = accepted }
    }

    val engineEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ENGINE_ENABLED] ?: false
    }

    suspend fun setEngineEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENGINE_ENABLED] = enabled }
    }

    val serviceMode: Flow<String> = context.dataStore.data.map {
        it[KEY_SERVICE_MODE] ?: "auto_accept"
    }

    suspend fun setServiceMode(mode: String) {
        context.dataStore.edit { it[KEY_SERVICE_MODE] = mode }
    }

    val alertOnAccept: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ALERT_ON_ACCEPT] ?: true
    }

    suspend fun setAlertOnAccept(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERT_ON_ACCEPT] = enabled }
    }

    val driverFilter: Flow<DriverFilter> = context.dataStore.data.map { prefs ->
        val locJson = prefs[KEY_LOC_KEYWORDS_JSON] ?: "[]"
        val locList = mutableListOf<LocationKeyword>()
        try {
            val array = JSONArray(locJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                locList.add(LocationKeyword(obj.getString("kw"), obj.getBoolean("acc")))
            }
        } catch (_: Exception) {}

        DriverFilter(
            minFare = prefs[KEY_MIN_FARE] ?: 80.0,
            maxFare = prefs[KEY_MAX_FARE],
            isUnlimitedMaxFare = prefs[KEY_UNLIMITED_MAX_FARE] ?: true,
            isDistanceFilterEnabled = prefs[KEY_DIST_FILTER_ENABLED] ?: true,
            pickupDistanceMinKm = prefs[KEY_PICKUP_DIST_MIN] ?: 0.0,
            pickupDistanceMaxKm = prefs[KEY_PICKUP_DIST_MAX] ?: 5.0,
            dropDistanceMinKm = prefs[KEY_DROP_DIST_MIN] ?: 0.0,
            dropDistanceMaxKm = prefs[KEY_DROP_DIST_MAX] ?: 150.0,
            fareBasis = prefs[KEY_FARE_BASIS] ?: "base_extra",
            minFarePerKm = prefs[KEY_MIN_FARE_PER_KM] ?: 0.0,
            isLocationFilterEnabled = prefs[KEY_LOC_FILTER_ENABLED] ?: false,
            locationKeywords = locList,
            allowedRideTypes = prefs[KEY_ALLOWED_RIDE_TYPES] ?: emptySet(),
            multipleMatchStrategy = prefs[KEY_MATCH_STRATEGY] ?: "first_match"
        )
    }

    suspend fun saveDriverFilter(filter: DriverFilter) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIN_FARE] = filter.minFare
            if (filter.maxFare != null) prefs[KEY_MAX_FARE] = filter.maxFare else prefs.remove(KEY_MAX_FARE)
            prefs[KEY_UNLIMITED_MAX_FARE] = filter.isUnlimitedMaxFare
            prefs[KEY_DIST_FILTER_ENABLED] = filter.isDistanceFilterEnabled
            prefs[KEY_PICKUP_DIST_MIN] = filter.pickupDistanceMinKm
            prefs[KEY_PICKUP_DIST_MAX] = filter.pickupDistanceMaxKm
            prefs[KEY_DROP_DIST_MIN] = filter.dropDistanceMinKm
            prefs[KEY_DROP_DIST_MAX] = filter.dropDistanceMaxKm
            prefs[KEY_FARE_BASIS] = filter.fareBasis
            prefs[KEY_MIN_FARE_PER_KM] = filter.minFarePerKm
            prefs[KEY_LOC_FILTER_ENABLED] = filter.isLocationFilterEnabled

            val jsonArray = JSONArray()
            filter.locationKeywords.forEach {
                val obj = JSONObject()
                obj.put("kw", it.keyword)
                obj.put("acc", it.isAccept)
                jsonArray.put(obj)
            }
            prefs[KEY_LOC_KEYWORDS_JSON] = jsonArray.toString()
            prefs[KEY_ALLOWED_RIDE_TYPES] = filter.allowedRideTypes
            prefs[KEY_MATCH_STRATEGY] = filter.multipleMatchStrategy
        }
    }

    suspend fun resetAllFilters() {
        saveDriverFilter(DriverFilter())
    }

    // Target App Package Mapping
    val resolvedBharatTaxiPackage: Flow<String?> = context.dataStore.data.map { it[KEY_BHARAT_TAXI_PACKAGE] }
    val resolvedRapidoPackage: Flow<String?> = context.dataStore.data.map { it[KEY_RAPIDO_PACKAGE] }

    suspend fun setResolvedPackage(appId: String, packageName: String) {
        context.dataStore.edit {
            if (appId == "bharat_taxi") it[KEY_BHARAT_TAXI_PACKAGE] = packageName
            else if (appId == "rapido") it[KEY_RAPIDO_PACKAGE] = packageName
        }
    }

    val isBharatTaxiEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BHARAT_TAXI_ENABLED] ?: true }
    val isRapidoEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_RAPIDO_ENABLED] ?: true }

    suspend fun setAppEnabled(appId: String, enabled: Boolean) {
        context.dataStore.edit {
            if (appId == "bharat_taxi") it[KEY_BHARAT_TAXI_ENABLED] = enabled
            else if (appId == "rapido") it[KEY_RAPIDO_ENABLED] = enabled
        }
    }

    // App Preferences
    val theme: Flow<String> = context.dataStore.data.map { it[KEY_APP_THEME] ?: "dark" }
    suspend fun setTheme(theme: String) { context.dataStore.edit { it[KEY_APP_THEME] = theme } }

    val language: Flow<String> = context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: "en" }
    suspend fun setLanguage(lang: String) { context.dataStore.edit { it[KEY_APP_LANGUAGE] = lang } }

    val tapMethod: Flow<String> = context.dataStore.data.map { it[KEY_TAP_METHOD] ?: "auto" }
    suspend fun setTapMethod(method: String) { context.dataStore.edit { it[KEY_TAP_METHOD] = method } }

    val showDebugInfo: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHOW_DEBUG_INFO] ?: false }
    suspend fun setShowDebugInfo(show: Boolean) { context.dataStore.edit { it[KEY_SHOW_DEBUG_INFO] = show } }

    val processTestRequests: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROCESS_TEST_REQUESTS] ?: true }
    suspend fun setProcessTestRequests(process: Boolean) { context.dataStore.edit { it[KEY_PROCESS_TEST_REQUESTS] = process } }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEY_KEEP_SCREEN_ON] ?: false }
    suspend fun setKeepScreenOn(keep: Boolean) { context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = keep } }

    val captureOnNextOffer: Flow<Boolean> = context.dataStore.data.map { it[KEY_CAPTURE_ON_NEXT_OFFER] ?: false }
    suspend fun setCaptureOnNextOffer(capture: Boolean) { context.dataStore.edit { it[KEY_CAPTURE_ON_NEXT_OFFER] = capture } }

    // Strategy calibration stats
    val strategyStats: Flow<Map<String, StrategyStats>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_STRATEGY_STATS_JSON] ?: "{}"
        val map = mutableMapOf<String, StrategyStats>()
        try {
            val root = JSONObject(json)
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                map[key] = StrategyStats(
                    strategyName = key,
                    attempts = obj.optInt("att", 0),
                    verifiedSuccesses = obj.optInt("succ", 0)
                )
            }
        } catch (_: Exception) {}
        map
    }

    suspend fun recordStrategyAttempt(strategy: String, success: Boolean) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_STRATEGY_STATS_JSON] ?: "{}"
            val root = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            val existing = root.optJSONObject(strategy) ?: JSONObject()
            val att = existing.optInt("att", 0) + 1
            val succ = existing.optInt("succ", 0) + (if (success) 1 else 0)
            existing.put("att", att)
            existing.put("succ", succ)
            root.put(strategy, existing)
            prefs[KEY_STRATEGY_STATS_JSON] = root.toString()
        }
    }
}
