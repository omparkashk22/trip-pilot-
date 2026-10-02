package com.example.domain.engine

import android.graphics.Rect
import com.example.data.model.RideOffer
import java.security.MessageDigest
import java.util.regex.Pattern

object RapidoParser {

    private val FARE_PATTERN = Pattern.compile("₹\\s*([0-9][0-9,]*(\\.[0-9]+)?)\\s*(\\+\\s*₹\\s*([0-9][0-9,]*(\\.[0-9]+)?))?")
    private val DIST_PATTERN = Pattern.compile("([0-9]+(\\.[0-9]+)?)\\s*(km|m)\\b", Pattern.CASE_INSENSITIVE)

    fun parseNodes(
        nodes: List<ParsedNode>,
        screenBounds: Rect = Rect(0, 0, 1080, 2400)
    ): List<RideOffer> {
        val offers = mutableListOf<RideOffer>()

        // Find any floating overlay bounds like "Extra from Customer"
        val overlayBoundsList = mutableListOf<Rect>()
        for (n in nodes) {
            val t = n.text.trim()
            if (t.contains("Extra from Customer", ignoreCase = true)) {
                overlayBoundsList.add(n.bounds)
            }
        }

        // Each card ends with "Accept"
        val acceptIndices = mutableListOf<Int>()
        for (i in nodes.indices) {
            val text = nodes[i].text.trim()
            if (text.equals("Accept", ignoreCase = true) || text.equals("स्वीकार करें", ignoreCase = true)) {
                acceptIndices.add(i)
            }
        }

        if (acceptIndices.isEmpty()) return emptyList()

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
        var rideType = "Bike"
        var baseFare: Double? = null
        var extraFare: Double = 0.0

        var pickupDistKm: Double? = null
        var dropDistKm: Double? = null

        var pickupDistNodeIdx = -1
        var dropDistNodeIdx = -1

        for (i in cardNodes.indices) {
            val t = cardNodes[i].text.trim()

            // Skip top navigation / rails
            if (t.equals("Go To", ignoreCase = true) || t.equals("Stay In", ignoreCase = true)) {
                continue
            }

            // Ride type badge (e.g. "Bike Boost", "Auto", "Bike Lite", "Cab")
            if (isRideTypeCandidate(t) && baseFare == null) {
                rideType = t
            }

            // Fare line: "₹223 + ₹137" or "₹50"
            if (baseFare == null && t.contains("₹")) {
                val fm = FARE_PATTERN.matcher(t)
                if (fm.find()) {
                    baseFare = fm.group(1)?.replace(",", "")?.toDoubleOrNull()
                    val extraStr = fm.group(4)?.replace(",", "")
                    if (extraStr != null) {
                        extraFare = extraStr.toDoubleOrNull() ?: 0.0
                    }
                }
            }

            // Distance parsing
            val dm = DIST_PATTERN.matcher(t)
            if (dm.find()) {
                val value = dm.group(1)?.toDoubleOrNull() ?: 0.0
                val unit = dm.group(3)?.lowercase() ?: "km"
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

        if (baseFare == null || pickupDistKm == null || dropDistKm == null || pickupDistNodeIdx == -1 || dropDistNodeIdx == -1) {
            return null
        }

        // Pickup address: between pickupDistNodeIdx and dropDistNodeIdx
        val pickupParts = mutableListOf<String>()
        for (i in (pickupDistNodeIdx + 1) until dropDistNodeIdx) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredRapidoText(text)) {
                pickupParts.add(text)
            }
        }
        val pickupAddress = pickupParts.joinToString(" - ").ifEmpty { "Pickup Location" }

        // Drop address: between dropDistNodeIdx and accept button
        val dropParts = mutableListOf<String>()
        for (i in (dropDistNodeIdx + 1) until (cardNodes.size - 1)) {
            val text = cardNodes[i].text.trim()
            if (text.isNotEmpty() && !isIgnoredRapidoText(text)) {
                dropParts.add(text)
            }
        }
        val dropAddress = dropParts.joinToString(" - ").ifEmpty { "Destination Address" }

        val acceptNode = cardNodes.last()
        val acceptBounds = acceptNode.bounds

        // Check if drop address is near the bottom cutoff of the screen
        val dropAddressTruncated = acceptBounds.bottom >= (screenBounds.bottom - 40) ||
                (cardNodes.size > dropDistNodeIdx + 1 && cardNodes[dropDistNodeIdx + 1].bounds.bottom >= screenBounds.bottom - 80)

        // Overlap check with floating overlay
        var intersectsOverlay = false
        for (overlay in overlayBoundsList) {
            if (acceptBounds.left < overlay.right && overlay.left < acceptBounds.right &&
                acceptBounds.top < overlay.bottom && overlay.top < acceptBounds.bottom) {
                intersectsOverlay = true
                break
            }
        }

        val isInsideScreen = acceptBounds.left >= 0 && acceptBounds.top >= 0 &&
                acceptBounds.right <= screenBounds.right && acceptBounds.bottom <= screenBounds.bottom
        val isActionable = acceptNode.isVisibleToUser && isInsideScreen && !intersectsOverlay

        val rawTextHash = computeHash("rapido_${rideType}_${baseFare}_${extraFare}_${pickupAddress}_${dropAddress}")

        return RideOffer(
            appId = "rapido",
            rideType = rideType,
            baseFare = baseFare,
            extraFare = extraFare,
            totalFare = baseFare + extraFare,
            pickupDistanceKm = pickupDistKm,
            pickupEtaMin = null,
            dropDistanceKm = dropDistKm,
            dropEtaMin = null,
            pickupAddress = pickupAddress,
            dropAddress = dropAddress,
            dropAddressTruncated = dropAddressTruncated,
            isTest = false,
            rawTextHash = rawTextHash,
            acceptNodeBounds = acceptBounds,
            acceptNode = acceptNode.node,
            isActionable = isActionable
        )
    }

    private fun isRideTypeCandidate(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("bike") || lower.contains("auto") || lower.contains("cab") ||
                lower.contains("boost") || lower.contains("lite") || lower.contains("express")
    }

    private fun isIgnoredRapidoText(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "-" || lower == "accept" || lower.startsWith("₹") ||
                lower.contains("extra from customer") || lower == "go to" || lower == "stay in"
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
