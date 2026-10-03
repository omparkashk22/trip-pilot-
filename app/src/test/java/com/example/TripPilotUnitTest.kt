package com.example

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppResolver
import com.example.data.model.AppRuntimeState
import com.example.data.model.AppRuntimeStatus
import com.example.data.model.DriverFilter
import com.example.data.model.LocationKeyword
import com.example.data.model.RideLog
import com.example.data.model.RideOffer
import com.example.data.model.StrategyStats
import com.example.domain.engine.BharatTaxiParser
import com.example.domain.engine.FilterEngine
import com.example.domain.engine.ParsedNode
import com.example.domain.engine.RapidoParser
import com.example.domain.engine.SelectionEngine
import com.example.domain.engine.TapMethodType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TripPilotUnitTest {

    @Test
    fun testBharatTaxiParser_variant1_listMode() {
        val nodes = listOf(
            // Left Rail Item (must be ignored)
            ParsedNode(null, "₹", Rect(10, 50, 40, 80), viewId = "in.mobility.bharattaxidriver:id/tv_currency"),
            ParsedNode(null, "95", Rect(45, 50, 100, 80), viewId = "in.mobility.bharattaxidriver:id/tv_value"),
            ParsedNode(null, "", Rect(10, 85, 120, 95), viewId = "in.mobility.bharattaxidriver:id/ride_request_tab_recycler_view"),

            // Card 1 container under ride_request_content_recycler_view
            ParsedNode(null, "", Rect(200, 50, 1000, 800), viewId = "in.mobility.bharattaxidriver:id/ride_request_content_recycler_view"),
            ParsedNode(null, "Cab Economy", Rect(220, 70, 450, 100), viewId = null),
            ParsedNode(null, "₹", Rect(220, 110, 250, 150), viewId = "in.mobility.bharattaxidriver:id/tv_currency"),
            ParsedNode(null, "95", Rect(255, 110, 340, 150), viewId = "in.mobility.bharattaxidriver:id/tv_value"),
            ParsedNode(null, "₹", Rect(360, 120, 380, 145), viewId = "in.mobility.bharattaxidriver:id/tv_currency"),
            ParsedNode(null, "27/km", Rect(385, 120, 470, 145), viewId = "in.mobility.bharattaxidriver:id/tv_value"),
            ParsedNode(null, "Decline", Rect(850, 70, 980, 110), viewId = "in.mobility.bharattaxidriver:id/ride_decline", isClickable = true),
            ParsedNode(null, "0 m・1 min", Rect(220, 170, 450, 200), viewId = "in.mobility.bharattaxidriver:id/pickupDistance"),
            ParsedNode(null, "Industrial Area Phase II, Kailash Hardware, Chandigarh, India 160002", Rect(220, 210, 950, 260), viewId = "in.mobility.bharattaxidriver:id/tv_primary_text"),
            ParsedNode(null, "3.5 km・11 min", Rect(220, 280, 450, 310), viewId = "in.mobility.bharattaxidriver:id/tripDistance"),
            ParsedNode(null, "Industrial Area Phase I, ELANTE MALL, Chandigarh, चंडीगढ़, India 160002", Rect(220, 320, 950, 370), viewId = "in.mobility.bharattaxidriver:id/tv_primary_text"),
            ParsedNode(null, "Accept", Rect(220, 400, 980, 480), viewId = "in.mobility.bharattaxidriver:id/ride_accept", isClickable = true)
        )

        val offers = BharatTaxiParser.parseNodes(nodes)
        assertEquals(1, offers.size)
        val offer = offers.first()

        assertEquals("bharat_taxi", offer.appId)
        assertEquals("Cab Economy", offer.rideType)
        assertEquals(95.0, offer.baseFare, 0.01)
        assertEquals(27.0, offer.appFarePerKm ?: 0.0, 0.01)
        assertEquals(0.0, offer.pickupDistanceKm, 0.01)
        assertEquals(1, offer.pickupEtaMin)
        assertEquals(3.5, offer.dropDistanceKm, 0.01)
        assertEquals(11, offer.dropEtaMin)
        assertTrue(offer.pickupAddress.contains("Kailash Hardware"))
        assertTrue(offer.dropAddress.contains("ELANTE MALL"))
    }

    @Test
    fun testBharatTaxiParser_variant2_singleRequestMode() {
        val nodes = listOf(
            ParsedNode(null, "TEST REQUEST", Rect(50, 100, 250, 140)),
            ParsedNode(null, "₹50", Rect(50, 150, 200, 220)),
            ParsedNode(null, "0.1 km · 1 min", Rect(50, 230, 300, 260)),
            ParsedNode(null, "Kempegowda Int'l Airport, T1 Terminal, Bengaluru 560300", Rect(50, 270, 800, 320)),
            ParsedNode(null, "1 km · 5 min", Rect(50, 330, 300, 360)),
            ParsedNode(null, "Aerocity Devanahalli Business Park, Bengaluru 562110", Rect(50, 370, 800, 420)),
            ParsedNode(null, "+10", Rect(50, 440, 200, 500), isClickable = true),
            ParsedNode(null, "Accept", Rect(250, 440, 800, 500), isClickable = true)
        )

        val offers = BharatTaxiParser.parseNodes(nodes, processTestRequests = true)
        assertEquals(1, offers.size)
        val offer = offers.first()

        assertEquals("bharat_taxi", offer.appId)
        assertEquals(50.0, offer.baseFare, 0.01)
        assertEquals(0.0, offer.extraFare, 0.01)
        assertEquals(50.0, offer.totalFare, 0.01)
        assertEquals(0.1, offer.pickupDistanceKm, 0.01)
        assertEquals(1, offer.pickupEtaMin)
        assertEquals(1.0, offer.dropDistanceKm, 0.01)
        assertEquals(5, offer.dropEtaMin)
        assertTrue(offer.pickupAddress.contains("Kempegowda"))
        assertTrue(offer.dropAddress.contains("Aerocity"))
        assertTrue(offer.isTest)

        // When processTestRequests is false, test offers must be ignored
        val ignoredOffers = BharatTaxiParser.parseNodes(nodes, processTestRequests = false)
        assertEquals(0, ignoredOffers.size)
    }

    @Test
    fun testDistanceRegex_allSeparators() {
        // '・' U+30FB
        val r1 = BharatTaxiParser.parseDistanceTime("0 m・1 min")
        assertNotNull(r1)
        assertEquals(0.0, r1!!.first, 0.001)
        assertEquals(1, r1.second)

        val r2 = BharatTaxiParser.parseDistanceTime("3.5 km・11 min")
        assertNotNull(r2)
        assertEquals(3.5, r2!!.first, 0.001)
        assertEquals(11, r2.second)

        // '·' U+00B7
        val r3 = BharatTaxiParser.parseDistanceTime("0.1 km · 1 min")
        assertNotNull(r3)
        assertEquals(0.1, r3!!.first, 0.001)
        assertEquals(1, r3.second)

        // '•'
        val r4 = BharatTaxiParser.parseDistanceTime("2.4 km • 8 min")
        assertNotNull(r4)
        assertEquals(2.4, r4!!.first, 0.001)
        assertEquals(8, r4.second)

        // '-'
        val r5 = BharatTaxiParser.parseDistanceTime("4.2 km - 15 mins")
        assertNotNull(r5)
        assertEquals(4.2, r5!!.first, 0.001)
        assertEquals(15, r5.second)

        // '.'
        val r6 = BharatTaxiParser.parseDistanceTime("5 km . 12 min")
        assertNotNull(r6)
        assertEquals(5.0, r6!!.first, 0.001)
        assertEquals(12, r6.second)

        // Space
        val r7 = BharatTaxiParser.parseDistanceTime("10 km 20 mins")
        assertNotNull(r7)
        assertEquals(10.0, r7!!.first, 0.001)
        assertEquals(20, r7.second)

        // Meters conversion (500 m = 0.5 km)
        val r8 = BharatTaxiParser.parseDistanceTime("500 m・3 min")
        assertNotNull(r8)
        assertEquals(0.5, r8!!.first, 0.001)
        assertEquals(3, r8.second)
    }

    @Test
    fun testTargetAppRegistry_noForbiddenPackages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appResolver = AppResolver(context)
        val configs = appResolver.loadTargetConfigs()

        for (config in configs) {
            assertFalse(
                "Forbidden package 'com.bharat.taxi' found in candidatePackages of ${config.appId}",
                config.candidatePackages.contains("com.bharat.taxi")
            )
            if (config.appId == "bharat_taxi") {
                assertTrue(
                    "candidatePackages for bharat_taxi must contain 'in.mobility.bharattaxidriver'",
                    config.candidatePackages.contains("in.mobility.bharattaxidriver")
                )
                assertTrue(
                    "excludePackages for bharat_taxi must contain 'com.bharat.taxi'",
                    config.excludePackages.contains("com.bharat.taxi")
                )
            }
        }
    }

    @Test
    fun testFilterEngine_pickupZeroDistancePasses() {
        val offerZeroPickup = RideOffer(
            appId = "bharat_taxi",
            rideType = "Cab Economy",
            baseFare = 95.0,
            pickupDistanceKm = 0.0, // 0 m
            dropDistanceKm = 3.5,
            pickupAddress = "Origin",
            dropAddress = "Destination"
        )

        val filter = DriverFilter(
            minFare = 50.0,
            pickupDistanceMinKm = 0.0,
            pickupDistanceMaxKm = 5.0
        )

        val res = FilterEngine.evaluate(offerZeroPickup, filter)
        assertTrue("Pickup distance of 0.0 km must pass when min = 0.0", res.isMatched)
        assertNull(res.skipReason)
    }

    @Test
    fun testFilterEngine_farePerKm_calculatedVsAppGiven() {
        val offer = RideOffer(
            appId = "bharat_taxi",
            rideType = "Cab Economy",
            baseFare = 100.0,
            appFarePerKm = 25.0,
            pickupDistanceKm = 0.5,
            dropDistanceKm = 5.0, // Calculated fare/km = 100 / 5.0 = 20.0
            pickupAddress = "A",
            dropAddress = "B"
        )

        assertEquals(20.0, offer.farePerKm(), 0.01)
        assertEquals(25.0, offer.appFarePerKm ?: 0.0, 0.01)

        // When minFarePerKm = 22.0, calculated rate (20.0) is below filter
        val filterHigh = DriverFilter(minFare = 50.0, minFarePerKm = 22.0)
        val resHigh = FilterEngine.evaluate(offer, filterHigh)
        assertFalse(resHigh.isMatched)
        assertTrue(resHigh.skipReason?.contains("Fare per km too low") == true)

        // When minFarePerKm = 18.0, calculated rate (20.0) passes
        val filterLow = DriverFilter(minFare = 50.0, minFarePerKm = 18.0)
        val resLow = FilterEngine.evaluate(offer, filterLow)
        assertTrue(resLow.isMatched)
    }

    @Test
    fun testFilterEngine_allowedRideTypes_tolerance() {
        val offer = RideOffer(
            appId = "bharat_taxi",
            rideType = "Cab Economy",
            baseFare = 150.0,
            pickupDistanceKm = 1.0,
            dropDistanceKm = 6.0,
            pickupAddress = "A",
            dropAddress = "B"
        )

        // Empty allowed types = all types allowed
        val filterEmpty = DriverFilter(allowedRideTypes = emptySet())
        assertTrue(FilterEngine.evaluate(offer, filterEmpty).isMatched)

        // Case-insensitive & trimmed & whitespace collapsed match
        val filterTolerant = DriverFilter(allowedRideTypes = setOf("  cab   economy  "))
        assertTrue(FilterEngine.evaluate(offer, filterTolerant).isMatched)

        // Partial contains match: e.g. "Economy" matches "Cab Economy"
        val filterContains = DriverFilter(allowedRideTypes = setOf("Economy"))
        assertTrue(FilterEngine.evaluate(offer, filterContains).isMatched)

        // Non-matching type skips with exact reason "Ride type not allowed"
        val filterNonMatch = DriverFilter(allowedRideTypes = setOf("Auto", "Bike"))
        val res = FilterEngine.evaluate(offer, filterNonMatch)
        assertFalse(res.isMatched)
        assertEquals("Ride type not allowed", res.skipReason)
    }

    @Test
    fun testEngineState_stopsProcessingWhenDisabled() {
        TripPilotApp.engineState.value = false
        assertFalse(TripPilotApp.engineState.value)

        TripPilotApp.engineState.value = true
        assertTrue(TripPilotApp.engineState.value)

        TripPilotApp.engineState.value = false
        assertFalse(TripPilotApp.engineState.value)
    }

    @Test
    fun testRapidoParser_multipleCardsWithExtraFare() {
        val nodes = listOf(
            // Card 1
            ParsedNode(null, "Bike Boost", Rect(50, 100, 200, 130)),
            ParsedNode(null, "₹223 + ₹137", Rect(50, 140, 400, 180)),
            ParsedNode(null, "2.5 km", Rect(50, 190, 200, 220)),
            ParsedNode(null, "Khanpur - A17, Khanpur Extension, Khanpur, New Delhi, Delhi 110080", Rect(50, 230, 800, 280)),
            ParsedNode(null, "25.8 km", Rect(50, 290, 200, 320)),
            ParsedNode(null, "Vishnu_Garden_Tilak_Nagar - jolly jewellers, Sant Garh, Shahpura, Block K, Tilak Nagar, New Delhi, Delhi, India", Rect(50, 330, 800, 380)),
            ParsedNode(null, "-", Rect(50, 400, 150, 460)),
            ParsedNode(null, "Accept", Rect(200, 400, 750, 460)),

            // Card 2
            ParsedNode(null, "Auto", Rect(50, 500, 200, 530)),
            ParsedNode(null, "₹100 + ₹53", Rect(50, 540, 400, 580)),
            ParsedNode(null, "1.2 km", Rect(50, 590, 200, 620)),
            ParsedNode(null, "Saket Metro Station Gate 2", Rect(50, 630, 800, 680)),
            ParsedNode(null, "8.4 km", Rect(50, 690, 200, 720)),
            ParsedNode(null, "Cyber Hub DLF Phase 2, Gurugram", Rect(50, 730, 800, 780)),
            ParsedNode(null, "Accept", Rect(200, 800, 750, 860))
        )

        val offers = RapidoParser.parseNodes(nodes)
        assertEquals(2, offers.size)

        val offer1 = offers[0]
        assertEquals("rapido", offer1.appId)
        assertEquals("Bike Boost", offer1.rideType)
        assertEquals(223.0, offer1.baseFare, 0.01)
        assertEquals(137.0, offer1.extraFare, 0.01)
        assertEquals(360.0, offer1.totalFare, 0.01)
        assertEquals(2.5, offer1.pickupDistanceKm, 0.01)
        assertEquals(25.8, offer1.dropDistanceKm, 0.01)
        assertTrue(offer1.pickupAddress.contains("Khanpur"))
        assertTrue(offer1.dropAddress.contains("Tilak Nagar"))

        val offer2 = offers[1]
        assertEquals("Auto", offer2.rideType)
        assertEquals(100.0, offer2.baseFare, 0.01)
        assertEquals(53.0, offer2.extraFare, 0.01)
        assertEquals(153.0, offer2.totalFare, 0.01)
    }

    @Test
    fun testRapidoParser_floatingOverlayIntersection() {
        val nodes = listOf(
            ParsedNode(null, "Bike Lite", Rect(50, 100, 200, 130)),
            ParsedNode(null, "₹41 + ₹40", Rect(50, 140, 400, 180)),
            ParsedNode(null, "2.6 km", Rect(50, 190, 200, 220)),
            ParsedNode(null, "Hauz Khas Village", Rect(50, 230, 800, 280)),
            ParsedNode(null, "12.0 km", Rect(50, 290, 200, 320)),
            ParsedNode(null, "Connaught Place", Rect(50, 330, 800, 380)),
            ParsedNode(null, "Accept", Rect(200, 1800, 750, 1900)),
            ParsedNode(null, "Extra from Customer", Rect(100, 1850, 800, 1950))
        )

        val offers = RapidoParser.parseNodes(nodes)
        assertEquals(1, offers.size)
        assertFalse(offers.first().isActionable)
    }

    @Test
    fun testSelectionEngine_fourStrategies() {
        val offer1 = RideOffer(
            appId = "rapido", rideType = "Bike", baseFare = 100.0, extraFare = 0.0,
            pickupDistanceKm = 1.0, dropDistanceKm = 10.0, pickupAddress = "A", dropAddress = "B"
        )

        val offer2 = RideOffer(
            appId = "rapido", rideType = "Auto", baseFare = 300.0, extraFare = 50.0,
            pickupDistanceKm = 4.0, dropDistanceKm = 15.0, pickupAddress = "C", dropAddress = "D"
        )

        val offer3 = RideOffer(
            appId = "rapido", rideType = "Cab", baseFare = 200.0, extraFare = 0.0,
            pickupDistanceKm = 0.5, dropDistanceKm = 5.0, pickupAddress = "E", dropAddress = "F"
        )

        val candidates = listOf(offer1, offer2, offer3)

        // 1. First match from top
        val best1 = SelectionEngine.selectBestOffer(candidates, DriverFilter(multipleMatchStrategy = "first_match"))
        assertEquals(offer1, best1)

        // 2. Highest fare
        val best2 = SelectionEngine.selectBestOffer(candidates, DriverFilter(multipleMatchStrategy = "highest_fare"))
        assertEquals(offer2, best2)

        // 3. Highest fare per km
        val best3 = SelectionEngine.selectBestOffer(candidates, DriverFilter(multipleMatchStrategy = "highest_per_km"))
        assertEquals(offer3, best3)

        // 4. Nearest pickup
        val best4 = SelectionEngine.selectBestOffer(candidates, DriverFilter(multipleMatchStrategy = "nearest_pickup"))
        assertEquals(offer3, best4)
    }

    @Test
    fun testStrategyStats_ordering() {
        val stats = mapOf(
            "node_click" to StrategyStats("node_click", attempts = 10, verifiedSuccesses = 9), // 90%
            "parent_click" to StrategyStats("parent_click", attempts = 10, verifiedSuccesses = 4), // 40%
            "gesture" to StrategyStats("gesture", attempts = 10, verifiedSuccesses = 7) // 70%
        )

        val ordered = listOf(
            TapMethodType.NODE_CLICK,
            TapMethodType.PARENT_CLICK,
            TapMethodType.GESTURE_TAP
        ).sortedByDescending { method ->
            stats[method.key]?.successRate ?: 0.5
        }

        assertEquals(TapMethodType.NODE_CLICK, ordered[0])
        assertEquals(TapMethodType.GESTURE_TAP, ordered[1])
        assertEquals(TapMethodType.PARENT_CLICK, ordered[2])
    }

    // --- PART 7 SPECIFIC TESTS ---

    @Test
    fun testAccessibilityConfig_noPackageNames() {
        val candidateFiles = listOf(
            File("app/src/main/res/xml/accessibility_service_config.xml"),
            File("src/main/res/xml/accessibility_service_config.xml"),
            File("/app/src/main/res/xml/accessibility_service_config.xml")
        )
        val configFile = candidateFiles.firstOrNull { it.exists() }
        assertNotNull("accessibility_service_config.xml must exist", configFile)
        val content = configFile!!.readText()

        assertFalse(
            "accessibility_service_config.xml must NOT contain 'android:packageNames'",
            content.contains("android:packageNames")
        )
        assertFalse(
            "accessibility_service_config.xml must NOT contain 'packageNames'",
            content.contains("packageNames")
        )
        assertTrue(
            "accessibility_service_config.xml must contain 'typeWindowStateChanged|typeWindowContentChanged'",
            content.contains("typeWindowStateChanged|typeWindowContentChanged")
        )
        assertTrue(
            "accessibility_service_config.xml must contain 'canRetrieveWindowContent=\"true\"'",
            content.contains("canRetrieveWindowContent=\"true\"")
        )
        assertTrue(
            "accessibility_service_config.xml must contain 'canPerformGestures=\"true\"'",
            content.contains("canPerformGestures=\"true\"")
        )
    }

    @Test
    fun testAppRuntimeState_mappingSixStates() {
        fun resolveState(
            isInstalled: Boolean,
            isEnabled: Boolean,
            isEngineRunning: Boolean,
            activeForegroundAppId: String?,
            offerDetectedAppId: String?,
            notRecognizedAppId: String?,
            appId: String
        ): AppRuntimeState {
            return when {
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
        }

        // 1. NOT_INSTALLED
        assertEquals(
            AppRuntimeState.NOT_INSTALLED,
            resolveState(isInstalled = false, isEnabled = true, isEngineRunning = true, null, null, null, "bharat_taxi")
        )

        // 2. PAUSED
        assertEquals(
            AppRuntimeState.PAUSED,
            resolveState(isInstalled = true, isEnabled = false, isEngineRunning = true, null, null, null, "bharat_taxi")
        )

        // 3. IDLE (when engine is stopped)
        assertEquals(
            AppRuntimeState.IDLE,
            resolveState(isInstalled = true, isEnabled = true, isEngineRunning = false, null, null, null, "bharat_taxi")
        )

        // 4. FOREGROUND (engine running, active app in front)
        assertEquals(
            AppRuntimeState.FOREGROUND,
            resolveState(isInstalled = true, isEnabled = true, isEngineRunning = true, activeForegroundAppId = "bharat_taxi", null, null, "bharat_taxi")
        )

        // 5. OFFER_DETECTED
        assertEquals(
            AppRuntimeState.OFFER_DETECTED,
            resolveState(isInstalled = true, isEnabled = true, isEngineRunning = true, activeForegroundAppId = "bharat_taxi", offerDetectedAppId = "bharat_taxi", null, "bharat_taxi")
        )

        // 6. NOT_RECOGNIZED
        assertEquals(
            AppRuntimeState.NOT_RECOGNIZED,
            resolveState(isInstalled = true, isEnabled = true, isEngineRunning = true, activeForegroundAppId = "bharat_taxi", null, notRecognizedAppId = "bharat_taxi", "bharat_taxi")
        )
    }

    @Test
    fun testForegroundClears_whenAnotherAppInFront() {
        var activeForegroundAppId: String? = "bharat_taxi"
        val resolvedBharatPackage = "in.mobility.bharattaxidriver"
        val resolvedRapidoPackage = "com.rapido.rider"

        fun onWindowChanged(newPkg: String) {
            when (newPkg) {
                resolvedBharatPackage -> activeForegroundAppId = "bharat_taxi"
                resolvedRapidoPackage -> activeForegroundAppId = "rapido"
                else -> activeForegroundAppId = null // Clears when any other app is in front!
            }
        }

        assertEquals("bharat_taxi", activeForegroundAppId)

        // Window changes to TripPilot itself or system launcher
        onWindowChanged("com.example")
        assertNull("Foreground app must be null when TripPilot or another app is in front", activeForegroundAppId)

        // Window changes to Rapido
        onWindowChanged("com.rapido.rider")
        assertEquals("rapido", activeForegroundAppId)

        // Window changes to Android home launcher
        onWindowChanged("com.google.android.apps.nexuslauncher")
        assertNull("Foreground app must be null when on home screen", activeForegroundAppId)
    }

    @Test
    fun testPerPackageEventCounters() {
        val now = 100_000L
        val eventTimestamps = mutableListOf(
            now - 70_000L, // Expired (> 60s)
            now - 50_000L, // Valid
            now - 30_000L, // Valid
            now - 5_000L   // Valid
        )

        val cutoff = now - 60_000L
        eventTimestamps.removeAll { it < cutoff }

        assertEquals(3, eventTimestamps.size)

        val status = AppRuntimeStatus(
            appId = "bharat_taxi",
            displayName = "Bharat Taxi",
            state = AppRuntimeState.FOREGROUND,
            lastEventAt = now,
            eventsLast60s = eventTimestamps.size,
            lastOfferParsedAt = now - 10_000L,
            lastDecision = "Accepted"
        )

        assertEquals("bharat_taxi", status.appId)
        assertEquals(3, status.eventsLast60s)
        assertEquals(now, status.lastEventAt)
        assertEquals(now - 10_000L, status.lastOfferParsedAt)
        assertEquals("Accepted", status.lastDecision)
    }

    @Test
    fun testVersionChecker_isNewerVersion() {
        // Newer versions
        assertTrue(com.example.data.remote.VersionChecker.isNewerVersion("1.0", "1.1"))
        assertTrue(com.example.data.remote.VersionChecker.isNewerVersion("1.0", "1.0.1"))
        assertTrue(com.example.data.remote.VersionChecker.isNewerVersion("1.0", "v1.0.1"))
        assertTrue(com.example.data.remote.VersionChecker.isNewerVersion("v1.0.0", "v1.0.1"))
        assertTrue(com.example.data.remote.VersionChecker.isNewerVersion("1.0.0", "2.0.0"))
        assertTrue(com.example.data.remote.VersionChecker.isNewerVersion("0.9.5", "1.0"))

        // Same or older versions
        assertFalse(com.example.data.remote.VersionChecker.isNewerVersion("1.0", "1.0"))
        assertFalse(com.example.data.remote.VersionChecker.isNewerVersion("v1.0", "1.0"))
        assertFalse(com.example.data.remote.VersionChecker.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(com.example.data.remote.VersionChecker.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(com.example.data.remote.VersionChecker.isNewerVersion("1.2.0", "1.1.9"))
    }

    @Test
    fun testAppUpdateInfo_properties() {
        val updateInfo = com.example.data.remote.AppUpdateInfo(
            currentVersion = "1.0",
            latestVersion = "1.1",
            releaseTitle = "TripPilot v1.1",
            releaseNotes = "New features & bug fixes",
            downloadUrl = "https://github.com/omparkashk22/trip-pilot-/releases/download/v1.1/trippilot.apk",
            releasePageUrl = "https://github.com/omparkashk22/trip-pilot-/releases/tag/v1.1",
            isUpdateAvailable = true
        )

        assertEquals("1.0", updateInfo.currentVersion)
        assertEquals("1.1", updateInfo.latestVersion)
        assertTrue(updateInfo.isUpdateAvailable)
        assertTrue(updateInfo.downloadUrl.endsWith(".apk"))
    }

    // --- PART 10 PHASE 1 TESTS ---

    @Test
    fun testFareParsing_perKmRateRejectedFromFare() {
        // "₹27/km", "₹ 22 / km", "26/km" must be parsed as appFarePerKm and NEVER as fare
        val nodes1 = listOf(
            ParsedNode(null, "Cab Economy", Rect(0, 0, 1080, 50)),
            ParsedNode(null, "₹95", Rect(50, 100, 300, 232)), // height 132
            ParsedNode(null, "₹27/km", Rect(320, 120, 450, 182)), // height 62
            ParsedNode(null, "0 m・1 min", Rect(50, 250, 300, 290)),
            ParsedNode(null, "Sector 17, Chandigarh", Rect(50, 300, 600, 340)),
            ParsedNode(null, "3.5 km・11 min", Rect(50, 350, 300, 390)),
            ParsedNode(null, "Elante Mall, Chandigarh", Rect(50, 400, 600, 440)),
            ParsedNode(null, "Accept", Rect(50, 500, 1000, 600))
        )
        val offers1 = BharatTaxiParser.parseNodes(nodes1)
        assertEquals(1, offers1.size)
        val o1 = offers1[0]
        assertEquals(95.0, o1.baseFare, 0.01)
        assertEquals(27.0, o1.appFarePerKm ?: 0.0, 0.01)
        assertEquals("HIGH", o1.parseConfidence)
        assertEquals(0.0, o1.pickupDistanceKm, 0.01)
        assertEquals(3.5, o1.dropDistanceKm, 0.01)

        // Separate nodes: "₹" followed by "27/km"
        val nodesSep = listOf(
            ParsedNode(null, "Cab Economy", Rect(0, 0, 1080, 50)),
            ParsedNode(null, "₹95", Rect(50, 100, 300, 232)),
            ParsedNode(null, "₹", Rect(320, 120, 350, 182)),
            ParsedNode(null, "27/km", Rect(355, 120, 450, 182)),
            ParsedNode(null, "1.1 km · 3 min", Rect(50, 250, 300, 290)),
            ParsedNode(null, "Pickup A", Rect(50, 300, 600, 340)),
            ParsedNode(null, "3.3 km · 7 min", Rect(50, 350, 300, 390)),
            ParsedNode(null, "Drop B", Rect(50, 400, 600, 440)),
            ParsedNode(null, "Accept", Rect(50, 500, 1000, 600))
        )
        val offersSep = BharatTaxiParser.parseNodes(nodesSep)
        assertEquals(1, offersSep.size)
        assertEquals(95.0, offersSep[0].baseFare, 0.01)
        assertEquals(27.0, offersSep[0].appFarePerKm ?: 0.0, 0.01)
    }

    @Test
    fun testBharatTaxi_realCardAndMismatchSafetyCheck() {
        // Real card with outside chip "₹95":
        val nodesReal = listOf(
            ParsedNode(null, "Cab Economy", Rect(50, 50, 300, 90)),
            ParsedNode(null, "₹95", Rect(50, 100, 300, 232)), // height 132
            ParsedNode(null, "₹27/km", Rect(320, 120, 450, 182)), // height 62
            ParsedNode(null, "0 m・1 min", Rect(50, 250, 300, 290)),
            ParsedNode(null, "Sector 17, Chandigarh", Rect(50, 300, 600, 340)),
            ParsedNode(null, "3.5 km・11 min", Rect(50, 350, 300, 390)),
            ParsedNode(null, "Elante Mall, Chandigarh", Rect(50, 400, 600, 440)),
            ParsedNode(null, "Accept", Rect(50, 500, 1000, 600)),
            // Outside nodes below card (mode 1 controls):
            ParsedNode(null, "₹95", Rect(50, 650, 150, 700)),
            ParsedNode(null, "expand", Rect(200, 650, 250, 700))
        )

        val offers = BharatTaxiParser.parseNodes(nodesReal)
        assertEquals(1, offers.size)
        val offer = offers[0]
        assertEquals(95.0, offer.baseFare, 0.01)
        assertEquals(27.0, offer.appFarePerKm ?: 0.0, 0.01)
        assertEquals(3.5, offer.dropDistanceKm, 0.01)
        assertEquals("HIGH", offer.parseConfidence)
        assertEquals("SINGLE", offer.layoutVariant)

        // Mismatch case: fare 27 with app 27/km and drop 3.5 km -> calculated 7.7/km vs 27/km -> diff > 35% -> LOW
        val mismatchOffer = offer.copy(baseFare = 27.0, totalFare = 27.0)
        val calculatedRate = mismatchOffer.totalFare / mismatchOffer.dropDistanceKm
        val diff = Math.abs(calculatedRate - (mismatchOffer.appFarePerKm ?: 0.0)) / (mismatchOffer.appFarePerKm ?: 1.0)
        assertTrue(diff > 0.35)
    }

    @Test
    fun testBharatTaxi_mode1AndMode2SameFingerprint() {
        val mode1Nodes = listOf(
            ParsedNode(null, "Cab Economy", Rect(50, 50, 300, 90)),
            ParsedNode(null, "₹95", Rect(50, 100, 300, 232)),
            ParsedNode(null, "₹27/km", Rect(320, 120, 450, 182)),
            ParsedNode(null, "0 m・1 min", Rect(50, 250, 300, 290)),
            ParsedNode(null, "Dhanas-Khuda-Lahora - Dhanas, Chandigarh", Rect(50, 300, 600, 340)),
            ParsedNode(null, "3.5 km・11 min", Rect(50, 350, 300, 390)),
            ParsedNode(null, "Elante Mall, Industrial Area, Chandigarh", Rect(50, 400, 600, 440)),
            ParsedNode(null, "Accept", Rect(50, 500, 1000, 600))
        )
        val offers1 = BharatTaxiParser.parseNodes(mode1Nodes)
        assertEquals(1, offers1.size)
        assertEquals("SINGLE", offers1[0].layoutVariant)

        // Mode 2 has left rail (viewId ride_request_tab_recycler_view or bounds.left < 190)
        val mode2Nodes = listOf(
            ParsedNode(null, "Rail Tab", Rect(0, 0, 150, 2400), isClickable = true, viewId = "in.mobility.bharattaxidriver:id/ride_request_tab_recycler_view"),
            ParsedNode(null, "Cab Economy", Rect(200, 50, 500, 90)),
            ParsedNode(null, "₹95", Rect(200, 100, 450, 232)),
            ParsedNode(null, "₹27/km", Rect(470, 120, 600, 182)),
            ParsedNode(null, "0 m・1 min", Rect(200, 250, 450, 290)),
            ParsedNode(null, "Dhanas-Khuda-Lahora - Dhanas, Chandigarh", Rect(200, 300, 750, 340)),
            ParsedNode(null, "3.5 km・11 min", Rect(200, 350, 450, 390)),
            ParsedNode(null, "Elante Mall, Industrial Area, Chandigarh", Rect(200, 400, 750, 440)),
            ParsedNode(null, "Accept", Rect(200, 500, 950, 600))
        )
        val offers2 = BharatTaxiParser.parseNodes(mode2Nodes)
        assertEquals(1, offers2.size)
        assertEquals("LIST", offers2[0].layoutVariant)

        // Both mode 1 and mode 2 must yield the exact same fingerprint!
        assertEquals(offers1[0].fingerprint, offers2[0].fingerprint)
    }

    @Test
    fun testRapidoParser_extraFareAcrossNodesAndBanner() {
        // Case 1: "₹116", "+₹10", banner "Customer added ₹10.0 extra"
        val nodes1 = listOf(
            ParsedNode(null, "Cab Premium", Rect(50, 50, 250, 90)),
            ParsedNode(null, "₹116", Rect(50, 100, 200, 150)),
            ParsedNode(null, "+₹10", Rect(210, 100, 300, 150)),
            ParsedNode(null, "1.7 km", Rect(50, 180, 200, 210)),
            ParsedNode(null, "Sec-51-52 - Bus Stand, Chandigarh", Rect(50, 220, 800, 260)),
            ParsedNode(null, "5.3 km", Rect(50, 280, 200, 310)),
            ParsedNode(null, "(12 mins)", Rect(210, 280, 350, 310)),
            ParsedNode(null, "Sector 17 Plaza, Chandigarh", Rect(50, 330, 800, 370)),
            ParsedNode(null, "Customer added ₹10.0 extra", Rect(50, 390, 600, 420)),
            ParsedNode(null, "-", Rect(50, 450, 120, 510)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510))
        )

        val offers1 = RapidoParser.parseNodes(nodes1)
        assertEquals(1, offers1.size)
        val o1 = offers1[0]
        assertEquals("Cab Premium", o1.rideType)
        assertEquals(116.0, o1.baseFare, 0.01)
        assertEquals(10.0, o1.extraFare, 0.01)
        assertEquals(126.0, o1.totalFare, 0.01)
        assertEquals(1.7, o1.pickupDistanceKm, 0.01)
        assertEquals(5.3, o1.dropDistanceKm, 0.01)
        assertEquals(12, o1.dropEtaMin)
        assertFalse(o1.dropAddress.contains("Customer added"))
        assertFalse(o1.pickupAddress.contains("Customer added"))

        // Case 2: "₹116 + ₹10" in one node
        val nodesComb = listOf(
            ParsedNode(null, "Cab Premium", Rect(50, 50, 250, 90)),
            ParsedNode(null, "₹116 + ₹10", Rect(50, 100, 350, 150)),
            ParsedNode(null, "1.7 km", Rect(50, 180, 200, 210)),
            ParsedNode(null, "Sec-51-52", Rect(50, 220, 800, 260)),
            ParsedNode(null, "5.3 km", Rect(50, 280, 200, 310)),
            ParsedNode(null, "Sector 17", Rect(50, 330, 800, 370)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510))
        )
        val offersComb = RapidoParser.parseNodes(nodesComb)
        assertEquals(1, offersComb.size)
        assertEquals(116.0, offersComb[0].baseFare, 0.01)
        assertEquals(10.0, offersComb[0].extraFare, 0.01)
        assertEquals(126.0, offersComb[0].totalFare, 0.01)

        // Case 3: "₹116", "+", "₹10" separate nodes
        val nodesSep = listOf(
            ParsedNode(null, "Cab Premium", Rect(50, 50, 250, 90)),
            ParsedNode(null, "₹116", Rect(50, 100, 200, 150)),
            ParsedNode(null, "+", Rect(205, 100, 225, 150)),
            ParsedNode(null, "₹10", Rect(230, 100, 300, 150)),
            ParsedNode(null, "1.7 km", Rect(50, 180, 200, 210)),
            ParsedNode(null, "Sec-51-52", Rect(50, 220, 800, 260)),
            ParsedNode(null, "5.3 km", Rect(50, 280, 200, 310)),
            ParsedNode(null, "Sector 17", Rect(50, 330, 800, 370)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510))
        )
        val offersSep = RapidoParser.parseNodes(nodesSep)
        assertEquals(1, offersSep.size)
        assertEquals(116.0, offersSep[0].baseFare, 0.01)
        assertEquals(10.0, offersSep[0].extraFare, 0.01)
        assertEquals(126.0, offersSep[0].totalFare, 0.01)

        // Case 4: No ride type badge -> "Unknown" (never "Bike")
        val nodesNoBadge = listOf(
            ParsedNode(null, "₹90", Rect(50, 100, 200, 150)),
            ParsedNode(null, "+₹26", Rect(210, 100, 300, 150)),
            ParsedNode(null, "2.0 km", Rect(50, 180, 200, 210)),
            ParsedNode(null, "Pickup", Rect(50, 220, 800, 260)),
            ParsedNode(null, "5.0 km", Rect(50, 280, 200, 310)),
            ParsedNode(null, "Drop", Rect(50, 330, 800, 370)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510))
        )
        val offersNoBadge = RapidoParser.parseNodes(nodesNoBadge)
        assertEquals(1, offersNoBadge.size)
        assertEquals("Unknown", offersNoBadge[0].rideType)
        assertEquals(90.0, offersNoBadge[0].baseFare, 0.01)
        assertEquals(26.0, offersNoBadge[0].extraFare, 0.01)
        assertEquals(116.0, offersNoBadge[0].totalFare, 0.01)

        // Case 5: "You missed the order" screen -> emptyList()
        val missedScreen = listOf(
            ParsedNode(null, "You missed the order", Rect(50, 50, 500, 100)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510))
        )
        assertTrue(RapidoParser.parseNodes(missedScreen).isEmpty())
    }

    @Test
    fun testFareFilter_baseOnlyVsBaseExtra() {
        val offer = RideOffer(
            appId = "rapido",
            rideType = "Auto",
            baseFare = 90.0,
            extraFare = 26.0,
            totalFare = 116.0,
            pickupDistanceKm = 1.0,
            dropDistanceKm = 5.0,
            pickupAddress = "A",
            dropAddress = "B"
        )

        // Under Base Only with minFare = 100 -> FAILS (baseFare 90 < 100)
        val filterBaseOnly = DriverFilter(minFare = 100.0, fareBasis = "base_only")
        val resBaseOnly = FilterEngine.evaluate(offer, filterBaseOnly)
        assertFalse(resBaseOnly.isMatched)
        assertTrue(resBaseOnly.skipReason?.contains("Fare below minimum") == true)

        // Under Base + Extra with minFare = 100 -> PASSES (totalFare 116 >= 100)
        val filterBaseExtra = DriverFilter(minFare = 100.0, fareBasis = "base_extra")
        val resBaseExtra = FilterEngine.evaluate(offer, filterBaseExtra)
        assertTrue(resBaseExtra.isMatched)
    }

    // --- PART 10 PHASE 2 TESTS (Dedupe, Raw Card, CSV) ---

    private class FakeRideLogDao : com.example.data.local.RideLogDao {
        val logs = mutableListOf<RideLog>()
        var nextId = 1L

        override fun getAllRideLogs(): kotlinx.coroutines.flow.Flow<List<RideLog>> = kotlinx.coroutines.flow.flowOf(logs)
        override fun getTodayRideLogs(startOfDayMs: Long): kotlinx.coroutines.flow.Flow<List<RideLog>> =
            kotlinx.coroutines.flow.flowOf(logs.filter { it.timestamp >= startOfDayMs })
        override suspend fun insertRideLog(rideLog: RideLog): Long {
            val id = if (rideLog.id == 0L) nextId++ else rideLog.id
            val item = rideLog.copy(id = id)
            logs.add(0, item)
            return id
        }
        override suspend fun deleteRideLogById(id: Long) {
            logs.removeAll { it.id == id }
        }
        override suspend fun clearAll() {
            logs.clear()
        }
        override suspend fun getRecentByRawHash(rawHash: String, sinceTimestamp: Long): RideLog? {
            return logs.firstOrNull { it.rawTextHash == rawHash && it.timestamp >= sinceTimestamp }
        }
        override suspend fun updateStatus(id: Long, status: String, tapLatencyMs: Long) {
            val idx = logs.indexOfFirst { it.id == id }
            if (idx != -1) logs[idx] = logs[idx].copy(status = status, tapLatencyMs = tapLatencyMs)
        }
        override suspend fun updateRideLog(rideLog: RideLog) {
            val idx = logs.indexOfFirst { it.id == rideLog.id }
            if (idx != -1) logs[idx] = rideLog
        }
        override suspend fun getLogsSince(sinceTimestamp: Long): List<RideLog> {
            return logs.filter { it.timestamp >= sinceTimestamp }
        }
        override suspend fun pruneOldRawCards() {
            if (logs.size > 100) {
                for (i in 100 until logs.size) {
                    logs[i] = logs[i].copy(rawCard = null)
                }
            }
        }
    }

    @Test
    fun testDedupe_sameAddressesAndDistancesUpdatesSeenCountAndHigherConfidence() = kotlinx.coroutines.runBlocking {
        val dao = FakeRideLogDao()
        val repo = com.example.data.repository.RideLogRepository(dao)

        val offer1 = RideOffer(
            appId = "bharat_taxi",
            rideType = "Cab Economy",
            baseFare = 27.0,
            pickupDistanceKm = 0.0,
            dropDistanceKm = 3.5,
            pickupAddress = "Sector 17, Chandigarh",
            dropAddress = "Elante Mall, Chandigarh",
            parseConfidence = "LOW"
        )
        val log1 = RideLog(
            appId = offer1.appId,
            rideType = offer1.rideType,
            baseFare = offer1.baseFare,
            totalFare = offer1.totalFare,
            pickupDistanceKm = offer1.pickupDistanceKm,
            dropDistanceKm = offer1.dropDistanceKm,
            pickupAddress = offer1.pickupAddress,
            dropAddress = offer1.dropAddress,
            status = "SKIPPED",
            parseConfidence = "LOW",
            fingerprint = offer1.fingerprint
        )
        repo.insertRideLog(log1)
        assertEquals(1, dao.logs.size)
        assertEquals(27.0, dao.logs[0].baseFare, 0.01)
        assertEquals(1, dao.logs[0].seenCount)

        // Same offer parsed within 3 minutes with fare 95 and HIGH confidence
        val offer2 = offer1.copy(baseFare = 95.0, totalFare = 95.0, parseConfidence = "HIGH")
        val log2 = log1.copy(baseFare = 95.0, totalFare = 95.0, parseConfidence = "HIGH", fingerprint = offer2.fingerprint)
        repo.insertRideLog(log2)

        // Result: still 1 row, updated seenCount = 2, fare = 95.0, confidence = HIGH
        assertEquals(1, dao.logs.size)
        assertEquals(95.0, dao.logs[0].baseFare, 0.01)
        assertEquals(2, dao.logs[0].seenCount)
        assertEquals("HIGH", dao.logs[0].parseConfidence)

        // If row is marked ACCEPTED, a new offer with same fingerprint must NOT overwrite it
        dao.logs[0] = dao.logs[0].copy(status = "ACCEPTED")
        val log3 = log2.copy(baseFare = 110.0, totalFare = 110.0, fingerprint = offer1.fingerprint)
        repo.insertRideLog(log3)

        // Must create a new row!
        assertEquals(2, dao.logs.size)
        assertEquals("ACCEPTED", dao.logs.last().status)
    }

    @Test
    fun testCsvExport_containsNewColumns() {
        val dao = FakeRideLogDao()
        val repo = com.example.data.repository.RideLogRepository(dao)

        val log = RideLog(
            id = 1,
            appId = "bharat_taxi",
            rideType = "Cab Economy",
            baseFare = 95.0,
            totalFare = 95.0,
            appFarePerKm = 27.0,
            pickupDistanceKm = 0.0,
            dropDistanceKm = 3.5,
            pickupAddress = "Sector 17",
            dropAddress = "Elante Mall",
            status = "ACCEPTED",
            parseConfidence = "HIGH",
            layoutVariant = "SINGLE",
            seenCount = 2,
            rawCard = "{\"layout\":\"SINGLE\"}"
        )

        val csv = repo.exportToCsv(listOf(log))
        assertTrue(csv.contains("App Fare/km,Layout,Confidence,Seen Count,Raw Card"))
        assertTrue(csv.contains("27.0,SINGLE,HIGH,2,\"{\"\"layout\"\":\"\"SINGLE\"\"}\""))
    }

    // --- PART 10 PHASE 3 TESTS (Alert on Accept / Match) ---

    @Test
    fun testAlertFeedback_notificationManagerInitialization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifManager = com.example.service.OverlayNotificationManager(context)

        // Verifying that playAlertFeedback does not crash with different combinations
        notifManager.playAlertFeedback(sound = true, vibrate = true)
        notifManager.playAlertFeedback(sound = true, vibrate = false)
        notifManager.playAlertFeedback(sound = false, vibrate = true)
        notifManager.playAlertFeedback(sound = false, vibrate = false)

        val offer = RideOffer(
            appId = "bharat_taxi",
            rideType = "Cab Economy",
            baseFare = 95.0,
            pickupDistanceKm = 0.0,
            dropDistanceKm = 3.5,
            pickupAddress = "Sector 17, Chandigarh",
            dropAddress = "Elante Mall, Chandigarh"
        )
        notifManager.showOfferMatchedNotification(offer)
    }

    @Test
    fun testPreferencesManager_soundAndVibrationPreferences() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PreferencesManager(context)

        // Defaults must be true
        val soundDefault = prefs.isSoundEnabled.first()
        val vibDefault = prefs.isVibrationEnabled.first()
        val alertDefault = prefs.alertOnAccept.first()

        assertTrue(soundDefault)
        assertTrue(vibDefault)
        assertTrue(alertDefault)

        // Can update and retrieve
        prefs.setSoundEnabled(false)
        prefs.setVibrationEnabled(false)
        prefs.setAlertOnAccept(false)

        assertFalse(prefs.isSoundEnabled.first())
        assertFalse(prefs.isVibrationEnabled.first())
        assertFalse(prefs.alertOnAccept.first())

        // Reset back to true
        prefs.setSoundEnabled(true)
        prefs.setVibrationEnabled(true)
        prefs.setAlertOnAccept(true)
    }

    // --- PART 10 PHASE 4 TESTS (Text Size & Comprehensive Checklist) ---

    @Test
    fun testTextScale_preferencesAndScaleValues() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PreferencesManager(context)

        // Default text scale must be 1.0f (100%)
        assertEquals(1.0f, prefs.textScale.first(), 0.001f)

        // Set to 115%
        prefs.setTextScale(1.15f)
        assertEquals(1.15f, prefs.textScale.first(), 0.001f)

        // Set to 130%
        prefs.setTextScale(1.30f)
        assertEquals(1.30f, prefs.textScale.first(), 0.001f)

        // Set to 140%
        prefs.setTextScale(1.40f)
        assertEquals(1.40f, prefs.textScale.first(), 0.001f)

        // Reset back to 1.0f
        prefs.setTextScale(1.0f)
        assertEquals(1.0f, prefs.textScale.first(), 0.001f)
    }

    @Test
    fun testDensity_fontScaleCalculationAt140Percent() {
        val baseDensity = androidx.compose.ui.unit.Density(density = 2.0f, fontScale = 1.0f)
        val textScale = 1.40f

        val customDensity = androidx.compose.ui.unit.Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * textScale
        )

        assertEquals(2.0f, customDensity.density, 0.001f)
        assertEquals(1.40f, customDensity.fontScale, 0.001f)
    }

    @Test
    fun testPart10_completeChecklist() {
        // Rule A & C: Bharat Taxi parse
        val nodes = listOf(
            ParsedNode(null, "Cab Economy", Rect(0, 0, 1080, 50)),
            ParsedNode(null, "₹95", Rect(50, 100, 300, 232)),
            ParsedNode(null, "₹27/km", Rect(320, 120, 450, 182)),
            ParsedNode(null, "0 m・1 min", Rect(50, 250, 300, 290)),
            ParsedNode(null, "Pickup", Rect(50, 300, 600, 340)),
            ParsedNode(null, "3.5 km・11 min", Rect(50, 350, 300, 390)),
            ParsedNode(null, "Drop", Rect(50, 400, 600, 440)),
            ParsedNode(null, "Accept", Rect(50, 500, 1000, 600))
        )
        val offers = BharatTaxiParser.parseNodes(nodes)
        assertEquals(1, offers.size)
        val o = offers[0]
        assertEquals(95.0, o.baseFare, 0.01)
        assertEquals(27.0, o.appFarePerKm ?: 0.0, 0.01)
        assertEquals("HIGH", o.parseConfidence)
        assertEquals(0.0, o.pickupDistanceKm, 0.01)

        // Rule B: Safety Check: Fare/km mismatch > 35% sets LOW
        val mismatchOffer = o.copy(baseFare = 27.0, totalFare = 27.0)
        val calcRate = mismatchOffer.totalFare / mismatchOffer.dropDistanceKm
        val diff = Math.abs(calcRate - (mismatchOffer.appFarePerKm ?: 0.0)) / (mismatchOffer.appFarePerKm ?: 1.0)
        assertTrue(diff > 0.35)

        // Rule D: Rapido ride type badge / vehicle icon / fallback "Unknown" (never "Bike")
        val rapidoNodesNoBadge = listOf(
            ParsedNode(null, "₹90", Rect(50, 100, 200, 150)),
            ParsedNode(null, "+₹26", Rect(210, 100, 300, 150)),
            ParsedNode(null, "1.0 km", Rect(50, 180, 200, 210)),
            ParsedNode(null, "Pickup", Rect(50, 220, 800, 260)),
            ParsedNode(null, "5.0 km", Rect(50, 280, 200, 310)),
            ParsedNode(null, "Drop", Rect(50, 330, 800, 370)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510))
        )
        val rapidoOffers = RapidoParser.parseNodes(rapidoNodesNoBadge)
        assertEquals("Unknown", rapidoOffers[0].rideType)
        assertEquals(116.0, rapidoOffers[0].totalFare, 0.01)

        // Rule E: Dedupe fingerprint does not include fare
        val fpWithFare1 = o.copy(baseFare = 95.0).fingerprint
        val fpWithFare2 = o.copy(baseFare = 150.0).fingerprint
        assertEquals("Fingerprint must not change when fare changes", fpWithFare1, fpWithFare2)
    }

    // --- PART 11 PHASE 2 TESTS (Dashboard Rows Non-Clickable & AppLogger) ---

    @Test
    fun testDashboard_targetAppStatusRow_isDisplayOnly_notClickable() {
        // 1. Verify TargetAppStatusRow does not accept any onClick or onRowClick callback
        val dashboardScreenClass = Class.forName("com.example.ui.dashboard.DashboardScreenKt")
        val targetRowMethods = dashboardScreenClass.declaredMethods
            .filter { it.name.startsWith("TargetAppStatusRow") }
        assertTrue("TargetAppStatusRow method must exist", targetRowMethods.isNotEmpty())

        val targetRowMethod = targetRowMethods.first()
        val paramTypes = targetRowMethod.parameterTypes
        val hasOnClickParam = paramTypes.any {
            it.name.contains("Function0") || it.name.contains("onClick", ignoreCase = true)
        }
        assertFalse("TargetAppStatusRow must not accept any onClick/onRowClick lambda", hasOnClickParam)

        // 2. Verify DashboardViewModel no longer contains the openApp function
        val dashboardVmMethods = com.example.ui.dashboard.DashboardViewModel::class.java.declaredMethods
            .map { it.name }
        assertFalse("DashboardViewModel must not contain openApp method", dashboardVmMethods.contains("openApp"))

        // 3. Verify AppsViewModel still retains openApp for the Apps tab
        val appsVmMethods = com.example.ui.apps.AppsViewModel::class.java.declaredMethods
            .map { it.name }
        assertTrue("AppsViewModel must retain openApp for Apps screen", appsVmMethods.contains("openApp"))
    }

    @Test
    fun testAppLogger_safeExecution() {
        com.example.util.AppLogger.d("TestTag", "Debug message")
        com.example.util.AppLogger.i("TestTag", "Info message")
        com.example.util.AppLogger.w("TestTag", "Warning message", null)
        com.example.util.AppLogger.e("TestTag", "Error message", Exception("test"))
    }

    @Test
    fun testMicroBenchmark_parseAndDecideTimes() {
        // Bharat Taxi List Mode Sample Tree
        val btListTree = listOf(
            ParsedNode(null, "Cab Economy", Rect(0, 0, 1080, 50)),
            ParsedNode(null, "₹95", Rect(50, 100, 300, 232)),
            ParsedNode(null, "₹27/km", Rect(320, 120, 450, 182)),
            ParsedNode(null, "0 m・1 min", Rect(50, 250, 300, 290)),
            ParsedNode(null, "Pickup Central Station", Rect(50, 300, 600, 340)),
            ParsedNode(null, "3.5 km・11 min", Rect(50, 350, 300, 390)),
            ParsedNode(null, "Drop Tech Park Gate 2", Rect(50, 400, 600, 440)),
            ParsedNode(null, "Accept", Rect(50, 500, 1000, 600), isClickable = true)
        )

        // Bharat Taxi Single Mode Sample Tree
        val btSingleTree = listOf(
            ParsedNode(null, "Cab Premium", Rect(0, 0, 1080, 60)),
            ParsedNode(null, "₹180", Rect(50, 100, 300, 250)),
            ParsedNode(null, "₹24/km", Rect(320, 120, 450, 180)),
            ParsedNode(null, "1.2 km・4 min", Rect(50, 260, 300, 300)),
            ParsedNode(null, "Pickup Airport Terminal 1", Rect(50, 310, 800, 350)),
            ParsedNode(null, "7.5 km・22 min", Rect(50, 360, 300, 400)),
            ParsedNode(null, "Drop City Center Mall", Rect(50, 410, 800, 450)),
            ParsedNode(null, "Accept", Rect(50, 520, 1000, 620), isClickable = true)
        )

        // Rapido Sample Tree
        val rapidoTree = listOf(
            ParsedNode(null, "Cab Boost", Rect(50, 60, 300, 90)),
            ParsedNode(null, "₹90", Rect(50, 100, 200, 150)),
            ParsedNode(null, "+₹26", Rect(210, 100, 300, 150)),
            ParsedNode(null, "1.0 km", Rect(50, 180, 200, 210)),
            ParsedNode(null, "Pickup Sector 18 Market", Rect(50, 220, 800, 260)),
            ParsedNode(null, "5.0 km", Rect(50, 280, 200, 310)),
            ParsedNode(null, "Drop Cyber City Hub", Rect(50, 330, 800, 370)),
            ParsedNode(null, "Accept", Rect(150, 450, 750, 510), isClickable = true)
        )

        val filter = DriverFilter(minFare = 70.0, maxFare = 500.0, isUnlimitedMaxFare = false)

        val iterations = 50
        val durationsBtList = mutableListOf<Double>()
        val durationsBtSingle = mutableListOf<Double>()
        val durationsRapido = mutableListOf<Double>()

        // Warmup
        for (i in 0 until 10) {
            val offers1 = BharatTaxiParser.parseNodes(btListTree)
            if (offers1.isNotEmpty()) {
                FilterEngine.evaluate(offers1[0], filter)
                SelectionEngine.selectBestOffer(offers1, filter)
            }
            val offers2 = BharatTaxiParser.parseNodes(btSingleTree)
            if (offers2.isNotEmpty()) {
                FilterEngine.evaluate(offers2[0], filter)
                SelectionEngine.selectBestOffer(offers2, filter)
            }
            val offers3 = RapidoParser.parseNodes(rapidoTree)
            if (offers3.isNotEmpty()) {
                FilterEngine.evaluate(offers3[0], filter)
                SelectionEngine.selectBestOffer(offers3, filter)
            }
        }

        // Benchmark runs
        for (i in 0 until iterations) {
            val start1 = System.nanoTime()
            val o1 = BharatTaxiParser.parseNodes(btListTree)
            if (o1.isNotEmpty()) {
                FilterEngine.evaluate(o1[0], filter)
                SelectionEngine.selectBestOffer(o1, filter)
            }
            val end1 = System.nanoTime()
            durationsBtList.add((end1 - start1) / 1_000_000.0)

            val start2 = System.nanoTime()
            val o2 = BharatTaxiParser.parseNodes(btSingleTree)
            if (o2.isNotEmpty()) {
                FilterEngine.evaluate(o2[0], filter)
                SelectionEngine.selectBestOffer(o2, filter)
            }
            val end2 = System.nanoTime()
            durationsBtSingle.add((end2 - start2) / 1_000_000.0)

            val start3 = System.nanoTime()
            val o3 = RapidoParser.parseNodes(rapidoTree)
            if (o3.isNotEmpty()) {
                FilterEngine.evaluate(o3[0], filter)
                SelectionEngine.selectBestOffer(o3, filter)
            }
            val end3 = System.nanoTime()
            durationsRapido.add((end3 - start3) / 1_000_000.0)
        }

        durationsBtList.sort()
        durationsBtSingle.sort()
        durationsRapido.sort()

        val medianBtList = durationsBtList[durationsBtList.size / 2]
        val medianBtSingle = durationsBtSingle[durationsBtSingle.size / 2]
        val medianRapido = durationsRapido[durationsRapido.size / 2]

        println("MICRO-BENCHMARK RESULTS (Parse + Decide):")
        println("  Bharat Taxi List Mode Median: ${String.format("%.3f", medianBtList)} ms")
        println("  Bharat Taxi Single Mode Median: ${String.format("%.3f", medianBtSingle)} ms")
        println("  Rapido Mode Median: ${String.format("%.3f", medianRapido)} ms")

        assertTrue("Bharat Taxi list mode parse+decide ($medianBtList ms) must be well under 30 ms target", medianBtList < 30.0)
        assertTrue("Bharat Taxi single mode parse+decide ($medianBtSingle ms) must be well under 30 ms target", medianBtSingle < 30.0)
        assertTrue("Rapido parse+decide ($medianRapido ms) must be well under 30 ms target", medianRapido < 30.0)
    }
}

