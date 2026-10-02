package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.example.TripPilotApp
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class OfferWatcherService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var notificationManager: OverlayNotificationManager

    private var lastEventTime = 0L
    private val tappedFingerprints = ConcurrentHashMap<String, Long>()

    // Live configuration cache
    private var resolvedBharatPackage: String? = null
    private var resolvedRapidoPackage: String? = "com.rapido.rider"
    private var isBharatEnabled = true
    private var isRapidoEnabled = true

    companion object {
        var instance: OfferWatcherService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        notificationManager = OverlayNotificationManager(this)
        TripPilotApp.isServiceRunning.value = true

        // Observe resolved packages & enabled targets live from DataStore
        serviceScope.launch {
            val app = TripPilotApp.instance
            launch { app.preferencesManager.resolvedBharatTaxiPackage.collect { resolvedBharatPackage = it } }
            launch { app.preferencesManager.resolvedRapidoPackage.collect { resolvedRapidoPackage = it } }
            launch { app.preferencesManager.isBharatTaxiEnabled.collect { isBharatEnabled = it } }
            launch { app.preferencesManager.isRapidoEnabled.collect { isRapidoEnabled = it } }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        val targetAppId = when {
            isBharatEnabled && !resolvedBharatPackage.isNullOrEmpty() && pkgName == resolvedBharatPackage -> "bharat_taxi"
            isRapidoEnabled && !resolvedRapidoPackage.isNullOrEmpty() && pkgName == resolvedRapidoPackage -> "rapido"
            else -> return // Ignore other applications
        }

        TripPilotApp.currentForegroundApp.value = if (targetAppId == "bharat_taxi") "Bharat Taxi" else "Rapido"
        TripPilotApp.detectionState.value = "Watching"

        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastEventTime < 50) {
            // Debounce to at most one parse per 50 ms
            return
        }
        lastEventTime = currentTime

        serviceScope.launch {
            processScreen(targetAppId, pkgName)
        }
    }

    private suspend fun processScreen(appId: String, packageName: String) {
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
            if (rootText.contains("Accept", ignoreCase = true) || rootText.contains("₹")) {
                TripPilotApp.detectionState.value = "Offer screen not recognized"
                TripPilotApp.offerRecognitionFailed.value = true
            } else {
                TripPilotApp.detectionState.value = "Watching"
                TripPilotApp.offerRecognitionFailed.value = false
            }
            return
        }

        TripPilotApp.detectionState.value = "Offer detected"
        TripPilotApp.offerRecognitionFailed.value = false

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
            }
        }

        if (actionableCandidates.isEmpty()) return

        // SelectionEngine chooses best offer
        val chosenOffer = SelectionEngine.selectBestOffer(actionableCandidates, filter) ?: return

        // Log other matching offers as skipped ("Better offer chosen")
        actionableCandidates.filter { it != chosenOffer }.forEach { other ->
            saveRideLogAsync(other, status = "SKIPPED", skipReason = "Better offer chosen")
        }

        tappedFingerprints[chosenOffer.fingerprint] = now

        if (mode == "notify_only") {
            notificationManager.showOfferMatchedNotification(chosenOffer)
            if (alertOnAccept) notificationManager.playAlertFeedback()
            saveRideLogAsync(chosenOffer, status = "ACCEPTED", skipReason = null, tapMethod = "NOTIFY_ONLY", tapLatencyMs = 0)
            return
        }

        // AUTO-ACCEPT MODE: Tap Accept once per decision cycle
        val tapResult = TapStrategyExecutor.executeTap(
            service = this@OfferWatcherService,
            node = chosenOffer.acceptNode,
            nodeBounds = chosenOffer.acceptNodeBounds,
            forcedMethod = forcedTapMethod,
            strategyStats = strategyStats
        )

        // Wait up to 1.5s and verify that the card disappeared
        delay(1500)
        val recheckOffers = OfferParser.parseWindows(
            windows = windows,
            rootNode = rootInActiveWindow,
            appId = appId,
            processTestRequests = processTest,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels
        )
        val stillPresent = recheckOffers.any { it.fingerprint == chosenOffer.fingerprint }
        val verified = tapResult.success && !stillPresent

        // Record strategy success in DataStore
        app.preferencesManager.recordStrategyAttempt(tapResult.methodUsed.key, verified)

        if (verified) {
            if (alertOnAccept) notificationManager.playAlertFeedback()
            saveRideLogAsync(
                chosenOffer,
                status = "ACCEPTED",
                skipReason = null,
                tapMethod = tapResult.methodUsed.displayName,
                tapLatencyMs = tapResult.latencyMs
            )
        } else {
            // One retry with alternative strategy
            val retryResult = TapStrategyExecutor.executeTap(
                service = this@OfferWatcherService,
                node = chosenOffer.acceptNode,
                nodeBounds = chosenOffer.acceptNodeBounds,
                forcedMethod = if (tapResult.methodUsed.key == "node_click") "gesture" else "node_click",
                strategyStats = strategyStats
            )
            saveRideLogAsync(
                chosenOffer,
                status = if (retryResult.success) "ACCEPTED" else "TAP_FAILED",
                skipReason = if (retryResult.success) null else "Accept tap was not verified",
                tapMethod = retryResult.methodUsed.displayName,
                tapLatencyMs = retryResult.latencyMs
            )
        }
    }

    private fun saveRideLogAsync(
        offer: RideOffer,
        status: String,
        skipReason: String?,
        tapMethod: String? = null,
        tapLatencyMs: Long = 0
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val log = RideLog(
                appId = offer.appId,
                timestamp = System.currentTimeMillis(),
                rideType = offer.rideType,
                baseFare = offer.baseFare,
                extraFare = offer.extraFare,
                totalFare = offer.totalFare,
                pickupDistanceKm = offer.pickupDistanceKm,
                pickupEtaMin = offer.pickupEtaMin,
                dropDistanceKm = offer.dropDistanceKm,
                dropEtaMin = offer.dropEtaMin,
                pickupAddress = offer.pickupAddress,
                dropAddress = offer.dropAddress,
                dropAddressTruncated = offer.dropAddressTruncated,
                status = status,
                skipReason = skipReason,
                tapMethod = tapMethod,
                tapLatencyMs = tapLatencyMs,
                isTest = offer.isTest,
                rawTextHash = offer.rawTextHash
            )
            TripPilotApp.instance.rideLogRepository.insertRideLog(log)
        }
    }

    override fun onInterrupt() {
        TripPilotApp.isServiceRunning.value = false
        TripPilotApp.detectionState.value = "Idle"
    }

    override fun onDestroy() {
        TripPilotApp.isServiceRunning.value = false
        TripPilotApp.detectionState.value = "Idle"
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
