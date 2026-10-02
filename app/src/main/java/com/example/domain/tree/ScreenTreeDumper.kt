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
    val isFewNodesWarning: Boolean
) {
    fun toSummaryText(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return """
            --- Screen Tree Dump Summary ---
            Timestamp: ${dateFormat.format(Date(timestamp))}
            File: $fileName
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
    private const val MAX_DUMPS = 5

    suspend fun dumpScreenTree(
        context: Context,
        windows: List<AccessibilityWindowInfo>?,
        rootNode: AccessibilityNodeInfo?,
        appPackage: String
    ): ScreenTreeSummary = withContext(Dispatchers.IO) {
        val dumpDir = File(context.filesDir, DUMP_DIR_NAME).apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val fileName = "dump_${appPackage.replace(".", "_")}_$dateStr.txt"
        val outputFile = File(dumpDir, fileName)

        val sb = StringBuilder()
        sb.append("=== TRIP-PILOT SCREEN TREE DUMP ===\n")
        sb.append("Package: $appPackage\n")
        sb.append("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}\n\n")

        var totalNodes = 0
        var nodesWithText = 0
        var nodesWithDesc = 0
        var hasAccept = false
        var isAcceptClickable = false

        fun dumpNode(node: AccessibilityNodeInfo, depth: Int, parentIdx: Int, currentIndex: Int) {
            totalNodes++
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
            sb.append("${indent}[Node #$currentIndex, Parent #$parentIdx, Depth $depth]\n")
            sb.append("${indent}  class: ${node.className}\n")
            sb.append("${indent}  viewId: ${node.viewIdResourceName}\n")
            if (text.isNotEmpty()) sb.append("${indent}  text: \"$text\"\n")
            if (desc.isNotEmpty()) sb.append("${indent}  contentDesc: \"$desc\"\n")
            sb.append("${indent}  bounds: [${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\n")
            sb.append("${indent}  clickable: ${node.isClickable}, enabled: ${node.isEnabled}, visible: ${node.isVisibleToUser}\n")
            sb.append("${indent}  package: ${node.packageName}\n\n")

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) {
                    dumpNode(child, depth + 1, currentIndex, totalNodes)
                }
            }
        }

        if (!windows.isNullOrEmpty()) {
            sb.append("Total Windows: ${windows.size}\n\n")
            windows.forEachIndexed { wIdx, win ->
                sb.append("--- Window #$wIdx (type: ${win.type}, title: ${win.title}) ---\n")
                val root = try { win.root } catch (_: Exception) { null }
                if (root != null) {
                    dumpNode(root, 1, -1, totalNodes)
                }
            }
        } else if (rootNode != null) {
            dumpNode(rootNode, 0, -1, 0)
        }

        outputFile.writeText(sb.toString())

        // Prune older dumps to keep last MAX_DUMPS
        val existingDumps = dumpDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (existingDumps.size > MAX_DUMPS) {
            existingDumps.drop(MAX_DUMPS).forEach { it.delete() }
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
            isFewNodesWarning = totalNodes < 5
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
