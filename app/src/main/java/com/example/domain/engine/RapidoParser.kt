package com.example.domain.engine

import android.graphics.Rect
import com.example.data.model.RideOffer
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

object RapidoParser {

    private val FARE_PATTERN = Pattern.compile(
        "₹\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(?!\\s*/\\s*km)(?![0-9])",
        Pattern.CASE_INSENSITIVE
    )

    private val PER_KM_PATTERN = Pattern.compile(
        "₹?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*km",
        Pattern.CASE_INSENSITIVE
    )

    // Extra fare pattern: ^\s*\+\s*₹?\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*$
    private val EXTRA_FARE_PATTERN = Pattern.compile(
        "^\\s*\\+\\s*₹?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*$"
    )

    // One-node fare: "₹116 + ₹10"
    private val COMBINED_FARE_PATTERN = Pattern.compile(
        "₹\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*\\+\\s*₹?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
    )

    // Distance regex: "1.7 km"
    private val DIST_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE)

    // Drop ETA regex: (12 mins) or (12 min)
    private val DROP_ETA_PATTERN = Pattern.compile("\\(\\s*([0-9]+)\\s*mins?\\s*\\)", Pattern.CASE_INSENSITIVE)

    // Green banner: Customer added ₹10.0 extra
    private val BANNER_PATTERN = Pattern.compile(
        "(?i)^\\s*customer added\\s*₹?\\s*([0-9.,]+)\\s*extra\\s*$"
    )

    val KNOWN_RIDE_TYPES = listOf(
        "Cab Economy",
        "Cab Premium",
        "Cab XL",
        "Bike Boost",
        "Auto Boost",
        "Cab Boost",
        "Bike",
        "Auto",
        "Cab"
    )

    fun parseNodes(
        nodes: List<ParsedNode>,
        screenBounds: Rect = Rect(0, 0, 1080, 2400)
    ): List<RideOffer> {
        if (nodes.isEmpty()) return emptyList()

        // 1. Check for "Not an offer" screens
        for (n in nodes) {
            val text = n.text.trim()
            if (text.contains("You missed the order", ignoreCase = true) ||
                text.contains("Sorry, this order was accepted by another captain", ignoreCase = true)
            ) {
                return emptyList()
            }
        }

        // Must have an Accept node in the window
        val acceptIndices = mutableListOf<Int>()
        for (i in nodes.indices) {
            val text = nodes[i].text.trim()
            if (text.equals("Accept", ignoreCase = true) || text.equals("स्वीकार करें", ignoreCase = true)) {
                acceptIndices.add(i)
            }
        }
        if (acceptIndices.isEmpty()) return emptyList()

        // Overlays like "Extra from Customer"
        val overlayBoundsList = mutableListOf<Rect>()
        for (n in nodes) {
            val t = n.text.trim()
            if (t.contains("Extra from Customer", ignoreCase = true)) {
                overlayBoundsList.add(n.bounds)
            }
        }

        val offers = mutableListOf<RideOffer>()
        var cardStart = 0
        for (acceptIdx in acceptIndices) {
            val cardNodes = nodes.subList(cardStart, acceptIdx + 1)
            val offer = parseSingleCard(cardNodes, screenBounds, overlayBoundsList)
            if (offer != null) {
                offers.add(offer)
            }
            cardStart = acceptIdx + 1
        }

        return offers
    }

    private fun parseSingleCard(
        cardNodes: List<ParsedNode>,
        screenBounds: Rect,
        overlayBoundsList: List<Rect>
    ): RideOffer? {
        var rideType = "Unknown"
        var baseFare: Double? = null
        var chosenFareNode: ParsedNode? = null
        var chosenFareIdx = -1
        var extraFare = 0.0
        var bannerExtraFare: Double? = null
        var appFarePerKm: Double? = null

        var pickupDistKm: Double? = null
        var dropDistKm: Double? = null
        var dropEtaMin: Int? = null

        var pickupDistNodeIdx = -1
        var dropDistNodeIdx = -1

        // 1. Scan for Banner: "Customer added ₹10.0 extra"
        for (n in cardNodes) {
            val text = n.text.trim()
            val bm = BANNER_PATTERN.matcher(text)
            if (bm.find()) {
                val amt = bm.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (amt != null) {
                    bannerExtraFare = amt
                }
            }
            // Check for per-km rate if present
            val pkm = PER_KM_PATTERN.matcher(text)
            if (pkm.find() && appFarePerKm == null) {
                appFarePerKm = pkm.group(1)?.toDoubleOrNull()
            }
        }

        // 2. Scan for candidate fare nodes (Rule A: largest bounds height, on tie first in tree order)
        data class FareCandidate(val node: ParsedNode, val base: Double, val extra: Double, val height: Int, val index: Int)
        val candidates = mutableListOf<FareCandidate>()

        for (i in cardNodes.indices) {
            val n = cardNodes[i]
            val text = n.text.trim()

            if (text.contains("/km", ignoreCase = true) || PER_KM_PATTERN.matcher(text).find() ||
                text.startsWith("+") || EXTRA_FARE_PATTERN.matcher(text).find()
            ) {
                continue
            }

            // Check if combined: "₹116 + ₹10"
            val combM = COMBINED_FARE_PATTERN.matcher(text)
            if (combM.find()) {
                val b = combM.group(1)?.replace(",", "")?.toDoubleOrNull()
                val e = combM.group(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                if (b != null) {
                    candidates.add(FareCandidate(n, b, e, n.bounds.height(), i))
                    continue
                }
            }

            // Single fare match
            val fm = FARE_PATTERN.matcher(text)
            if (fm.find()) {
                val b = fm.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (b != null) {
                    candidates.add(FareCandidate(n, b, 0.0, n.bounds.height(), i))
                }
            }
        }

        if (candidates.isNotEmpty()) {
            candidates.sortWith(compareByDescending<FareCandidate> { it.height }.thenBy { it.index })
            val best = candidates.first()
            baseFare = best.base
            extraFare = best.extra
            chosenFareNode = best.node
            chosenFareIdx = best.index
        }

        // 3. Find extra fare across nodes if not already parsed
        // After chosen fare node, within next 3 nodes and before first distance line:
        // accept ^\s*\+\s*₹?\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*$
        // or a lone "+" followed by a "₹NN" node
        if (baseFare != null && extraFare == 0.0 && chosenFareIdx != -1) {
            val searchLimit = minOf(chosenFareIdx + 4, cardNodes.size)
            var j = chosenFareIdx + 1
            while (j < searchLimit) {
                val nodeText = cardNodes[j].text.trim()
                // Stop if we hit a distance node
                if (DIST_PATTERN.matcher(nodeText).find()) break

                val extraM = EXTRA_FARE_PATTERN.matcher(nodeText)
                if (extraM.find()) {
                    val amt = extraM.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (amt != null) {
                        extraFare = amt
                        break
                    }
                } else if (nodeText == "+") {
                    // Check next node for "₹26" or "26"
                    if (j + 1 < searchLimit) {
                        val nextText = cardNodes[j + 1].text.trim()
                        val num = parseNumber(nextText)
                        if (num != null) {
                            extraFare = num
                            break
                        }
                    }
                }
                j++
            }
        }

        // If banner extra exists, use it as extraFare (or if they differ, trust banner)
        var bannerDiffers = false
        if (bannerExtraFare != null) {
            if (extraFare > 0.0 && extraFare != bannerExtraFare) {
                bannerDiffers = true
            }
            extraFare = bannerExtraFare
        }

        // 4. Distances and Drop ETA
        for (i in cardNodes.indices) {
            val text = cardNodes[i].text.trim()

            // Skip top navigation / rails
            if (text.equals("Go To", ignoreCase = true) || text.equals("Stay In", ignoreCase = true)) {
                continue
            }

            // Drop ETA: "(12 mins)" or "(12 min)"
            val etaM = DROP_ETA_PATTERN.matcher(text)
            if (etaM.find()) {
                dropEtaMin = etaM.group(1)?.toIntOrNull()
            }

            // Distance match
            val dm = DIST_PATTERN.matcher(text)
            if (dm.find()) {
                val value = dm.group(1)?.toDoubleOrNull() ?: 0.0
                val unit = dm.group(2)?.lowercase(Locale.ROOT) ?: "km"
                val distKm = if (unit == "m") value / 1000.0 else value

                if (pickupDistKm == null) {
                    pickupDistKm = distKm
                    pickupDistNodeIdx = i
                } else if (dropDistKm == null) {
                    dropDistKm = distKm
                    dropDistNodeIdx = i
                }
            }
        }

        // 5. Ride type detection order:
        // (1) ride-type badge text (label above the fare)
        // (2) contentDescription of vehicle icon near top
        // (3) matching known list: "Cab Economy", "Cab Premium", "Cab XL", "Bike Boost", "Auto Boost", "Cab Boost", "Bike", "Auto", "Cab"
        // (4) otherwise "Unknown"
        if (chosenFareIdx > 0) {
            // Check nodes above the fare
            for (i in 0 until chosenFareIdx) {
                val t = cardNodes[i].text.trim()
                if (t.isNotEmpty() && !t.startsWith("₹") && !DIST_PATTERN.matcher(t).find() &&
                    !t.equals("Go To", ignoreCase = true) && !t.equals("Stay In", ignoreCase = true)
                ) {
                    val matching = KNOWN_RIDE_TYPES.firstOrNull { t.equals(it, ignoreCase = true) || t.contains(it, ignoreCase = true) }
                    if (matching != null) {
                        rideType = t // Keep full badge text as rideType (e.g. "Bike Boost")
                        break
                    }
                }
            }
        }

        if (rideType == "Unknown") {
            // Check top nodes for contentDescription matching vehicle icon
            for (i in 0 until minOf(5, cardNodes.size)) {
                val cd = cardNodes[i].node?.contentDescription?.toString()?.trim() ?: ""
                if (cd.isNotEmpty()) {
                    val matching = KNOWN_RIDE_TYPES.firstOrNull { cd.contains(it, ignoreCase = true) }
                    if (matching != null) {
                        rideType = matching
                        break
                    }
                }
            }
        }

        if (rideType == "Unknown") {
            // Check any text in the card matching known list from registry
            for (known in KNOWN_RIDE_TYPES) {
                val found = cardNodes.firstOrNull { it.text.contains(known, ignoreCase = true) }
                if (found != null) {
                    val pillText = found.text.trim()
                    // Keep full pill text if it starts with the known ride type
                    rideType = if (isLikelyRideTypePill(pillText)) pillText else known
                    break
                }
            }
        }

        if (baseFare == null || pickupDistKm == null || dropDistKm == null || pickupDistNodeIdx == -1 || dropDistNodeIdx == -1) {
            return null
        }

        // 6. Address extraction (exclude banner from both addresses)
        val pickupParts = mutableListOf<String>()
        for (i in (pickupDistNodeIdx + 1) until dropDistNodeIdx) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredRapidoText(text) && !BANNER_PATTERN.matcher(text).find()) {
                pickupParts.add(text)
            }
        }
        val pickupAddress = pickupParts.joinToString(" - ").ifEmpty { "Pickup Location" }

        val dropParts = mutableListOf<String>()
        for (i in (dropDistNodeIdx + 1) until (cardNodes.size - 1)) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredRapidoText(text) && !BANNER_PATTERN.matcher(text).find() &&
                !DROP_ETA_PATTERN.matcher(text).find()
            ) {
                dropParts.add(text)
            }
        }
        val dropAddress = dropParts.joinToString(" - ").ifEmpty { "Destination Address" }

        val totalFare = baseFare + extraFare

        // 7. Safety check and confidence calculation (Rule B)
        var parseConfidence = if (bannerDiffers) "MEDIUM" else "HIGH"
        var parseReason: String? = if (bannerDiffers) "Banner extra fare applied" else null

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

        val acceptNode = cardNodes.last()
        val acceptBounds = acceptNode.bounds

        // Check drop address truncation near bottom cutoff
        val dropAddressTruncated = acceptBounds.bottom >= (screenBounds.bottom - 40)

        // Overlay intersection check
        var intersectsOverlay = false
        for (overlay in overlayBoundsList) {
            if (acceptBounds.left < overlay.right && overlay.left < acceptBounds.right &&
                acceptBounds.top < overlay.bottom && overlay.top < acceptBounds.bottom
            ) {
                intersectsOverlay = true
                break
            }
        }

        val isInsideScreen = acceptBounds.left >= 0 && acceptBounds.top >= 0 &&
                acceptBounds.right <= screenBounds.right && acceptBounds.bottom <= screenBounds.bottom
        val isActionable = acceptNode.isVisibleToUser && isInsideScreen && !intersectsOverlay

        val rawTextHash = computeHash("rapido_${rideType}_${baseFare}_${extraFare}_${pickupAddress}_${dropAddress}")
        val rawCard = buildRawCardJson(cardNodes, "RAPIDO", parseConfidence, parseReason)

        return RideOffer(
            appId = "rapido",
            rideType = rideType,
            baseFare = baseFare,
            extraFare = extraFare,
            totalFare = totalFare,
            appFarePerKm = appFarePerKm,
            pickupDistanceKm = pickupDistKm,
            pickupEtaMin = null,
            dropDistanceKm = dropDistKm,
            dropEtaMin = dropEtaMin,
            pickupAddress = pickupAddress,
            dropAddress = dropAddress,
            dropAddressTruncated = dropAddressTruncated,
            isTest = false,
            rawTextHash = rawTextHash,
            acceptNodeBounds = acceptBounds,
            acceptNode = acceptNode.node,
            isActionable = isActionable,
            parseConfidence = parseConfidence,
            parseReason = parseReason,
            layoutVariant = "RAPIDO",
            rawCard = rawCard
        )
    }

    private fun isLikelyRideTypePill(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return KNOWN_RIDE_TYPES.any { lower.contains(it.lowercase(Locale.ROOT)) } && text.length <= 25
    }

    private fun parseNumber(text: String): Double? {
        val m = Pattern.compile("([0-9][0-9,]*(\\.[0-9]+)?)").matcher(text)
        return if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
    }

    private fun isIgnoredRapidoText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower == "-" || lower == "accept" || lower.startsWith("₹") ||
                lower.contains("extra from customer") || lower == "go to" || lower == "stay in" ||
                BANNER_PATTERN.matcher(text).find() || DROP_ETA_PATTERN.matcher(text).find() ||
                DIST_PATTERN.matcher(text).find() || EXTRA_FARE_PATTERN.matcher(text).find()
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
