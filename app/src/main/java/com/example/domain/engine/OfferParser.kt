package com.example.domain.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.data.model.RideOffer

object OfferParser {

    /**
     * Fast-path detection:
     * - Searches for Accept node first using platform methods (findAccessibilityNodeInfosByViewId / findAccessibilityNodeInfosByText)
     * - Walks UP parents until the card ancestor (containing ₹ and 2 distance lines, max 8 levels)
     * - Parses ONLY that card subtree, avoiding walking the whole window tree.
     */
    fun fastPathParse(
        windows: List<AccessibilityWindowInfo>?,
        rootNode: AccessibilityNodeInfo?,
        eventSource: AccessibilityNodeInfo?,
        appId: String,
        targetPackage: String?,
        processTestRequests: Boolean = true,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): List<RideOffer> {
        val screenBounds = Rect(0, 0, screenWidth, screenHeight)
        val candidateAcceptNodes = mutableListOf<AccessibilityNodeInfo>()

        // Collect roots to search in priority order: eventSource, rootNode, then windows of targetPackage
        val rootsToSearch = mutableListOf<AccessibilityNodeInfo>()
        if (eventSource != null) {
            val pkg = try { eventSource.packageName?.toString() } catch (_: Exception) { null }
            if (pkg != "com.android.systemui" && pkg?.contains("systemui", ignoreCase = true) != true) {
                if (targetPackage == null || pkg == targetPackage) {
                    rootsToSearch.add(eventSource)
                }
            }
        }
        if (rootNode != null && !rootsToSearch.contains(rootNode)) {
            val pkg = try { rootNode.packageName?.toString() } catch (_: Exception) { null }
            if (pkg != "com.android.systemui" && pkg?.contains("systemui", ignoreCase = true) != true) {
                if (targetPackage == null || pkg == targetPackage) {
                    rootsToSearch.add(rootNode)
                }
            }
        }
        if (!windows.isNullOrEmpty()) {
            for (w in windows) {
                val root = try { w.root } catch (_: Exception) { null } ?: continue
                val pkg = try { root.packageName?.toString() } catch (_: Exception) { null }
                if (pkg == "com.android.systemui" || pkg?.contains("systemui", ignoreCase = true) == true) continue
                if (targetPackage != null && pkg != targetPackage) continue
                if (!rootsToSearch.contains(root)) {
                    rootsToSearch.add(root)
                }
            }
        }

        // Look for Accept node with platform search first
        if (appId == "bharat_taxi") {
            val pkgName = targetPackage ?: "com.bharattaxi.driver"
            for (root in rootsToSearch) {
                var byId = try { root.findAccessibilityNodeInfosByViewId("$pkgName:id/ride_accept") } catch (_: Exception) { emptyList() }
                if (byId.isNullOrEmpty()) {
                    byId = try { root.findAccessibilityNodeInfosByViewId("com.bharattaxi.driver:id/ride_accept") } catch (_: Exception) { emptyList() }
                }
                if (!byId.isNullOrEmpty()) {
                    candidateAcceptNodes.addAll(byId)
                }
            }
            if (candidateAcceptNodes.isEmpty()) {
                for (root in rootsToSearch) {
                    val byText = try { root.findAccessibilityNodeInfosByText("Accept") } catch (_: Exception) { emptyList() }
                    if (!byText.isNullOrEmpty()) {
                        candidateAcceptNodes.addAll(byText)
                    }
                    val byHindi = try { root.findAccessibilityNodeInfosByText("स्वीकार") } catch (_: Exception) { emptyList() }
                    if (!byHindi.isNullOrEmpty()) {
                        candidateAcceptNodes.addAll(byHindi)
                    }
                    if (root.text?.toString()?.equals("Accept", ignoreCase = true) == true) {
                        candidateAcceptNodes.add(root)
                    }
                }
            }
        } else if (appId == "rapido") {
            for (root in rootsToSearch) {
                val byText = try { root.findAccessibilityNodeInfosByText("Accept") } catch (_: Exception) { emptyList() }
                if (!byText.isNullOrEmpty()) {
                    candidateAcceptNodes.addAll(byText)
                }
                if (root.text?.toString()?.equals("Accept", ignoreCase = true) == true) {
                    candidateAcceptNodes.add(root)
                }
            }
        }

        if (candidateAcceptNodes.isEmpty()) {
            return emptyList()
        }

        // Walk UP parents only until the card ancestor and parse ONLY that subtree
        val allOffers = mutableListOf<RideOffer>()
        for (acceptNode in candidateAcceptNodes) {
            val cardAncestor = findCardAncestor(acceptNode)
            val cardNodes = mutableListOf<ParsedNode>()
            traverseSubtree(cardAncestor ?: acceptNode, cardNodes, maxDepth = 40, maxNodes = 400)
            if (cardNodes.isNotEmpty()) {
                val offers = when (appId) {
                    "bharat_taxi" -> BharatTaxiParser.parseNodes(cardNodes, processTestRequests, screenBounds)
                    "rapido" -> RapidoParser.parseNodes(cardNodes, screenBounds)
                    else -> BharatTaxiParser.parseNodes(cardNodes, processTestRequests, screenBounds)
                }
                allOffers.addAll(offers)
            }
        }

        return allOffers
    }

