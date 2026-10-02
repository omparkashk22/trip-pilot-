package com.example

import android.graphics.Rect
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TripPilotUnitTest {

    @Test
    fun testBharatTaxiParser_validOffer() {
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
            // Floating overlay intersects Accept
            ParsedNode(null, "Extra from Customer", Rect(100, 1850, 800, 1950))
        )

        val offers = RapidoParser.parseNodes(nodes)
        assertEquals(1, offers.size)
        // Since Accept intersects floating overlay, it should not be actionable
        assertFalse(offers.first().isActionable)
    }

    @Test
    fun testFilterEngine_ruleOrderAndSkipReason() {
        val offer = RideOffer(
            appId = "rapido",
            rideType = "Bike",
            baseFare = 150.0,
            extraFare = 50.0,
            totalFare = 200.0,
            pickupDistanceKm = 3.0,
            dropDistanceKm = 10.0,
            pickupAddress = "Indiranagar",
            dropAddress = "Whitefield"
        )

        // 1. App disabled
        val res1 = FilterEngine.evaluate(offer, DriverFilter(), isAppEnabled = false)
        assertFalse(res1.isMatched)
        assertEquals("App disabled in settings", res1.skipReason)

        // 2. Ride type not allowed
        val filterAllowedType = DriverFilter(allowedRideTypes = setOf("Auto", "Cab"))
        val res2 = FilterEngine.evaluate(offer, filterAllowedType)
        assertFalse(res2.isMatched)
        assertTrue(res2.skipReason?.contains("Ride type") == true)

        // 3. Min fare check
        val filterMinFare = DriverFilter(minFare = 250.0)
        val res3 = FilterEngine.evaluate(offer, filterMinFare)
        assertFalse(res3.isMatched)
        assertTrue(res3.skipReason?.contains("Fare below minimum") == true)

        // 4. Pickup distance max
        val filterPickup = DriverFilter(pickupDistanceMaxKm = 2.0)
        val res4 = FilterEngine.evaluate(offer, filterPickup)
        assertFalse(res4.isMatched)
        assertTrue(res4.skipReason?.contains("Pickup outside range") == true)

        // 5. Drop distance max
        val filterDrop = DriverFilter(dropDistanceMaxKm = 8.0)
        val res5 = FilterEngine.evaluate(offer, filterDrop)
        assertFalse(res5.isMatched)
        assertTrue(res5.skipReason?.contains("Drop outside range") == true)

        // 6. Location keyword rejection
        val filterLocation = DriverFilter(
            isLocationFilterEnabled = true,
            locationKeywords = listOf(LocationKeyword("Whitefield", isAccept = false))
        )
        val res6 = FilterEngine.evaluate(offer, filterLocation)
        assertFalse(res6.isMatched)
        assertTrue(res6.skipReason?.contains("Location keyword rejected") == true)

        // 7. Perfect match
        val filterPass = DriverFilter(
            minFare = 100.0,
            pickupDistanceMaxKm = 5.0,
            dropDistanceMaxKm = 20.0
        )
        val res7 = FilterEngine.evaluate(offer, filterPass)
        assertTrue(res7.isMatched)
        assertNull(res7.skipReason)
    }

    @Test
    fun testSelectionEngine_fourStrategies() {
        val offer1 = RideOffer(
            appId = "rapido", rideType = "Bike", baseFare = 100.0, extraFare = 0.0,
            pickupDistanceKm = 1.0, dropDistanceKm = 10.0, pickupAddress = "A", dropAddress = "B"
        ) // Fare = 100, Fare/km = 10.0, Pickup = 1.0

        val offer2 = RideOffer(
            appId = "rapido", rideType = "Auto", baseFare = 300.0, extraFare = 50.0,
            pickupDistanceKm = 4.0, dropDistanceKm = 15.0, pickupAddress = "C", dropAddress = "D"
        ) // Total Fare = 350, Fare/km = 23.33, Pickup = 4.0

        val offer3 = RideOffer(
            appId = "rapido", rideType = "Cab", baseFare = 200.0, extraFare = 0.0,
            pickupDistanceKm = 0.5, dropDistanceKm = 5.0, pickupAddress = "E", dropAddress = "F"
        ) // Fare = 200, Fare/km = 40.0, Pickup = 0.5

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
}
