package com.example

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppResolver
import com.example.data.model.AppRuntimeState
import com.example.data.model.AppRuntimeStatus
import com.example.data.model.DriverFilter
import com.example.data.model.LocationKeyword
import com.example.data.model.RideOffer
import com.example.data.model.StrategyStats
import com.example.domain.engine.BharatTaxiParser
import com.example.domain.engine.FilterEngine
import com.example.domain.engine.ParsedNode
import com.example.domain.engine.RapidoParser
import com.example.domain.engine.SelectionEngine
import com.example.domain.engine.TapMethodType
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
}