    private fun findCardAncestor(acceptNode: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var curr: AccessibilityNodeInfo? = acceptNode.parent
        var depth = 0
        var bestCandidate: AccessibilityNodeInfo = acceptNode

        while (curr != null && depth < 8) {
            bestCandidate = curr
            if (hasFareAndDistances(curr)) {
                return curr
            }
            curr = curr.parent
            depth++
        }
        return bestCandidate
    }

    private fun hasFareAndDistances(root: AccessibilityNodeInfo): Boolean {
        var hasFare = false
        var distCount = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var scanned = 0

        while (queue.isNotEmpty() && scanned < 80) {
            val node = queue.removeFirst()
            scanned++
            val text = node.text?.toString() ?: ""
            val cd = node.contentDescription?.toString() ?: ""
            if (text.contains("₹") || cd.contains("₹")) {
                hasFare = true
            }
            if (BharatTaxiParser.DIST_TIME_PATTERN.matcher(text).find() || RapidoParser.DIST_PATTERN.matcher(text).find()) {
                distCount++
            }
            if (hasFare && distCount >= 2) {
                return true
            }
            val count = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return hasFare && distCount >= 2
    }

    private fun traverseSubtree(root: AccessibilityNodeInfo, list: MutableList<ParsedNode>, maxDepth: Int = 40, maxNodes: Int = 400) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty() && list.size < maxNodes) {
            val (node, depth) = stack.removeLast()
            if (depth > maxDepth) continue

            val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }
            if (viewId?.contains("floating_layout") == true) continue

            val pkg = try { node.packageName?.toString() } catch (_: Exception) { null }
            if (pkg == "com.android.systemui" || pkg?.contains("systemui", ignoreCase = true) == true) continue

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
                if (list.size >= maxNodes) break
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) {
                    stack.addLast(child to (depth + 1))
                }
            }
        }
    }

    /**
     * Fallback full tree parser (used for ScreenTreeDumper, inspector, or unrecognized screens)
     */
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
                val root = try { w.root } catch (_: Exception) { null } ?: continue
                val pkg = try { root.packageName?.toString() } catch (_: Exception) { null }
                if (pkg == "com.android.systemui" || pkg?.contains("systemui", ignoreCase = true) == true) {
                    continue
                }
                traverseNode(root, flatNodes)
            }
        } else if (rootNode != null) {
            val pkg = try { rootNode.packageName?.toString() } catch (_: Exception) { null }
            if (pkg != "com.android.systemui" && pkg?.contains("systemui", ignoreCase = true) != true) {
                traverseNode(rootNode, flatNodes)
            }
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

            val pkg = try { node.packageName?.toString() } catch (_: Exception) { null }
            if (pkg == "com.android.systemui" || pkg?.contains("systemui", ignoreCase = true) == true) {
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
