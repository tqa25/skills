package com.flowbot.app.core.detection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flowbot.app.data.model.ElementSelector
import kotlinx.coroutines.delay

/**
 * [AccessibilityService] that exposes the live accessibility node tree to
 * the rest of the app, and implements [ElementDetector] for workflow
 * element lookups.
 *
 * The service must be declared in the manifest with the appropriate
 * `<accessibility-service>` meta-data. Enabling it is a user action
 * (Settings → Accessibility → FlowBot).
 *
 * A companion-object singleton reference is the simplest reliable pattern for
 * AccessibilityService because the OS owns the lifecycle.
 */
class FlowBotAccessibilityService : AccessibilityService(), ElementDetector {

    companion object {
        private const val TAG = "FlowBotA11y"

        /** Live reference; non-null only while the service is bound. */
        @Volatile
        var instance: FlowBotAccessibilityService? = null
            private set

        /** Polling interval when waiting for an element to appear. */
        private const val POLL_INTERVAL_MS = 250L
    }

    // ── Service lifecycle ───────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't react to events; we traverse the tree on demand.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Service destroyed")
    }

    // ── ElementDetector implementation ──────────────────────────────────

    override suspend fun findElement(
        selector: ElementSelector,
        timeoutMs: Long,
    ): Result<DetectedElement> {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val match = searchNode(root, selector, findAll = false).firstOrNull()
                root.recycle()
                if (match != null) return Result.success(match)
            }
            delay(POLL_INTERVAL_MS)
        }

        return Result.failure(
            NoSuchElementException(
                buildString {
                    append("Element not found within ${timeoutMs}ms — selector: ")
                    append(selector)
                },
            ),
        )
    }

    override suspend fun findAllElements(selector: ElementSelector): Result<List<DetectedElement>> {
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("No active window"))

        val results = searchNode(root, selector, findAll = true)
        root.recycle()
        return Result.success(results)
    }

    override fun isServiceRunning(): Boolean = instance != null

    // ── Tree traversal ──────────────────────────────────────────────────

    /**
     * Depth-first search of the accessibility node tree.
     *
     * If [findAll] is false, stops after the first match (or returns empty list).
     * The caller is responsible for recycling the root node.
     */
    private fun searchNode(
        node: AccessibilityNodeInfo,
        selector: ElementSelector,
        findAll: Boolean,
    ): List<DetectedElement> {
        val results = mutableListOf<DetectedElement>()
        traverseNode(node, selector, results, findAll)
        return results
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        selector: ElementSelector,
        results: MutableList<DetectedElement>,
        findAll: Boolean,
    ) {
        if (!findAll && results.isNotEmpty()) return

        if (matchesSelector(node, selector)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            results += DetectedElement(
                bounds = bounds,
                text = node.text?.toString(),
                className = node.className?.toString(),
                contentDescription = node.contentDescription?.toString(),
            )
            if (!findAll) return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, selector, results, findAll)
            child.recycle()
            if (!findAll && results.isNotEmpty()) return
        }
    }

    /**
     * Check whether a single node matches the given [ElementSelector].
     *
     * All non-null fields in the selector must match (AND logic).
     * Text and contentDescription use *contains* (case-insensitive) for
     * practical robustness. resourceId and className use exact match.
     */
    private fun matchesSelector(
        node: AccessibilityNodeInfo,
        selector: ElementSelector,
    ): Boolean {
        selector.text?.let { expected ->
            val actual = node.text?.toString() ?: return false
            if (!actual.contains(expected, ignoreCase = true)) return false
        }

        selector.contentDescription?.let { expected ->
            val actual = node.contentDescription?.toString() ?: return false
            if (!actual.contains(expected, ignoreCase = true)) return false
        }

        selector.className?.let { expected ->
            val actual = node.className?.toString() ?: return false
            if (actual != expected) return false
        }

        selector.resourceId?.let { expected ->
            val actual = node.viewIdResourceName ?: return false
            if (actual != expected) return false
        }

        // If every non-null selector field matched, and at least one was set,
        // we consider it a match. An empty selector matches nothing.
        return selector.text != null ||
            selector.contentDescription != null ||
            selector.className != null ||
            selector.resourceId != null
    }
}
