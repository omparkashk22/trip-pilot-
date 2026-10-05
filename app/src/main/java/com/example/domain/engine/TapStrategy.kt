package com.example.domain.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.StrategyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class TapMethodType(val key: String, val displayName: String) {
    NODE_CLICK("node_click", "Node Click"),
    PARENT_CLICK("parent_click", "Parent Click"),
    GESTURE_TAP("gesture", "Gesture Tap")
}

data class TapExecutionResult(
    val success: Boolean,
    val methodUsed: TapMethodType,
    val latencyMs: Long
)

object TapStrategyExecutor {

    fun performMethodSync(
        service: AccessibilityService?,
        node: AccessibilityNodeInfo?,
        bounds: Rect?,
        method: TapMethodType
    ): Boolean {
        return when (method) {
            TapMethodType.NODE_CLICK -> {
                if (node != null && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else false
            }
            TapMethodType.PARENT_CLICK -> {
                var parent = node?.parent
                var clicked = false
                while (parent != null) {
                    if (parent.isClickable) {
                        clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        break
                    }
                    parent = parent.parent
                }
                clicked
            }
            TapMethodType.GESTURE_TAP -> {
                if (service != null && bounds != null && !bounds.isEmpty && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val path = Path().apply {
                        moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                    }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 50)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    service.dispatchGesture(gesture, null, null)
                } else false
            }
        }
    }

    fun executeTapImmediate(
        service: AccessibilityService?,
        node: AccessibilityNodeInfo?,
        nodeBounds: Rect?,
        forcedMethod: String = "auto",
        strategyStats: Map<String, StrategyStats> = emptyMap(),
        attemptMethod: TapMethodType? = null
    ): TapExecutionResult {
        val startTime = android.os.SystemClock.uptimeMillis()

        if (attemptMethod != null) {
            val ok = performMethodSync(service, node, nodeBounds, attemptMethod)
            val latency = android.os.SystemClock.uptimeMillis() - startTime
            return TapExecutionResult(ok, attemptMethod, latency)
        }

        if (forcedMethod != "auto") {
            val chosen = when (forcedMethod) {
                "node_click" -> TapMethodType.NODE_CLICK
                "parent_click" -> TapMethodType.PARENT_CLICK
                "gesture" -> TapMethodType.GESTURE_TAP
                else -> TapMethodType.NODE_CLICK
            }
            val ok = performMethodSync(service, node, nodeBounds, chosen)
            val latency = android.os.SystemClock.uptimeMillis() - startTime
            return TapExecutionResult(ok, chosen, latency)
        }

        val orderedMethods = listOf(
            TapMethodType.NODE_CLICK,
            TapMethodType.PARENT_CLICK,
            TapMethodType.GESTURE_TAP
        ).sortedByDescending { method ->
            strategyStats[method.key]?.successRate ?: 0.5
        }

        for (method in orderedMethods) {
            val ok = performMethodSync(service, node, nodeBounds, method)
            if (ok) {
                val latency = android.os.SystemClock.uptimeMillis() - startTime
                return TapExecutionResult(true, method, latency)
            }
        }

        val latency = android.os.SystemClock.uptimeMillis() - startTime
        return TapExecutionResult(false, TapMethodType.NODE_CLICK, latency)
    }

    suspend fun executeTap(
        service: AccessibilityService?,
        node: AccessibilityNodeInfo?,
        nodeBounds: Rect?,
        forcedMethod: String = "auto",
        strategyStats: Map<String, StrategyStats> = emptyMap()
    ): TapExecutionResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()

        if (forcedMethod != "auto") {
            val chosen = when (forcedMethod) {
                "node_click" -> TapMethodType.NODE_CLICK
                "parent_click" -> TapMethodType.PARENT_CLICK
                "gesture" -> TapMethodType.GESTURE_TAP
                else -> TapMethodType.NODE_CLICK
            }
            val ok = performMethod(service, node, nodeBounds, chosen)
            val latency = System.currentTimeMillis() - startTime
            return@withContext TapExecutionResult(ok, chosen, latency)
        }

        // Ordered strategy list based on stats
        val orderedMethods = listOf(
            TapMethodType.NODE_CLICK,
            TapMethodType.PARENT_CLICK,
            TapMethodType.GESTURE_TAP
        ).sortedByDescending { method ->
            strategyStats[method.key]?.successRate ?: 0.5
        }

        for (method in orderedMethods) {
            val ok = performMethod(service, node, nodeBounds, method)
            if (ok) {
                val latency = System.currentTimeMillis() - startTime
                return@withContext TapExecutionResult(true, method, latency)
            }
        }

        val latency = System.currentTimeMillis() - startTime
        TapExecutionResult(false, TapMethodType.NODE_CLICK, latency)
    }

    private suspend fun performMethod(
        service: AccessibilityService?,
        node: AccessibilityNodeInfo?,
        bounds: Rect?,
        method: TapMethodType
    ): Boolean {
        return when (method) {
            TapMethodType.NODE_CLICK -> {
                if (node != null && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else false
            }
            TapMethodType.PARENT_CLICK -> {
                var parent = node?.parent
                var clicked = false
                while (parent != null) {
                    if (parent.isClickable) {
                        clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        break
                    }
                    parent = parent.parent
                }
                clicked
            }
            TapMethodType.GESTURE_TAP -> {
                if (service != null && bounds != null && !bounds.isEmpty) {
                    dispatchTapGesture(service, bounds.centerX().toFloat(), bounds.centerY().toFloat())
                } else false
            }
        }
    }

    private suspend fun dispatchTapGesture(
        service: AccessibilityService,
        x: Float,
        y: Float
    ): Boolean = suspendCancellableCoroutine<Boolean> { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
        } else {
            cont.resume(false)
        }
    }
}
