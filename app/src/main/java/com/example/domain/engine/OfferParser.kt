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

    private fun traverseNode(root: AccessibilityNodeInfo, list: MutableList<ParsedNode>) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty() && list.size < 600) {
            val (node, depth) = stack.removeLast()
            if (depth > 40) continue

            val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
            if (viewId?.contains("floating_layout") == true) {
                continue
            }

            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val combinedText = when {
                text.isNotEmpty() && contentDesc.isNotEmpty() && text != contentDesc -> "$text $contentDesc"
                text.isNotEmpty() -> text
                else -> contentDesc
            }

            val bounds = Rect()
            try { node.getBoundsInScreen(bounds) } catch (_: Exception) {}
            val className = try { node.className?.toString() } catch (_: Exception) { null }

            if (combinedText.isNotBlank() || !viewId.isNullOrEmpty()) {
                list.add(
                    ParsedNode(
                        node = node,
                        text = combinedText,
                        bounds = bounds,
                        isVisibleToUser = node.isVisibleToUser,
                        isClickable = node.isClickable,
                        viewId = viewId,
                        className = className,
                        depth = depth
                    )
                )
            }

            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in childCount - 1 downTo 0) {
                if (list.size >= 600) break
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) {
                    stack.addLast(child to (depth + 1))
                }
            }
        }
    }
}
