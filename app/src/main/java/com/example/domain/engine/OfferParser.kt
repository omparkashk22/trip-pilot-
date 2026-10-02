package com.example.domain.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.data.model.RideOffer

object OfferParser {

    fun parseWindows(
        windows: List<AccessibilityWindowInfo>?,
        rootNode: AccessibilityNodeInfo?,
        appId: String,
        processTestRequests: Boolean = true,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): List<RideOffer> {
        val screenBounds = Rect(0, 0, screenWidth, screenHeight)
        val flatNodes = mutableListOf<ParsedNode>()

        if (!windows.isNullOrEmpty()) {
            for (w in windows) {
                val root = try { w.root } catch (_: Exception) { null }
                if (root != null) {
                    traverseNode(root, flatNodes)
                }
            }
        } else if (rootNode != null) {
            traverseNode(rootNode, flatNodes)
        }

        if (flatNodes.isEmpty()) return emptyList()

        return when (appId) {
            "bharat_taxi" -> BharatTaxiParser.parseNodes(flatNodes, processTestRequests, screenBounds)
            "rapido" -> RapidoParser.parseNodes(flatNodes, screenBounds)
            else -> {
                // Try Bharat Taxi first then Rapido
                val bt = BharatTaxiParser.parseNodes(flatNodes, processTestRequests, screenBounds)
                if (bt.isNotEmpty()) bt else RapidoParser.parseNodes(flatNodes, screenBounds)
            }
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo, list: MutableList<ParsedNode>) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val combinedText = when {
            text.isNotEmpty() && contentDesc.isNotEmpty() && text != contentDesc -> "$text $contentDesc"
            text.isNotEmpty() -> text
            else -> contentDesc
        }

        val bounds = Rect()
        try { node.getBoundsInScreen(bounds) } catch (_: Exception) {}

        if (combinedText.isNotBlank()) {
            list.add(
                ParsedNode(
                    node = node,
                    text = combinedText,
                    bounds = bounds,
                    isVisibleToUser = node.isVisibleToUser,
                    isClickable = node.isClickable
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                traverseNode(child, list)
            }
        }
    }
}
