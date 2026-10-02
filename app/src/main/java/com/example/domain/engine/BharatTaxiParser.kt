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
    val isClickable: Boolean = false,
    val viewId: String? = null,
    val className: String? = null
)

object BharatTaxiParser {

    // Tolerant regex works with any separator: "・" U+30FB, "·" U+00B7, "•", "-", "." or space
    val DIST_TIME_PATTERN: Pattern = Pattern.compile(
        "([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)\\b\\s*[^0-9A-Za-z]{0,3}\\s*([0-9]+)\\s*(?:min|mins)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val FARE_PATTERN = Pattern.compile("₹\\s*([0-9][0-9,]*(\\.[0-9]+)?)")
    private val NUMERIC_PATTERN = Pattern.compile("([0-9][0-9,]*(\\.[0-9]+)?)")

    fun parseNodes(
        nodes: List<ParsedNode>,
        processTestRequests: Boolean = true,
        screenBounds: Rect = Rect(0, 0, 1080, 2400)
    ): List<RideOffer> {
        if (nodes.isEmpty()) return emptyList()

        // Check if Variant 1 view IDs exist on screen
        val hasVariant1Ids = nodes.any { n ->
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            vid == "ride_request_content_recycler_view" ||
                    vid == "ride_accept" ||
                    vid == "pickupDistance" ||
                    vid == "tripDistance"
        }

        return if (hasVariant1Ids) {
            parseVariant1(nodes, screenBounds)
        } else {
            parseVariant2(nodes, processTestRequests, screenBounds)
        }
    }

    // Variant 1: List mode with known view IDs
    private fun parseVariant1(
        nodes: List<ParsedNode>,
        screenBounds: Rect
    ): List<RideOffer> {
        val offers = mutableListOf<RideOffer>()

        // 1. Ignore the left rail (ride_request_tab_recycler_view, bounds x < 190)
        val cardNodes = nodes.filter { n ->
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            val isRail = vid == "ride_request_tab_recycler_view" ||
                    (n.bounds.right > 0 && n.bounds.right <= 190 && n.bounds.left < 190)
            !isRail
        }

        // Group into cards by "ride_accept" or "container"
        val acceptIndices = mutableListOf<Int>()
        for (i in cardNodes.indices) {
            val vid = cardNodes[i].viewId?.substringAfter(":id/") ?: ""
            val text = cardNodes[i].text.trim()
            if (vid == "ride_accept" || vid == "ride_accept_text" || text.equals("Accept", ignoreCase = true)) {
                acceptIndices.add(i)
            }
        }

        if (acceptIndices.isEmpty()) return emptyList()

        var startIndex = 0
        for (acceptIdx in acceptIndices) {
            val group = cardNodes.subList(startIndex, acceptIdx + 1)
            val offer = parseVariant1Card(group, screenBounds)
            if (offer != null) {
                offers.add(offer)
            }
            startIndex = acceptIdx + 1
        }

        return offers
    }

    private fun parseVariant1Card(
        cardNodes: List<ParsedNode>,
        screenBounds: Rect
    ): RideOffer? {
        var rideType = "Cab Economy"
        var baseFare: Double? = null
        var appFarePerKm: Double? = null

        var pickupDistKm: Double? = null
        var pickupEtaMin: Int? = null
        var dropDistKm: Double? = null
        var dropEtaMin: Int? = null

        var pickupAddress = ""
        var dropAddress = ""

        var rideAcceptNode: ParsedNode? = null

        // Pass 1: Parse structured elements by viewId
        var foundFirstCurrency = false
        var foundSecondCurrency = false

        for (i in cardNodes.indices) {
            val n = cardNodes[i]
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            val t = n.text.trim()

            // (a) Ride type: first non-empty TextView without viewId or with ride type text
            if ((vid.isEmpty() || vid == "container") && t.isNotEmpty() && !t.startsWith("₹") && !t.equals("Accept", ignoreCase = true)) {
                if (rideType == "Cab Economy" && isCandidateRideType(t)) {
                    rideType = t
                }
            }

            // (b) Fare: tv_currency ("₹") followed by tv_value ("95")
            if (vid == "tv_currency" || t == "₹") {
                if (!foundFirstCurrency) {
                    foundFirstCurrency = true
                    // Look ahead for tv_value
                    for (j in (i + 1) until minOf(i + 3, cardNodes.size)) {
                        val next = cardNodes[j]
                        val nextVid = next.viewId?.substringAfter(":id/") ?: ""
                        val num = parseNumber(next.text)
                        if (num != null) {
                            baseFare = num
                            break
                        }
                    }
                } else if (!foundSecondCurrency) {
                    foundSecondCurrency = true
                    // Look ahead for per-km value like "27/km"
                    for (j in (i + 1) until minOf(i + 3, cardNodes.size)) {
                        val next = cardNodes[j]
                        val num = parseNumber(next.text)
                        if (num != null) {
                            appFarePerKm = num
                            break
                        }
                    }
                }
            } else if (baseFare == null && (vid == "tv_value" || t.startsWith("₹"))) {
                val num = parseNumber(t)
                if (num != null) {
                    baseFare = num
                }
            }

            // (e) pickupDistance: e.g. "0 m・1 min"
            if (vid == "pickupDistance" || (pickupDistKm == null && DIST_TIME_PATTERN.matcher(t).find())) {
                val dt = parseDistanceTime(t)
                if (dt != null) {
                    pickupDistKm = dt.first
                    pickupEtaMin = dt.second
                }
            }

            // (f & h) tv_primary_text: first is pickup address, second is drop address
            if (vid == "tv_primary_text") {
                if (pickupAddress.isEmpty()) {
                    pickupAddress = t
                } else if (dropAddress.isEmpty()) {
                    dropAddress = t
                }
            }

            // (g) tripDistance: e.g. "3.5 km・11 min"
            if (vid == "tripDistance") {
                val dt = parseDistanceTime(t)
                if (dt != null) {
                    dropDistKm = dt.first
                    dropEtaMin = dt.second
                }
            }

            // (i) ride_accept node
            if (vid == "ride_accept") {
                rideAcceptNode = n
            } else if (rideAcceptNode == null && (vid == "ride_accept_text" || t.equals("Accept", ignoreCase = true))) {
                rideAcceptNode = n
            }
        }

        // Fallback for addresses if tv_primary_text wasn't tagged
        if (pickupAddress.isEmpty() || dropAddress.isEmpty()) {
            val textCandidates = cardNodes.map { it.text.trim() }.filter {
                it.isNotEmpty() && !it.startsWith("₹") && !it.equals("Accept", ignoreCase = true) &&
                        !DIST_TIME_PATTERN.matcher(it).find() && !isIgnoredBharatText(it)
            }
            if (pickupAddress.isEmpty() && textCandidates.isNotEmpty()) {
                pickupAddress = textCandidates.getOrNull(1) ?: textCandidates[0]
            }
            if (dropAddress.isEmpty() && textCandidates.size >= 3) {
                dropAddress = textCandidates.last()
            }
        }

        if (baseFare == null || pickupDistKm == null || dropDistKm == null) {
            return null
        }

        val acceptNode = rideAcceptNode ?: cardNodes.last()
        val acceptBounds = acceptNode.bounds
        val isInsideScreen = acceptBounds.left >= 0 && acceptBounds.top >= 0 &&
                acceptBounds.right <= screenBounds.right && acceptBounds.bottom <= screenBounds.bottom
        val isActionable = acceptNode.isVisibleToUser && isInsideScreen

        val rawTextHash = computeHash("bharat_taxi_v1_${baseFare}_${pickupAddress}_${dropAddress}")

        return RideOffer(
            appId = "bharat_taxi",
            rideType = rideType,
            baseFare = baseFare,
            extraFare = 0.0,
            totalFare = baseFare,
            appFarePerKm = appFarePerKm,
            pickupDistanceKm = pickupDistKm,
            pickupEtaMin = pickupEtaMin,
            dropDistanceKm = dropDistKm,
            dropEtaMin = dropEtaMin,
            pickupAddress = pickupAddress.ifEmpty { "Pickup Location" },
            dropAddress = dropAddress.ifEmpty { "Drop Location" },
            dropAddressTruncated = false,
            isTest = false,
            rawTextHash = rawTextHash,
            acceptNodeBounds = acceptBounds,
            acceptNode = acceptNode.node,
            isActionable = isActionable
        )
    }

    // Variant 2: Single request mode via text heuristics
    private fun parseVariant2(
        nodes: List<ParsedNode>,
        processTestRequests: Boolean,
        screenBounds: Rect
    ): List<RideOffer> {
        val offers = mutableListOf<RideOffer>()

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
            val offer = parseVariant2Card(cardNodes, processTestRequests, screenBounds)
            if (offer != null) {
                offers.add(offer)
            }
            startIndex = acceptIdx + 1
        }

        return offers
    }

