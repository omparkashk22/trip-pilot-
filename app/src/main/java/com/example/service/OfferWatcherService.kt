package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.TripPilotApp
import com.example.data.model.AppRuntimeState
import com.example.data.model.AppRuntimeStatus
import com.example.data.model.RideOffer
import com.example.data.model.StrategyStats
import com.example.domain.engine.FilterEngine
import com.example.domain.engine.FilterSnapshot
import com.example.domain.engine.OfferParser
import com.example.domain.engine.SelectionEngine
import com.example.domain.engine.TapExecutionResult
import com.example.domain.engine.TapMethodType
import com.example.domain.engine.TapStrategyExecutor
import com.example.domain.tree.ScreenTreeDumper
import com.example.util.AppLogger
import com.example.util.TurboModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class OfferWatcherService : AccessibilityService() {

    // Background coroutine scope for non-hot-path tasks (DB, alerts, UI status)
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: OverlayNotificationManager

    // Dedicated high-priority thread for hot-path offer engine (parse -> decide -> tap)
    private val engineThread = HandlerThread("offer-engine", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply {
        start()
    }
    private val engineHandler = Handler(engineThread.looper)

    // Immutable decision snapshot (zero I/O on hot path, single volatile reference swap)
    @Volatile
    var currentSnapshot: FilterSnapshot = FilterSnapshot()
        private set

    // In-memory 15-second cache of recently tapped fingerprints to prevent duplicate taps
    private val tappedFingerprints = ConcurrentHashMap<String, Long>()

    // Track tap attempts per fingerprint for verification retries (max 2 attempts per fingerprint)
    private val fingerprintTapAttempts = ConcurrentHashMap<String, Int>()
    private val fingerprintLastMethod = ConcurrentHashMap<String, TapMethodType>()

    // Leading-edge throttle: handle first event immediately; ignore further events of same package for 40 ms ONLY while parse is running
    private val isPackageParsingMap = ConcurrentHashMap<String, Boolean>()
    private val lastPackageEventTimeMap = ConcurrentHashMap<String, Long>()

    // Per-app runtime tracking
    private var activeForegroundAppId: String? = null
    private var offerDetectedAppId: String? = null
    private var notRecognizedAppId: String? = null

    private val lastEventAtMap = ConcurrentHashMap<String, Long>()
    private val eventTimestampsMap = ConcurrentHashMap<String, MutableList<Long>>()
    private val lastOfferParsedAtMap = ConcurrentHashMap<String, Long>()
    private val lastDecisionMap = ConcurrentHashMap<String, String>()

    private var lastAutoDumpTimeMs = 0L
    private var lastLowConfidenceDumpTimeMs = 0L

    // In-memory strategy stats cache
    @Volatile
    private var cachedStrategyStats: Map<String, StrategyStats> = emptyMap()

    companion object {
        var instance: OfferWatcherService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        notificationManager = OverlayNotificationManager(this)

        // B) SERVICE CONFIGURATION: notificationTimeout = 0; flags; eventTypes; packageNames = null
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 0
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.packageNames = null // Filter in code ONLY
        serviceInfo = info

        val app = TripPilotApp.instance

        // Observe filterSnapshot updates (single @Volatile reference swap)
        backgroundScope.launch {
            app.preferencesManager.filterSnapshot.collect { snapshot ->
                currentSnapshot = snapshot
                TripPilotApp.engineState.value = snapshot.engineEnabled
                TripPilotApp.resolvedBharatPackageLive.value = snapshot.resolvedBharatPackage
                TripPilotApp.resolvedRapidoPackageLive.value = snapshot.resolvedRapidoPackage

                // Maintain Turbo mode partial wake lock
                TurboModeManager.updateWakeLock(
                    context = this@OfferWatcherService,
                    engineRunning = snapshot.engineEnabled,
                    turboEnabled = snapshot.turboMode
                )

                if (!snapshot.engineEnabled) {
                    tappedFingerprints.clear()
                    fingerprintTapAttempts.clear()
                    fingerprintLastMethod.clear()
                    activeForegroundAppId = null
                    offerDetectedAppId = null
                    notRecognizedAppId = null
                    TripPilotApp.detectionState.value = "Idle"
                    TripPilotApp.currentForegroundApp.value = null
                    TripPilotApp.offerRecognitionFailed.value = false
                    publishRuntimeStatuses()
                } else {
                    publishRuntimeStatuses()
                }
            }
        }

        // Cache strategy stats in-memory
        backgroundScope.launch {
            app.preferencesManager.strategyStats.collect { stats ->
                cachedStrategyStats = stats
            }
        }

        // Periodic 1-second ticker to decay eventsLast60s
        backgroundScope.launch {
            while (isActive) {
                delay(1000)
                publishRuntimeStatuses()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val tEntry = SystemClock.uptimeMillis()
        val snapshot = currentSnapshot

        // (A) Check engineEnabled at the top of every event callback and return immediately when false
        if (!snapshot.engineEnabled) return
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        // 1) Fast package filter: check if event belongs to an enabled target app
        val targetAppId = when {
            snapshot.isBharatTaxiEnabled && !snapshot.resolvedBharatPackage.isNullOrEmpty() && pkgName == snapshot.resolvedBharatPackage -> "bharat_taxi"
            snapshot.isRapidoEnabled && !snapshot.resolvedRapidoPackage.isNullOrEmpty() && pkgName == snapshot.resolvedRapidoPackage -> "rapido"
            else -> {
                // If window state changed to non-target app, update foreground status asynchronously
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    backgroundScope.launch {
                        activeForegroundAppId = null
                        offerDetectedAppId = null
                        notRecognizedAppId = null
                        TripPilotApp.currentForegroundApp.value = null
                        TripPilotApp.detectionState.value = "Idle"
                        publishRuntimeStatuses()
                    }
                }
                return
            }
        }

        // B) LEADING-EDGE THROTTLE:
        // Handle the first event immediately; ignore further events of the same package for 40 ms ONLY while parse is already running
        val isParsing = isPackageParsingMap[targetAppId] == true
        val lastPkgTime = lastPackageEventTimeMap[targetAppId] ?: 0L
        if (isParsing && (tEntry - lastPkgTime < 40L)) {
            return
        }

        isPackageParsingMap[targetAppId] = true
        lastPackageEventTimeMap[targetAppId] = tEntry

        val eventTime = event.eventTime
        val eventLagMs = (tEntry - eventTime).coerceAtLeast(0L)

        val eventSource = try { event.source } catch (_: Exception) { null }
        val rootNode = try { rootInActiveWindow } catch (_: Exception) { null }
        val windowList = try { windows } catch (_: Exception) { null }

        // E) THREADING: dedicated "offer-engine" HandlerThread handles parse -> decide -> tap in one go
        engineHandler.post {
            try {
                handleOfferHotPath(
                    targetAppId = targetAppId,
                    packageName = pkgName,
                    eventSource = eventSource,
                    rootNode = rootNode,
                    windows = windowList,
                    tEntry = tEntry,
                    eventLagMs = eventLagMs,
                    eventTime = eventTime,
                    snapshot = snapshot
                )
            } finally {
                isPackageParsingMap[targetAppId] = false
            }
        }

        // Defer UI counters and foreground app state updates to background dispatcher (zero main-thread delay)
        backgroundScope.launch {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                activeForegroundAppId = targetAppId
                offerDetectedAppId = null
                notRecognizedAppId = null
                TripPilotApp.currentForegroundApp.value = if (targetAppId == "bharat_taxi") "Bharat Taxi" else "Rapido"
                if (TripPilotApp.detectionState.value == "Idle") {
                    TripPilotApp.detectionState.value = "Watching"
                }
            }
            val now = System.currentTimeMillis()
            lastEventAtMap[targetAppId] = now
            val list = eventTimestampsMap.getOrPut(targetAppId) { mutableListOf() }
            synchronized(list) {
                list.add(now)
                val cutoff = now - 60_000L
                list.removeAll { it < cutoff }
            }
            publishRuntimeStatuses()
        }
    }

    private fun handleOfferHotPath(
        targetAppId: String,
        packageName: String,
        eventSource: AccessibilityNodeInfo?,
        rootNode: AccessibilityNodeInfo?,
        windows: List<AccessibilityWindowInfo>?,
        tEntry: Long,
        eventLagMs: Long,
        eventTime: Long,
        snapshot: FilterSnapshot
    ) {
        val tStartParse = SystemClock.uptimeMillis()

        // I) MISSED RIDES CHECK: Detect "You missed the order" / "Sorry, this order was accepted by another captain."
        val missedOrderDetected = checkMissedOrderText(eventSource, rootNode, windows)
        if (missedOrderDetected) {
            backgroundScope.launch {
                TripPilotApp.instance.rideLogRepository.markLatestOfferMissed(targetAppId, 20_000L)
            }
            return
        }

        // C) FAST PATH DETECTION: Searches for Accept node first and parses ONLY the card ancestor subtree
        val displayMetrics = resources.displayMetrics
        var offers = OfferParser.fastPathParse(
            windows = windows,
            rootNode = rootNode,
            eventSource = eventSource,
            appId = targetAppId,
            targetPackage = packageName,
            processTestRequests = snapshot.processTestRequests,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels
        )

        // If the fast path finds no Accept node, do nothing (no full walk) and wait for the next event
        if (offers.isEmpty()) {
            return
        }

        // F) LOW PARSE CONFIDENCE RETRIES: retry at +60 ms, +120 ms, +250 ms (max 3 retries, total <= 430 ms)
        if (offers.any { it.parseConfidence == "LOW" }) {
            val retryDelays = longArrayOf(60L, 120L, 250L)
            for (delayMs in retryDelays) {
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    break
                }
                val reOffers = OfferParser.fastPathParse(
                    windows = try { this.windows } catch (_: Exception) { null },
                    rootNode = try { rootInActiveWindow } catch (_: Exception) { null },
                    eventSource = null,
                    appId = targetAppId,
                    targetPackage = packageName,
                    processTestRequests = snapshot.processTestRequests,
                    screenWidth = displayMetrics.widthPixels,
                    screenHeight = displayMetrics.heightPixels
                )
                if (reOffers.isNotEmpty()) {
                    offers = reOffers
                    if (offers.none { it.parseConfidence == "LOW" }) {
                        break
                    }
                }
            }
        }

        val tParsed = SystemClock.uptimeMillis()
        val parseMs = tParsed - tStartParse

        // D) DECIDE: Zero I/O on hot path, evaluate against in-memory FilterSnapshot
        val tStartDecide = SystemClock.uptimeMillis()
        val now = System.currentTimeMillis()
        val dedupeManager = TripPilotApp.instance.rideLogRepository.dedupeManager
        val actionableCandidates = mutableListOf<RideOffer>()

        // Clean up memory cache for old fingerprints (> 15 seconds)
        tappedFingerprints.entries.removeIf { now - it.value > 15_000L }

        for (offer in offers) {
            val adjustedOffer = if (offer.rideType.equals("Unknown", ignoreCase = true) && snapshot.allowedRideTypes.isNotEmpty()) {
                if (offer.parseConfidence == "HIGH") offer.copy(parseConfidence = "MEDIUM") else offer
            } else {
                offer
            }

            if (adjustedOffer.parseConfidence == "LOW") {
                val reason = adjustedOffer.parseReason ?: "Confidence LOW"
                postLogAsync(
                    offer = adjustedOffer,
                    status = "SKIPPED",
                    skipReason = "Parse uncertain ($reason)",
                    eventLagMs = eventLagMs,
                    parseMs = parseMs,
                    decideMs = 0L,
                    totalToTapMs = 0L,
                    outcome = "SKIPPED",
                    appForegroundId = targetAppId
                )
                continue
            }

            // Never overwrite or tap the same fingerprint again within 3-minute window if already accepted
            if (dedupeManager.isAcceptedWithinWindow(adjustedOffer.fingerprint, now)) {
                dedupeManager.extendWindowOnly(adjustedOffer.fingerprint, now)
                continue
            }

            val eval = FilterEngine.evaluate(adjustedOffer, snapshot)
            if (eval.isMatched && adjustedOffer.isActionable) {
                actionableCandidates.add(adjustedOffer)
            } else if (!eval.isMatched) {
                postLogAsync(
                    offer = adjustedOffer,
                    status = "SKIPPED",
                    skipReason = eval.skipReason,
                    eventLagMs = eventLagMs,
                    parseMs = parseMs,
                    decideMs = 0L,
                    totalToTapMs = 0L,
                    outcome = "SKIPPED",
                    appForegroundId = targetAppId
                )
            }
        }

        if (actionableCandidates.isEmpty()) {
            return
        }

        // G) SELECTION: "first_match" accepts immediately. Other strategies may wait at most 120 ms if only one card is present
        if (snapshot.multipleMatchStrategy != "first_match" && actionableCandidates.size <= 1) {
            try {
                Thread.sleep(120L)
                val moreOffers = OfferParser.fastPathParse(
                    windows = try { this.windows } catch (_: Exception) { null },
                    rootNode = try { rootInActiveWindow } catch (_: Exception) { null },
                    eventSource = null,
                    appId = targetAppId,
                    targetPackage = packageName,
                    processTestRequests = snapshot.processTestRequests,
                    screenWidth = displayMetrics.widthPixels,
                    screenHeight = displayMetrics.heightPixels
                )
                val moreActionable = moreOffers.filter { o ->
                    FilterEngine.evaluate(o, snapshot).isMatched && o.isActionable
                }
                if (moreActionable.size > actionableCandidates.size) {
                    actionableCandidates.clear()
                    actionableCandidates.addAll(moreActionable)
                }
            } catch (_: InterruptedException) {}
        }

        val chosenOffer = SelectionEngine.selectBestOffer(actionableCandidates, snapshot) ?: return

        // Log non-chosen candidates as "Better offer chosen"
        for (other in actionableCandidates) {
            if (other != chosenOffer) {
                postLogAsync(
                    offer = other,
                    status = "SKIPPED",
                    skipReason = "Better offer chosen",
                    eventLagMs = eventLagMs,
                    parseMs = parseMs,
                    decideMs = 0L,
                    totalToTapMs = 0L,
                    outcome = "SKIPPED",
                    appForegroundId = targetAppId
                )
            }
        }

        val tDecided = SystemClock.uptimeMillis()
        val decideMs = tDecided - tStartDecide

        // MODE CHECK: notify_only vs auto_accept
        if (snapshot.mode == "notify_only") {
            val totalToTapMs = (tDecided - eventTime).coerceAtLeast(0L)
            tappedFingerprints[chosenOffer.fingerprint] = now

            backgroundScope.launch {
                notificationManager.showOfferMatchedNotification(chosenOffer)
                if (snapshot.alertOnAccept) {
                    notificationManager.playAlertFeedback(sound = snapshot.soundEnabled, vibrate = snapshot.vibrationEnabled)
                }
                TripPilotApp.instance.rideLogRepository.recordOffer(
                    offer = chosenOffer,
                    status = "ACCEPTED",
                    skipReason = null,
                    tapMethod = "NOTIFY_ONLY",
                    tapLatencyMs = 0L,
                    eventLagMs = eventLagMs,
                    parseMs = parseMs,
                    decideMs = decideMs,
                    totalToTapMs = totalToTapMs,
                    outcome = "ACCEPTED",
                    now = now
                )
                lastDecisionMap[targetAppId] = "Accepted"
                publishRuntimeStatuses()
            }
            return
        }

        // TAP EXECUTION (IMMEDIATE, ZERO I/O BEFORE THE TAP)
        val tTapCalled = SystemClock.uptimeMillis()
        val tapResult = TapStrategyExecutor.executeTapImmediate(
            service = this@OfferWatcherService,
            node = chosenOffer.acceptNode,
            nodeBounds = chosenOffer.acceptNodeBounds,
            forcedMethod = snapshot.forcedTapMethod ?: "auto",
            strategyStats = cachedStrategyStats
        )
        val tTapReturn = SystemClock.uptimeMillis()
        val totalToTapMs = (tTapReturn - eventTime).coerceAtLeast(0L)

        // Record in-memory dedupe & attempts
        tappedFingerprints[chosenOffer.fingerprint] = now
        fingerprintTapAttempts[chosenOffer.fingerprint] = 1
        fingerprintLastMethod[chosenOffer.fingerprint] = tapResult.methodUsed

        // POST RESULT TO BACKGROUND DISPATCHER (STRICTLY AFTER THE TAP)
        backgroundScope.launch {
            TripPilotApp.instance.preferencesManager.recordStrategyAttempt(tapResult.methodUsed.key, tapResult.success)

            if (tapResult.success) {
                if (snapshot.alertOnAccept) {
                    notificationManager.playAlertFeedback(sound = snapshot.soundEnabled, vibrate = snapshot.vibrationEnabled)
                }
                TripPilotApp.instance.rideLogRepository.recordOffer(
                    offer = chosenOffer,
                    status = "ACCEPTED",
                    skipReason = null,
                    tapMethod = tapResult.methodUsed.name,
                    tapLatencyMs = tapResult.latencyMs,
                    eventLagMs = eventLagMs,
                    parseMs = parseMs,
                    decideMs = decideMs,
                    totalToTapMs = totalToTapMs,
                    outcome = "ACCEPTED",
                    now = now
                )
                lastDecisionMap[targetAppId] = "Accepted"
            } else {
                TripPilotApp.instance.rideLogRepository.recordOffer(
                    offer = chosenOffer,
                    status = "TAP_FAILED",
                    skipReason = "Tap execution failed",
                    tapMethod = tapResult.methodUsed.name,
                    tapLatencyMs = tapResult.latencyMs,
                    eventLagMs = eventLagMs,
                    parseMs = parseMs,
                    decideMs = decideMs,
                    totalToTapMs = totalToTapMs,
                    outcome = "TAP_FAILED",
                    now = now
                )
                lastDecisionMap[targetAppId] = "Tap Failed"
            }

            offerDetectedAppId = targetAppId
            lastOfferParsedAtMap[targetAppId] = now
            TripPilotApp.detectionState.value = "Offer detected"
            publishRuntimeStatuses()
        }

        // F) VERIFICATION RETRY: Schedule a check at +250 ms after the tap (not 1.5 s)
        engineHandler.postDelayed({
            verifyAndRetryTapIfNeeded(chosenOffer, targetAppId, packageName, snapshot)
        }, 250L)
    }

    private fun verifyAndRetryTapIfNeeded(
        chosenOffer: RideOffer,
        targetAppId: String,
        packageName: String,
        snapshot: FilterSnapshot
    ) {
        val attempts = fingerprintTapAttempts[chosenOffer.fingerprint] ?: 0
        if (attempts >= 2) return

        val displayMetrics = resources.displayMetrics
        val recheckOffers = OfferParser.fastPathParse(
            windows = try { this.windows } catch (_: Exception) { null },
            rootNode = try { rootInActiveWindow } catch (_: Exception) { null },
            eventSource = null,
            appId = targetAppId,
            targetPackage = packageName,
            processTestRequests = snapshot.processTestRequests,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels
        )

        val match = recheckOffers.firstOrNull { it.fingerprint == chosenOffer.fingerprint }
        if (match != null && match.acceptNode != null && match.acceptNode.isVisibleToUser) {
            // Still present and visible: try the next tap strategy once (NODE_CLICK -> PARENT_CLICK -> GESTURE_TAP)
            fingerprintTapAttempts[chosenOffer.fingerprint] = attempts + 1
            val lastMethod = fingerprintLastMethod[chosenOffer.fingerprint]
            val orderedMethods = listOf(
                TapMethodType.NODE_CLICK,
                TapMethodType.PARENT_CLICK,
                TapMethodType.GESTURE_TAP
            ).sortedByDescending { cachedStrategyStats[it.key]?.successRate ?: 0.5 }

            val nextMethod = orderedMethods.firstOrNull { it != lastMethod } ?: TapMethodType.GESTURE_TAP

            val retryResult = TapStrategyExecutor.executeTapImmediate(
                service = this@OfferWatcherService,
                node = match.acceptNode,
                nodeBounds = match.acceptNodeBounds,
                attemptMethod = nextMethod
            )

            backgroundScope.launch {
                TripPilotApp.instance.preferencesManager.recordStrategyAttempt(nextMethod.key, retryResult.success)
                if (retryResult.success) {
                    TripPilotApp.instance.rideLogRepository.recordOffer(
                        offer = match,
                        status = "ACCEPTED",
                        tapMethod = nextMethod.name,
                        tapLatencyMs = retryResult.latencyMs,
                        outcome = "ACCEPTED"
                    )
                }
            }
        }
    }

    private fun checkMissedOrderText(
        eventSource: AccessibilityNodeInfo?,
        rootNode: AccessibilityNodeInfo?,
        windows: List<AccessibilityWindowInfo>?
    ): Boolean {
        val missedPhrases = listOf(
            "You missed the order",
            "missed the order",
            "Sorry, this order was accepted by another captain",
            "accepted by another captain"
        )
        val roots = mutableListOf<AccessibilityNodeInfo>()
        if (eventSource != null) roots.add(eventSource)
        if (rootNode != null && !roots.contains(rootNode)) roots.add(rootNode)
        if (!windows.isNullOrEmpty()) {
            for (w in windows) {
                val r = try { w.root } catch (_: Exception) { null } ?: continue
                if (!roots.contains(r)) roots.add(r)
            }
        }

        for (root in roots) {
            for (phrase in missedPhrases) {
                val matches = try { root.findAccessibilityNodeInfosByText(phrase) } catch (_: Exception) { emptyList() }
                if (!matches.isNullOrEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun postLogAsync(
        offer: RideOffer,
        status: String,
        skipReason: String?,
        eventLagMs: Long,
        parseMs: Long,
        decideMs: Long,
        totalToTapMs: Long,
        outcome: String?,
        appForegroundId: String
    ) {
        backgroundScope.launch {
            TripPilotApp.instance.rideLogRepository.recordOffer(
                offer = offer,
                status = status,
                skipReason = skipReason,
                eventLagMs = eventLagMs,
                parseMs = parseMs,
                decideMs = decideMs,
                totalToTapMs = totalToTapMs,
                outcome = outcome,
                now = System.currentTimeMillis()
            )
            lastDecisionMap[appForegroundId] = when (status) {
                "ACCEPTED" -> "Accepted"
                "TAP_FAILED" -> "Tap Failed"
                else -> "Skipped"
            }
            publishRuntimeStatuses()
        }
    }

    private fun publishRuntimeStatuses() {
        val pm = packageManager
        val isEngineRunning = TripPilotApp.engineState.value
        val snapshot = currentSnapshot

        val targetApps = listOf(
            Triple("bharat_taxi", "Bharat Taxi", snapshot.resolvedBharatPackage),
            Triple("rapido", "Rapido", snapshot.resolvedRapidoPackage)
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

            val isEnabled = if (appId == "bharat_taxi") snapshot.isBharatTaxiEnabled else snapshot.isRapidoEnabled

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

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        TurboModeManager.release()
        engineThread.quitSafely()
        backgroundScope.cancel()
        instance = null
    }
}
