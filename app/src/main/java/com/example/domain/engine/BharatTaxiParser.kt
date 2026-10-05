package com.example.domain.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.RideOffer
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

data class ParsedNode(
    val node: AccessibilityNodeInfo?,
    val text: String,
    val bounds: Rect = Rect(),
    val isVisibleToUser: Boolean = true,
    val isClickable: Boolean = false,
    val viewId: String? = null,
    val className: String? = null,
    val depth: Int = 0
)

fun buildRawCardJson(
    cardNodes: List<ParsedNode>,
    layoutVariant: String,
    parseConfidence: String,
    parseReason: String?
): String {
    val maxNodes = cardNodes.take(80)
    val sb = StringBuilder()
    sb.append("{")
    sb.append("\"layout\":\"").append(layoutVariant).append("\",")
    sb.append("\"confidence\":\"").append(parseConfidence).append("\",")
    sb.append("\"reason\":").append(if (parseReason != null) "\"$parseReason\"" else "null").append(",")
    sb.append("\"nodes\":[")
    for (i in maxNodes.indices) {
        val n = maxNodes[i]
        val shortId = n.viewId?.substringAfter(":id/") ?: ""
        val shortCls = n.className?.substringAfterLast(".") ?: ""
        val escapedText = n.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")
        val cd = n.node?.contentDescription?.toString() ?: ""
        val escapedCd = cd.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")

        sb.append("{")
        sb.append("\"text\":\"").append(escapedText).append("\",")
        sb.append("\"contentDescription\":\"").append(escapedCd).append("\",")
        sb.append("\"viewId\":\"").append(shortId).append("\",")
        sb.append("\"className\":\"").append(shortCls).append("\",")
        sb.append("\"boundsHeight\":").append(n.bounds.height()).append(",")
        sb.append("\"clickable\":").append(n.isClickable).append(",")
        sb.append("\"depth\":").append(n.depth)
        sb.append("}")
        if (i < maxNodes.size - 1) sb.append(",")
    }
    sb.append("]}")
    return sb.toString()
}

object BharatTaxiParser {