    private fun parseVariant2Card(
        cardNodes: List<ParsedNode>,
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

            // Fare check: either "₹50" or "₹" followed by "50"
            if (fare == null) {
                if (t.startsWith("₹")) {
                    val m = FARE_PATTERN.matcher(t)
                    if (m.find()) {
                        fare = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    } else if (i + 1 < cardNodes.size) {
                        fare = parseNumber(cardNodes[i + 1].text)
                    }
                }
            }

            // Dist/time match with tolerant regex
            val dt = parseDistanceTime(t)
            if (dt != null) {
                if (pickupDistKm == null) {
                    pickupDistKm = dt.first
                    pickupEtaMin = dt.second
                    pickupDistNodeIdx = i
                } else if (dropDistKm == null) {
                    dropDistKm = dt.first
                    dropEtaMin = dt.second
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

        // Pickup address is text between pickup distance and drop distance
        val pickupAddrParts = mutableListOf<String>()
        for (i in (pickupDistNodeIdx + 1) until dropDistNodeIdx) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredBharatText(text)) {
                pickupAddrParts.add(text)
            }
        }
        val pickupAddress = pickupAddrParts.joinToString(", ").ifEmpty { "Pickup Location" }

        // Drop address is text after drop distance and before buttons
        val dropAddrParts = mutableListOf<String>()
        for (i in (dropDistNodeIdx + 1) until (cardNodes.size - 1)) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredBharatText(text)) {
                dropAddrParts.add(text)
            }
        }
        val dropAddress = dropAddrParts.joinToString(", ").ifEmpty { "Drop Location" }

        val acceptNode = cardNodes.last()
        val acceptBounds = acceptNode.bounds
        val isInsideScreen = acceptBounds.left >= 0 && acceptBounds.top >= 0 &&
                acceptBounds.right <= screenBounds.right && acceptBounds.bottom <= screenBounds.bottom
        val isActionable = acceptNode.isVisibleToUser && isInsideScreen

        val rawTextHash = computeHash("bharat_taxi_v2_${fare}_${pickupAddress}_${dropAddress}")

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

    fun parseDistanceTime(text: String): Pair<Double, Int?>? {
        val m = DIST_TIME_PATTERN.matcher(text)
        if (m.find()) {
            val value = m.group(1)?.toDoubleOrNull() ?: 0.0
            val unit = m.group(2)?.lowercase() ?: "km"
            val distKm = if (unit == "m") value / 1000.0 else value
            val mins = m.group(3)?.toIntOrNull()
            return Pair(distKm, mins)
        }
        return null
    }

    private fun parseNumber(text: String): Double? {
        val m = NUMERIC_PATTERN.matcher(text)
        return if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
    }

    private fun isCandidateRideType(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("cab") || lower.contains("economy") ||
                lower.contains("premium") || lower.contains("intercity") ||
                lower.contains("sedan") || lower.contains("mini")
    }

    private fun isIgnoredBharatText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("+") || lower == "x" || lower == "more requests" ||
                lower.startsWith("₹") || lower == "ride_decline" || lower == "decline"
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
