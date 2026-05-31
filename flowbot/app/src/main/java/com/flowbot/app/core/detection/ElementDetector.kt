package com.flowbot.app.core.detection

import android.graphics.Rect
import com.flowbot.app.data.model.ElementSelector

/**
 * Abstraction over UI element detection.
 *
 * The primary implementation ([FlowBotAccessibilityService]) walks the accessibility
 * node tree, but this interface allows swapping in OCR or screenshot-based detection
 * in the future.
 */
interface ElementDetector {

    /**
     * Find a single element matching [selector], polling up to [timeoutMs].
     *
     * Returns [Result.failure] if no element is found within the timeout.
     */
    suspend fun findElement(
        selector: ElementSelector,
        timeoutMs: Long = 5000,
    ): Result<DetectedElement>

    /**
     * Return *all* elements matching [selector] in a single pass (no polling).
     */
    suspend fun findAllElements(selector: ElementSelector): Result<List<DetectedElement>>

    /** Whether the backing service (e.g. AccessibilityService) is currently running. */
    fun isServiceRunning(): Boolean
}

/**
 * A UI element that was found on screen.
 *
 * Coordinates are in absolute screen pixels, matching what [GestureExecutor] expects.
 */
data class DetectedElement(
    val bounds: Rect,
    val text: String?,
    val className: String?,
    val contentDescription: String?,
) {
    /** Horizontal center of the element's bounding box. */
    val centerX: Int get() = bounds.centerX()

    /** Vertical center of the element's bounding box. */
    val centerY: Int get() = bounds.centerY()
}