    // Tolerant regex works with any separator: "・" U+30FB, "·" U+00B7, "•", "-", "." or space
    val DIST_TIME_PATTERN: Pattern = Pattern.compile(
        "([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)\\b\\s*[^0-9A-Za-z]{0,3}\\s*([0-9]+)\\s*(?:min|mins)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // A text is a PER-KM RATE if it matches ₹?\s*([0-9]+(?:\.[0-9]+)?)\s*/\s*km (case-insensitive)
    val PER_KM_PATTERN: Pattern = Pattern.compile(
        "₹?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*km",
        Pattern.CASE_INSENSITIVE
    )

    // Fare amount regex must reject per-km text: ₹\s*([0-9][0-9,]*(?:\.[0-9]+)?)(?!\s*/\s*km)(?![0-9])
    val FARE_AMOUNT_PATTERN: Pattern = Pattern.compile(
        "₹\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(?!\\s*/\\s*km)(?![0-9])",
        Pattern.CASE_INSENSITIVE
    )

    private val NUMERIC_PATTERN = Pattern.compile("([0-9][0-9,]*(\\.[0-9]+)?)")

    val KNOWN_RIDE_TYPES = listOf(
        "Economy Intercity",
        "Premium Intercity",
        "Cab Economy",
        "Cab Premium",
        "Cab XL",
        "Cab",
        "Auto",
        "Bike"
    )

    fun parseNodes(
        nodes: List<ParsedNode>,
        processTestRequests: Boolean = true,
        screenBounds: Rect = Rect(0, 0, 1080, 2400)
    ): List<RideOffer> {
        if (nodes.isEmpty()) return emptyList()

        // Ignore floating widget window (floating_layout)
        val nonFloatingNodes = nodes.filter { n ->
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            vid != "floating_layout" && !n.text.contains("floating_layout", ignoreCase = true)
        }
        if (nonFloatingNodes.isEmpty()) return emptyList()

        // Detect if left rail is present
        val hasLeftRail = nonFloatingNodes.any { n ->
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            vid == "ride_request_tab_recycler_view" || (n.bounds.right in 1..190 && n.bounds.left < 190 && n.isClickable)
        }
        val layoutVariant = if (hasLeftRail) "LIST" else "SINGLE"

        // Filter out left rail nodes and never-click nodes
        val cardNodes = nonFloatingNodes.filter { n ->
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            val isRail = vid == "ride_request_tab_recycler_view" ||
                    (hasLeftRail && n.bounds.right in 1..190 && n.bounds.left < 190)
            val isNeverClick = vid == "ride_decline" || vid == "rr_silent_container" ||
                    vid == "rr_minimize_container" || vid == "tts_toggle_container"
            !isRail && !isNeverClick
        }

        // Identify cards ending with Accept
        val acceptIndices = mutableListOf<Int>()
        for (i in cardNodes.indices) {
            val vid = cardNodes[i].viewId?.substringAfter(":id/") ?: ""
            val text = cardNodes[i].text.trim()
            if (vid == "ride_accept" || vid == "ride_accept_text" ||
                text.equals("Accept", ignoreCase = true) || text.equals("स्वीकार करें", ignoreCase = true)
            ) {
                // Must be an actual Accept button, not a cancellation or decline link
                if (!text.contains("cancellation", ignoreCase = true) && !text.contains("decline", ignoreCase = true)) {
                    acceptIndices.add(i)
                }
            }
        }

        if (acceptIndices.isEmpty()) return emptyList()

        val offers = mutableListOf<RideOffer>()
        var startIndex = 0
        for (acceptIdx in acceptIndices) {
            val group = cardNodes.subList(startIndex, acceptIdx + 1)
            val offer = parseCardGroup(group, processTestRequests, screenBounds, layoutVariant)
            if (offer != null) {
                offers.add(offer)
            }
            startIndex = acceptIdx + 1
        }

        return offers
    }

    private fun parseCardGroup(
        cardNodes: List<ParsedNode>,
        processTestRequests: Boolean,
        screenBounds: Rect,
        layoutVariant: String
    ): RideOffer? {
        var isTest = false
        var rideType = "Unknown"
        var baseFare: Double? = null
        var chosenFareNode: ParsedNode? = null
        var appFarePerKm: Double? = null

        var pickupDistKm: Double? = null
        var pickupEtaMin: Int? = null
        var dropDistKm: Double? = null
        var dropEtaMin: Int? = null

        var pickupAddress = ""
        var dropAddress = ""

        var rideAcceptNode: ParsedNode? = null
        var pickupDistNodeIdx = -1
        var dropDistNodeIdx = -1

        // Check for TEST REQUEST
        for (n in cardNodes) {
            if (n.text.contains("TEST REQUEST", ignoreCase = true)) {
                isTest = true
            }
        }
        if (!processTestRequests && isTest) return null

        // 1. First extract any PER-KM RATE: e.g. "₹27/km", "₹ 22 / km", "26/km"
        for (i in cardNodes.indices) {
            val n = cardNodes[i]
            val text = n.text.trim()

            // Check if this node matches PER-KM pattern
            val perKmM = PER_KM_PATTERN.matcher(text)
            if (perKmM.find()) {
                val rate = perKmM.group(1)?.toDoubleOrNull()
                if (rate != null && appFarePerKm == null) {
                    appFarePerKm = rate
                }
            } else if (text == "₹" && i + 1 < cardNodes.size) {
                // Separate nodes: "₹" followed by "27/km"
                val nextText = cardNodes[i + 1].text.trim()
                val nextM = PER_KM_PATTERN.matcher(nextText)
                if (nextM.find()) {
                    val rate = nextM.group(1)?.toDoubleOrNull()
                    if (rate != null && appFarePerKm == null) {
                        appFarePerKm = rate
                    }
                }
            } else if (text.contains("/km", ignoreCase = true)) {
                val num = parseNumber(text)
                if (num != null && appFarePerKm == null) {
                    appFarePerKm = num
                }
            }
        }

        // 2. Candidate fare nodes = ₹ amounts inside the card subtree only
        // Choose the candidate with largest text height (node bounds height); on tie, first in tree order
        data class FareCandidate(val node: ParsedNode, val amount: Double, val height: Int, val index: Int)
        val candidates = mutableListOf<FareCandidate>()

        for (i in cardNodes.indices) {
            val n = cardNodes[i]
            val text = n.text.trim()

            // Reject per-km text and +N buttons
            if (text.contains("/km", ignoreCase = true) || PER_KM_PATTERN.matcher(text).find() || text.startsWith("+")) {
                continue
            }

            // Check if node matches fare amount regex
            val fm = FARE_AMOUNT_PATTERN.matcher(text)
            if (fm.find()) {
                val amount = fm.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (amount != null) {
                    candidates.add(FareCandidate(n, amount, n.bounds.height(), i))
                }
            } else if (text == "₹" && i + 1 < cardNodes.size) {
                // Separate node: "₹" then "95"
                val nextNode = cardNodes[i + 1]
                val nextText = nextNode.text.trim()
                if (!nextText.contains("/km", ignoreCase = true)) {
                    val amount = parseNumber(nextText)
                    if (amount != null) {
                        val h = maxOf(n.bounds.height(), nextNode.bounds.height())
                        candidates.add(FareCandidate(nextNode, amount, h, i))
                    }
                }
            } else {
                val vid = n.viewId?.substringAfter(":id/") ?: ""
                if (vid == "tv_value" && !text.contains("/km", ignoreCase = true)) {
                    val amount = parseNumber(text)
                    if (amount != null) {
                        candidates.add(FareCandidate(n, amount, n.bounds.height(), i))
                    }
                }
            }
        }

        if (candidates.isNotEmpty()) {
            // Sort by largest bounds height descending, then by tree order index ascending
            candidates.sortWith(compareByDescending<FareCandidate> { it.height }.thenBy { it.index })
            val best = candidates.first()
            baseFare = best.amount
            chosenFareNode = best.node
        }

        // 3. Parse distance/time lines with tolerant regex
        for (i in cardNodes.indices) {
            val n = cardNodes[i]
            val vid = n.viewId?.substringAfter(":id/") ?: ""
            val text = n.text.trim()

            if (vid == "pickupDistance" || (pickupDistKm == null && DIST_TIME_PATTERN.matcher(text).find())) {
                val dt = parseDistanceTime(text)
                if (dt != null && pickupDistKm == null) {
                    pickupDistKm = dt.first
                    pickupEtaMin = dt.second
                    pickupDistNodeIdx = i
                }
            } else if (vid == "tripDistance" || (pickupDistKm != null && dropDistKm == null && DIST_TIME_PATTERN.matcher(text).find())) {
                val dt = parseDistanceTime(text)
                if (dt != null && dropDistKm == null) {
                    dropDistKm = dt.first
                    dropEtaMin = dt.second
                    dropDistNodeIdx = i
                }
            }

            // Accept node
            if (vid == "ride_accept" || vid == "ride_accept_text" ||
                text.equals("Accept", ignoreCase = true) || text.equals("स्वीकार करें", ignoreCase = true)
            ) {
                rideAcceptNode = n
            }
        }

        // 4. Ride Type detection using RideTypeSanitizer
        val fareTop = chosenFareNode?.bounds?.top ?: Int.MAX_VALUE
        // (a) First check for known ride types in cardNodes (longest match wins)
        for (known in RideTypeSanitizer.BHARAT_TAXI_KNOWN.sortedByDescending { it.length }) {
            val matchingNode = cardNodes.firstOrNull { n ->
                val t = n.text.trim()
                !RideTypeSanitizer.isRejectedCandidate(t) && (t.equals(known, ignoreCase = true) || t.contains(known, ignoreCase = true))
            }
            if (matchingNode != null) {
                rideType = known
                break
            }
        }

        // (b) If still Unknown, check short text (<= 24 chars) located above the fare node in the same card
        if (rideType == "Unknown") {
            for (n in cardNodes) {
                val isAboveFare = n.bounds.top < fareTop
                val sanitized = RideTypeSanitizer.sanitize(n.text, appId = "bharat_taxi", isAboveFareNode = isAboveFare)
                if (sanitized != "Unknown") {
                    rideType = sanitized
                    break
                }
            }
        }

        // 5. Address extraction
        // First try tv_primary_text
        val primaryTexts = cardNodes.filter { (it.viewId?.substringAfter(":id/") ?: "") == "tv_primary_text" }
        if (primaryTexts.size >= 2) {
            pickupAddress = primaryTexts[0].text.trim()
            dropAddress = primaryTexts[1].text.trim()
        } else {
            // Fall back to text position between distance lines
            if (pickupDistNodeIdx != -1 && dropDistNodeIdx != -1 && pickupDistNodeIdx < dropDistNodeIdx) {
                val pickupParts = mutableListOf<String>()
                for (i in (pickupDistNodeIdx + 1) until dropDistNodeIdx) {
                    val text = cardNodes[i].text.trim()
                    if (text.isNotEmpty() && !isIgnoredBharatText(text)) {
                        pickupParts.add(text)
                    }
                }
                pickupAddress = pickupParts.joinToString(", ")
            }

            if (dropDistNodeIdx != -1) {
                val dropParts = mutableListOf<String>()
                for (i in (dropDistNodeIdx + 1) until (cardNodes.size - 1)) {
                    val text = cardNodes[i].text.trim()
                    if (text.isNotEmpty() && !isIgnoredBharatText(text)) {
                        dropParts.add(text)
                    }
                }
                dropAddress = dropParts.joinToString(", ")
            }
        }

        if (pickupAddress.isEmpty()) pickupAddress = "Pickup Location"
        if (dropAddress.isEmpty()) dropAddress = "Drop Location"

        if (baseFare == null || pickupDistKm == null || dropDistKm == null) {
            return null
        }

        // 6. Safety check & confidence calculation (Rule B)
        var parseConfidence = "HIGH"
        var parseReason: String? = null

        val totalFare = baseFare
        if (appFarePerKm != null && dropDistKm > 0) {
            val calculatedFarePerKm = totalFare / dropDistKm
            val diff = abs(calculatedFarePerKm - appFarePerKm) / appFarePerKm
            if (diff > 0.35) {
                parseConfidence = "LOW"
                parseReason = "Fare/km mismatch"
            }
        }

        if (chosenFareNode?.text?.contains("/km", ignoreCase = true) == true) {
            parseConfidence = "LOW"
            parseReason = "Fare node contains /km"
        }

        if (totalFare < 5.0) {
            parseConfidence = "LOW"
            parseReason = "Fare below ₹5"
        }

        val acceptNode = rideAcceptNode ?: cardNodes.last()
        val acceptBounds = acceptNode.bounds
        val centerX = acceptBounds.centerX()
        val centerY = acceptBounds.centerY()
        val centerInsideScreen = centerX >= screenBounds.left && centerX <= screenBounds.right &&
                centerY >= screenBounds.top && centerY <= screenBounds.bottom
        val isEnabled = acceptNode.node?.isEnabled ?: true
        val isActionable = acceptNode.isVisibleToUser && isEnabled && centerInsideScreen

        val rawTextHash = computeHash("bharat_${rideType}_${baseFare}_${pickupAddress}_${dropAddress}")
        val rawCard = buildRawCardJson(cardNodes, layoutVariant, parseConfidence, parseReason)

        return RideOffer(
            appId = "bharat_taxi",
            rideType = rideType,
            baseFare = baseFare,
            extraFare = 0.0,
            totalFare = totalFare,
            appFarePerKm = appFarePerKm,
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
            isActionable = isActionable,
            parseConfidence = parseConfidence,
            parseReason = parseReason,
            layoutVariant = layoutVariant,
            rawCard = rawCard
        )
    }

    fun parseDistanceTime(text: String): Pair<Double, Int?>? {
        val m = DIST_TIME_PATTERN.matcher(text)
        if (m.find()) {
            val value = m.group(1)?.toDoubleOrNull() ?: 0.0
            val unit = m.group(2)?.lowercase(Locale.ROOT) ?: "km"
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

    private fun isIgnoredBharatText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.startsWith("+") || lower == "x" || lower == "more requests" ||
                lower.startsWith("₹") || lower == "ride_decline" || lower == "decline" ||
                DIST_TIME_PATTERN.matcher(text).find() || PER_KM_PATTERN.matcher(text).find()
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
