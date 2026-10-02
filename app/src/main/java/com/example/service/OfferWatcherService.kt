package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.TripPilotApp
import com.example.data.model.AppRuntimeState
import com.example.data.model.AppRuntimeStatus
import com.example.data.model.DriverFilter
import com.example.data.model.RideLog
import com.example.data.model.RideOffer
import com.example.domain.engine.FilterEngine
import com.example.domain.engine.OfferParser
import com.example.domain.engine.SelectionEngine
import com.example.domain.engine.TapStrategyExecutor
import com.example.domain.tree.ScreenTreeDumper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class OfferWatcherService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processJob: Job? = null
    private lateinit var notificationManager: OverlayNotificationManager

    private var lastEventTime = 0L
    private val tappedFingerprints = ConcurrentHashMap<String, Long>()
    private var lastAutoDumpTimeMs = 0L

    // Live configuration cache from DataStore/AppResolver
    private var resolvedBharatPackage: String? = null
    private var resolvedRapidoPackage: String? = null
    private var isBharatEnabled = true
    private var isRapidoEnabled = true

    // Per-app runtime tracking
    private var activeForegroundAppId: String? = null
    private var offerDetectedAppId: String? = null
    private var notRecognizedAppId: String? = null

    private val lastEventAtMap = ConcurrentHashMap<String, Long>()
    private val eventTimestampsMap = ConcurrentHashMap<String, MutableList<Long>>()
    private val lastOfferParsedAtMap = ConcurrentHashMap<String, Long>()
    private val lastDecisionMap = ConcurrentHashMap<String, String>()

    companion object {
        var instance: OfferWatcherService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        notificationManager = OverlayNotificationManager(this)

        // 1) Configure serviceInfo: REMOVE packageNames; do NOT filter by package in XML or ServiceInfo!
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 50
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.packageNames = null // Filter ONLY in code, inside onAccessibilityEvent!
        serviceInfo = info

        // Observe DataStore engineEnabled and live package configurations
        serviceScope.launch {
            val app = TripPilotApp.instance

            launch {
                app.preferencesManager.engineEnabled.collect { enabled ->
                    TripPilotApp.engineState.value = enabled
                    if (!enabled) {
                        processJob?.cancel()
                        tappedFingerprints.clear()
                        activeForegroundAppId = null
                        offerDetectedAppId = null
                        notRecognizedAppId = null
                        TripPilotApp.detectionState.value = "Idle"
                        TripPilotApp.currentForegroundApp.value = null
                        TripPilotApp.offerRecognitionFailed.value = false
                        publishRuntimeStatuses()
                        try {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } catch (_: Exception) {}
                    } else {
                        publishRuntimeStatuses()
                    }
                }
            }

            launch {
                app.preferencesManager.resolvedBharatTaxiPackage.collect {
                    resolvedBharatPackage = it
                    TripPilotApp.resolvedBharatPackageLive.value = it
                    publishRuntimeStatuses()
                }
            }

            launch {
                app.preferencesManager.resolvedRapidoPackage.collect {
                    resolvedRapidoPackage = it
                    TripPilotApp.resolvedRapidoPackageLive.value = it
                    publishRuntimeStatuses()
                }
            }

            launch {
                app.preferencesManager.isBharatTaxiEnabled.collect {
                    isBharatEnabled = it
                    publishRuntimeStatuses()
                }
            }
            launch {
                app.preferencesManager.isRapidoEnabled.collect {
                    isRapidoEnabled = it
                    publishRuntimeStatuses()
                }
            }

            // Periodic 1-second ticker to decay eventsLast60s and keep UI live
            launch {
                while (isActive) {
                    delay(1000)
                    publishRuntimeStatuses()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // (A) Check engineEnabled at the top of every event callback and return immediately when false
        if (!TripPilotApp.engineState.value) {
            return
        }

        if (event == null) return
        val pkgName = event.packageName?.toString() ?: return

        // 1) Window State Changed: check if front app changed
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isBharat = !resolvedBharatPackage.isNullOrEmpty() && pkgName == resolvedBharatPackage
            val isRapido = !resolvedRapidoPackage.isNullOrEmpty() && pkgName == resolvedRapidoPackage

            when {
                isBharat && isBharatEnabled -> {
                    activeForegroundAppId = "bharat_taxi"
                    offerDetectedAppId = null
                    notRecognizedAppId = null
                    TripPilotApp.currentForegroundApp.value = "Bharat Taxi"
                    TripPilotApp.detectionState.value = "Watching"
                }
                isRapido && isRapidoEnabled -> {
                    activeForegroundAppId = "rapido"
                    offerDetectedAppId = null
                    notRecognizedAppId = null
                    TripPilotApp.currentForegroundApp.value = "Rapido"
                    TripPilotApp.detectionState.value = "Watching"
                }
                else -> {
                    // Another app is in front (TripPilot, Launcher, Settings, etc.)
                    // FOREGROUND is true ONLY while that app's window is the active one!
                    activeForegroundAppId = null
                    offerDetectedAppId = null
                    notRecognizedAppId = null
                    TripPilotApp.currentForegroundApp.value = null
                    TripPilotApp.detectionState.value = "Idle"
                }
            }
            publishRuntimeStatuses()
        }

        // 2) Filter by package in code: check if event belongs to an enabled target app
        val targetAppId = when {
            isBharatEnabled && !resolvedBharatPackage.isNullOrEmpty() && pkgName == resolvedBharatPackage -> "bharat_taxi"
            isRapidoEnabled && !resolvedRapidoPackage.isNullOrEmpty() && pkgName == resolvedRapidoPackage -> "rapido"
            else -> return // Ignore non-target applications
        }

        // Active foreground target app confirmed
        activeForegroundAppId = targetAppId
        TripPilotApp.currentForegroundApp.value = if (targetAppId == "bharat_taxi") "Bharat Taxi" else "Rapido"
        if (TripPilotApp.detectionState.value == "Idle") {
            TripPilotApp.detectionState.value = "Watching"
        }

        // Record per-app event counter
        val now = System.currentTimeMillis()
        lastEventAtMap[targetAppId] = now
        val list = eventTimestampsMap.getOrPut(targetAppId) { mutableListOf() }
        synchronized(list) {
            list.add(now)
            val cutoff = now - 60_000L
            list.removeAll { it < cutoff }
        }
        publishRuntimeStatuses()

        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastEventTime < 50) {
            // Debounce to at most one parse per 50 ms
            return
        }
        lastEventTime = currentTime

        processJob?.cancel()
        processJob = serviceScope.launch {
            processScreen(targetAppId, pkgName)
        }
    }

    private suspend fun processScreen(appId: String, packageName: String) {
        if (!TripPilotApp.engineState.value) return

        val app = TripPilotApp.instance
        val processTest = app.preferencesManager.processTestRequests.first()
        val filter = app.preferencesManager.driverFilter.first()
        val mode = app.preferencesManager.serviceMode.first()
        val forcedTapMethod = app.preferencesManager.tapMethod.first()
        val strategyStats = app.preferencesManager.strategyStats.first()
        val alertOnAccept = app.preferencesManager.alertOnAccept.first()

        val displayMetrics = resources.displayMetrics
        val offers = OfferParser.parseWindows(
            windows = windows,
            rootNode = rootInActiveWindow,
            appId = appId,
            processTestRequests = processTest,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels
        )

        if (offers.isEmpty()) {
            val root = rootInActiveWindow
            val rootText = root?.text?.toString() ?: ""
            val hasAcceptOnScreen = rootText.contains("Accept", ignoreCase = true) ||
                    rootText.contains("₹") ||
                    (windows?.any { w -> w.root?.text?.toString()?.contains("Accept", ignoreCase = true) == true } == true)

            if (hasAcceptOnScreen) {
                notRecognizedAppId = appId
                offerDetectedAppId = null
                TripPilotApp.detectionState.value = "Offer screen not recognized"
                TripPilotApp.offerRecognitionFailed.value = true

                // Auto-save one dump (at most once per 10 minutes)
                val now = System.currentTimeMillis()
                if (now - lastAutoDumpTimeMs > 10 * 60 * 1000) {
                    lastAutoDumpTimeMs = now
                    try {
                        ScreenTreeDumper.dumpScreenTree(this@OfferWatcherService, windows, rootInActiveWindow, packageName)
                        Log.i("OfferWatcherService", "Auto-saved unrecognized screen tree dump for $packageName")
                    } catch (e: Exception) {
                        Log.e("OfferWatcherService", "Failed to auto-save dump", e)
                    }
                }
            } else {
                notRecognizedAppId = null
                offerDetectedAppId = null
                TripPilotApp.detectionState.value = "Watching"
                TripPilotApp.offerRecognitionFailed.value = false
            }
            publishRuntimeStatuses()
            return
        }

        offerDetectedAppId = appId
        notRecognizedAppId = null
        lastOfferParsedAtMap[appId] = System.currentTimeMillis()
        TripPilotApp.detectionState.value = "Offer detected"
        TripPilotApp.offerRecognitionFailed.value = false
        publishRuntimeStatuses()

        // Check if Developer Tools requested "Capture on next offer"
        val captureOnNext = app.preferencesManager.captureOnNextOffer.first()
        if (captureOnNext) {
            app.preferencesManager.setCaptureOnNextOffer(false)
            ScreenTreeDumper.dumpScreenTree(this@OfferWatcherService, windows, rootInActiveWindow, packageName)
        }

        // Clean up old fingerprints (> 15 seconds)
        val now = System.currentTimeMillis()
        tappedFingerprints.entries.removeIf { now - it.value > 15_000 }

        // Evaluate all offers
        val actionableCandidates = mutableListOf<RideOffer>()
        for (offer in offers) {
            val eval = FilterEngine.evaluate(offer, filter, isAppEnabled = true)
            if (eval.isMatched && offer.isActionable) {
                // Check 15-second deduplication
                if (!tappedFingerprints.containsKey(offer.fingerprint)) {
                    actionableCandidates.add(offer)
                }
            } else if (!eval.isMatched) {
                // Log non-matching offer as skipped
                saveRideLogAsync(offer, status = "SKIPPED", skipReason = eval.skipReason)
                lastDecisionMap[appId] = "Skipped"
            }
        }

        if (actionableCandidates.isEmpty()) {
            publishRuntimeStatuses()
            return
        }

        // SelectionEngine chooses best offer
        val chosenOffer = SelectionEngine.selectBestOffer(actionableCandidates, filter)
        if (chosenOffer == null) {
            publishRuntimeStatuses()
            return
        }

        // Log other matching offers as skipped ("Better offer chosen")
        actionableCandidates.filter { it != chosenOffer }.forEach { other ->
            saveRideLogAsync(other, status = "SKIPPED", skipReason = "Better offer chosen")
        }

        tappedFingerprints[chosenOffer.fingerprint] = now

        if (mode == "notify_only") {
            notificationManager.showOfferMatchedNotification(chosenOffer)
            if (alertOnAccept) notificationManager.playAlertFeedback()
            saveRideLogAsync(chosenOffer, status = "ACCEPTED", skipReason = null, tapMethod = "NOTIFY_ONLY", tapLatencyMs = 0)
            lastDecisionMap[appId] = "Accepted"
            publishRuntimeStatuses()
            return
        }

        // Execute tap in auto-accept mode
        val tapResult = TapStrategyExecutor.executeTap(
            service = this@OfferWatcherService,
            node = chosenOffer.acceptNode,
            nodeBounds = chosenOffer.acceptNodeBounds,
            forcedMethod = forcedTapMethod,
            strategyStats = strategyStats
        )

        app.preferencesManager.recordStrategyAttempt(tapResult.methodUsed.key, tapResult.success)

        if (tapResult.success) {
            if (alertOnAccept) notificationManager.playAlertFeedback()
            saveRideLogAsync(chosenOffer, status = "ACCEPTED", skipReason = null, tapMethod = tapResult.methodUsed.name, tapLatencyMs = tapResult.latencyMs)
            lastDecisionMap[appId] = "Accepted"
        } else {
            saveRideLogAsync(chosenOffer, status = "TAP_FAILED", skipReason = "Tap execution failed", tapMethod = tapResult.methodUsed.name, tapLatencyMs = tapResult.latencyMs)
            lastDecisionMap[appId] = "Tap Failed"
        }
        publishRuntimeStatuses()
    }

    private fun publishRuntimeStatuses() {
        val app = TripPilotApp.instance
        val pm = packageManager
        val isEngineRunning = TripPilotApp.engineState.value

        val targetApps = listOf(
            Triple("bharat_taxi", "Bharat Taxi", resolvedBharatPackage),
            Triple("rapido", "Rapido", resolvedRapidoPackage)
        )

        val updatedMap = mutableMapOf<String, AppRuntimeStatus>()
        val now = System.currentTimeMillis()
        val cutoff = now - 60_000L

        for ((appId, name, resolvedPkg) in targetApps) {
            val isInstalled = if (!resolvedPkg.isNullOrEmpty()) {
                try {
                    pm.getPackageInfo(resolvedPkg, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            } else false

            val isEnabled = if (appId == "bharat_taxi") isBharatEnabled else isRapidoEnabled

            val state = when {
                !isInstalled -> AppRuntimeState.NOT_INSTALLED
                !isEnabled -> AppRuntimeState.PAUSED
                !isEngineRunning -> AppRuntimeState.IDLE
                activeForegroundAppId == appId -> {
                    when {
                        offerDetectedAppId == appId -> AppRuntimeState.OFFER_DETECTED
                        notRecognizedAppId == appId -> AppRuntimeState.NOT_RECOGNIZED
                        else -> AppRuntimeState.FOREGROUND
                    }
                }
                else -> AppRuntimeState.IDLE
            }

            // Events last 60s
            val list = eventTimestampsMap[appId]
            val count60s = if (list != null) {
                synchronized(list) {
                    list.removeAll { it < cutoff }
                    list.size
                }
            } else 0

            updatedMap[appId] = AppRuntimeStatus(
                appId = appId,
                displayName = name,
                state = state,
                resolvedPackage = resolvedPkg,
                isInstalled = isInstalled,
                isEnabled = isEnabled,
                lastEventAt = lastEventAtMap[appId],
                eventsLast60s = count60s,
                lastOfferParsedAt = lastOfferParsedAtMap[appId],
                lastDecision = lastDecisionMap[appId]
            )
        }

        TripPilotApp.appRuntimeStatuses.value = updatedMap
    }

    private fun saveRideLogAsync(
        offer: RideOffer,
        status: String,
        skipReason: String? = null,
        tapMethod: String? = null,
        tapLatencyMs: Long = 0
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val log = RideLog(
                appId = offer.appId,
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
                status = status,
                skipReason = skipReason,
                tapMethod = tapMethod,
                tapLatencyMs = tapLatencyMs
            )
            TripPilotApp.instance.rideLogRepository.insertRideLog(log)
        }
    }

    override fun onInterrupt() {
        processJob?.cancel()
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        processJob?.cancel()
        serviceScope.cancel()
        instance = null
    }
}
