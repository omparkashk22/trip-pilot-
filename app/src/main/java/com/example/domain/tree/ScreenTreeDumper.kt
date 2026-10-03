package com.example.domain.tree

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScreenTreeSummary(
    val fileName: String,
    val filePath: String,
    val timestamp: Long,
    val totalNodes: Int,
    val nodesWithText: Int,
    val nodesWithContentDescription: Int,
    val hasAcceptNode: Boolean,
    val isAcceptClickableOrAncestorClickable: Boolean,
    val isFewNodesWarning: Boolean,
    val saveSuccessful: Boolean = true,
    val errorMessage: String? = null,
    val dumpedPackages: List<String> = emptyList()
) {
    fun toSummaryText(): String {
        if (!saveSuccessful) {
            return errorMessage ?: "No useful content — open the offer first"
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return """
            --- Screen Tree Dump Summary ---
            Timestamp: ${dateFormat.format(Date(timestamp))}
            File: $fileName
            Packages Dumped: ${dumpedPackages.joinToString(", ")}
            Total Nodes: $totalNodes
            Nodes with text: $nodesWithText
            Nodes with content description: $nodesWithContentDescription
            'Accept' button found: ${if (hasAcceptNode) "YES ✓" else "NO ✗"}
            Clickable / Parent clickable: ${if (isAcceptClickableOrAncestorClickable) "YES ✓" else "NO ✗"}
            ${if (isFewNodesWarning) "⚠️ WARNING: Very few nodes detected ($totalNodes). App might be hiding content or using FLAG_SECURE." else "Tree depth and node count look healthy."}
        """.trimIndent()
    }
}

object ScreenTreeDumper {

    private const val DUMP_DIR_NAME = "screen_tree_dumps"
    const val MAX_DUMPS = 10

    private val EXCLUDED_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.markup",
        "com.android.shell",
        "com.sec.android.app.sbrowser"
    )

    suspend fun dumpScreenTree(
        context: Context,
        windows: List<AccessibilityWindowInfo>?,
        rootNode: AccessibilityNodeInfo?,
        appPackage: String,
        includeAll: Boolean = false,
        maxDumps: Int = MAX_DUMPS
    ): ScreenTreeSummary = withContext(Dispatchers.IO) {
        val dumpDir = File(context.filesDir, DUMP_DIR_NAME).apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))

        val dumpedPackages = mutableSetOf<String>()
        val selfPkg = context.packageName

        // Filter windows: root package must match appPackage (or its overlay windows)
        // Never include or save systemui, screenshot popup, TripPilot itself unless includeAll is ON
        val eligibleWindows = if (!windows.isNullOrEmpty()) {
            windows.filter { win ->
                val root = try { win.root } catch (_: Exception) { null }
                val pkg = root?.packageName?.toString()
                if (pkg != null) {
                    if (includeAll) true
                    else if (EXCLUDED_PACKAGES.contains(pkg) || pkg == selfPkg) false
                    else pkg == appPackage || pkg.contains(appPackage) ||
                            (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || win.type == AccessibilityWindowInfo.TYPE_APPLICATION) &&
                            (pkg.startsWith(appPackage.substringBeforeLast(".")))
                } else false
            }
        } else emptyList()

        var totalNodes = 0
        var nodesWithText = 0
        var nodesWithDesc = 0
        var hasAccept = false
        var isAcceptClickable = false

        val nodeLines = mutableListOf<String>()

        fun dumpNode(node: AccessibilityNodeInfo, depth: Int, parentIdx: Int, currentIndex: Int) {
            totalNodes++
            val pkg = node.packageName?.toString() ?: ""
            if (pkg.isNotEmpty()) dumpedPackages.add(pkg)

            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val bounds = Rect()
            try { node.getBoundsInScreen(bounds) } catch (_: Exception) {}

            if (text.isNotBlank()) nodesWithText++
            if (desc.isNotBlank()) nodesWithDesc++

            val isAccept = text.trim().equals("Accept", ignoreCase = true) ||
                    desc.trim().equals("Accept", ignoreCase = true) ||
                    text.trim().equals("स्वीकार करें", ignoreCase = true)

            if (isAccept) {
                hasAccept = true
                var clickable = node.isClickable
                var p = node.parent
                while (!clickable && p != null) {
                    if (p.isClickable) clickable = true
                    p = p.parent
                }
                if (clickable) isAcceptClickable = true
            }

            val indent = "  ".repeat(depth)
            nodeLines.add("${indent}[Node #$currentIndex, Parent #$parentIdx, Depth $depth]\n" +
                    "${indent}  class: ${node.className}\n" +
                    "${indent}  viewId: ${node.viewIdResourceName}\n" +
                    (if (text.isNotEmpty()) "${indent}  text: \"$text\"\n" else "") +
                    (if (desc.isNotEmpty()) "${indent}  contentDesc: \"$desc\"\n" else "") +
                    "${indent}  bounds: [${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\n" +
                    "${indent}  clickable: ${node.isClickable}, enabled: ${node.isEnabled}, visible: ${node.isVisibleToUser}\n" +
                    "${indent}  package: ${node.packageName}\n\n")

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) {
                    dumpNode(child, depth + 1, currentIndex, totalNodes)
                }
            }
        }

        if (eligibleWindows.isNotEmpty()) {
            eligibleWindows.forEachIndexed { wIdx, win ->
                val root = try { win.root } catch (_: Exception) { null }
                if (root != null) {
                    dumpNode(root, 1, -1, totalNodes)
                }
            }
        } else if (rootNode != null) {
            val pkg = rootNode.packageName?.toString() ?: ""
            if (includeAll || (!EXCLUDED_PACKAGES.contains(pkg) && pkg != selfPkg && (pkg == appPackage || pkg.contains(appPackage)))) {
                dumpNode(rootNode, 0, -1, 0)
            }
        }

        // Rule G: If target package contributes fewer than 5 nodes or no node with text, do NOT save
        if (totalNodes < 5 || nodesWithText == 0) {
            return@withContext ScreenTreeSummary(
                fileName = "",
                filePath = "",
                timestamp = timestamp,
                totalNodes = totalNodes,
                nodesWithText = nodesWithText,
                nodesWithContentDescription = nodesWithDesc,
                hasAcceptNode = hasAccept,
                isAcceptClickableOrAncestorClickable = isAcceptClickable,
                isFewNodesWarning = true,
                saveSuccessful = false,
                errorMessage = "No useful content — open the offer first",
                dumpedPackages = dumpedPackages.toList()
            )
        }

        val offerTag = if (hasAccept) "offer_yes" else "offer_no"
        val fileName = "dump_${appPackage.replace(".", "_")}_${dateStr}_$offerTag.txt"
        val outputFile = File(dumpDir, fileName)

        val sb = StringBuilder()
        sb.append("=== TRIP-PILOT SCREEN TREE DUMP ===\n")
        sb.append("Target Package: $appPackage\n")
        sb.append("Packages Dumped: ${dumpedPackages.joinToString(", ")}\n")
        sb.append("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}\n")
        sb.append("Total Nodes: $totalNodes | Nodes With Text: $nodesWithText | Has Accept: $hasAccept\n\n")

        for (line in nodeLines) {
            sb.append(line)
        }

        outputFile.writeText(sb.toString())

        // Keep up to maxDumps (default 10); never delete a good dump to make room for a poor one
        val existingDumps = dumpDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (existingDumps.size > maxDumps) {
            existingDumps.drop(maxDumps).forEach { it.delete() }
        }

        ScreenTreeSummary(
            fileName = fileName,
            filePath = outputFile.absolutePath,
            timestamp = timestamp,
            totalNodes = totalNodes,
            nodesWithText = nodesWithText,
            nodesWithContentDescription = nodesWithDesc,
            hasAcceptNode = hasAccept,
            isAcceptClickableOrAncestorClickable = isAcceptClickable,
            isFewNodesWarning = totalNodes < 5,
            saveSuccessful = true,
            dumpedPackages = dumpedPackages.toList()
        )
    }

    suspend fun getExistingDumps(context: Context): List<File> = withContext(Dispatchers.IO) {
        val dumpDir = File(context.filesDir, DUMP_DIR_NAME)
        if (!dumpDir.exists()) emptyList()
        else dumpDir.listFiles()?.sortedByDescending { it.lastModified() }?.take(MAX_DUMPS) ?: emptyList()
    }

    suspend fun deleteAllDumps(context: Context) = withContext(Dispatchers.IO) {
        val dumpDir = File(context.filesDir, DUMP_DIR_NAME)
        if (dumpDir.exists()) {
            dumpDir.listFiles()?.forEach { it.delete() }
        }
    }
}
