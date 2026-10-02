package com.example.domain.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.RideOffer
import java.security.MessageDigest
import java.util.regex.Pattern

data class ParsedNode(
    val node: AccessibilityNodeInfo?,
    val text: String,
    val bounds: Rect = Rect(),
    val isVisibleToUser: Boolean = true,
    val isClickable: Boolean = false
)

object BharatTaxiParser {

    private val FARE_PATTERN = Pattern.compile("₹\\s*([0-9][0-9,]*(\\.[0-9]+)?)")
    private val DIST_TIME_PATTERN = Pattern.compile("([0-9]+(\\.[0-9]+)?)\\s*(km|m)\\s*[·•.\\-]\\s*([0-9]+)\\s*(min|mins)", Pattern.CASE_INSENSITIVE)

    fun parseNodes(
        nodes: List<ParsedNode>,
        processTestRequests: Boolean = true,
        screenBounds: Rect = Rect(0, 0, 1080, 2400)
    ): List<RideOffer> {
        val offers = mutableListOf<RideOffer>()

        // Find cards or segments that contain Accept
        // In Bharat Taxi, each card contains:
        // optional "TEST REQUEST"
        // Fare starting with ₹
        // 1st dist/time (pickup)
        // pickup address
        // 2nd dist/time (drop)
        // drop address
        // Buttons: "+10", Accept
        val acceptIndices = mutableListOf<Int>()
        for (i in nodes.indices) {
            val text = nodes[i].text.trim()
            if (text.equals("Accept", ignoreCase = true) || text.equals("स्वीकार करें", ignoreCase = true)) {
                acceptIndices.add(i)
            }
        }

        if (acceptIndices.isEmpty()) return emptyList()

        var startIndex = 0
        for (acceptIdx in acceptIndices) {
            val cardNodes = nodes.subList(startIndex, acceptIdx + 1)
            val offer = parseSingleCard(cardNodes, acceptIdx, processTestRequests, screenBounds)
            if (offer != null) {
                offers.add(offer)
            }
            startIndex = acceptIdx + 1
        }

        return offers
    }

    private fun parseSingleCard(
        cardNodes: List<ParsedNode>,
        acceptIndex: Int,
        processTestRequests: Boolean,
        screenBounds: Rect
    ): RideOffer? {
        var isTest = false
        var fare: Double? = null
        var pickupDistKm: Double? = null
        var pickupEtaMin: Int? = null
        var dropDistKm: Double? = null
        var dropEtaMin: Int? = null

        var pickupDistNodeIdx = -1
        var dropDistNodeIdx = -1

        for (i in cardNodes.indices) {
            val t = cardNodes[i].text.trim()
            if (t.contains("TEST REQUEST", ignoreCase = true)) {
                isTest = true
            }

            // Fare check
            if (fare == null && t.startsWith("₹")) {
                val m = FARE_PATTERN.matcher(t)
                if (m.find()) {
                    fare = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                }
            }

            // Dist/time match
            val dtMatcher = DIST_TIME_PATTERN.matcher(t)
            if (dtMatcher.find()) {
                val value = dtMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                val unit = dtMatcher.group(3)?.lowercase() ?: "km"
                val distKm = if (unit == "m") value / 1000.0 else value
                val mins = dtMatcher.group(4)?.toIntOrNull()

                if (pickupDistKm == null) {
                    pickupDistKm = distKm
                    pickupEtaMin = mins
                    pickupDistNodeIdx = i
                } else if (dropDistKm == null) {
                    dropDistKm = distKm
                    dropEtaMin = mins
                    dropDistNodeIdx = i
                }
            }
        }

        if (!processTestRequests && isTest) {
            return null
        }

        if (fare == null || pickupDistKm == null || dropDistKm == null || pickupDistNodeIdx == -1 || dropDistNodeIdx == -1) {
            return null
        }

        // Pickup address is text after pickupDistNodeIdx and before dropDistNodeIdx
        val pickupAddrParts = mutableListOf<String>()
        for (i in (pickupDistNodeIdx + 1) until dropDistNodeIdx) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredText(text)) {
                pickupAddrParts.add(text)
            }
        }
        val pickupAddress = pickupAddrParts.joinToString(", ").ifEmpty { "Pickup Location" }

        // Drop address is text after dropDistNodeIdx and before buttons row
        val dropAddrParts = mutableListOf<String>()
        for (i in (dropDistNodeIdx + 1) until (cardNodes.size - 1)) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredText(text)) {
                dropAddrParts.add(text)
            }
        }
        val dropAddress = dropAddrParts.joinToString(", ").ifEmpty { "Drop Location" }

        val acceptNode = cardNodes.last()
        val acceptBounds = acceptNode.bounds

        // Check if bounds inside screen and visible
        val isInsideScreen = acceptBounds.left >= 0 && acceptBounds.top >= 0 &&
                acceptBounds.right <= screenBounds.right && acceptBounds.bottom <= screenBounds.bottom
        val isActionable = acceptNode.isVisibleToUser && isInsideScreen

        val rawTextHash = computeHash("bharat_taxi_${fare}_${pickupAddress}_${dropAddress}")

        return RideOffer(
            appId = "bharat_taxi",
            rideType = "Bharat Taxi",
            baseFare = fare,
            extraFare = 0.0,
            totalFare = fare,
            pickupDistanceKm = pickupDistKm,
            pickupEtaMin = pickupEtaMin,
            dropDistanceKm = dropDistKm,
            dropEtaMin = dropEtaMin,
            pickupAddress = pickupAddress,
            dropAddress = dropAddress,
            dropAddressTruncated = false,
            isTest = isTest,
            rawTextHash = rawTextHash,
            acceptNodeBounds = acceptBounds,
            acceptNode = acceptNode.node,
            isActionable = isActionable
        )
    }

    private fun isIgnoredText(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "+10" || lower == "+20" || lower == "+30" || lower == "x" ||
                lower == "more requests" || lower.startsWith("₹")
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
