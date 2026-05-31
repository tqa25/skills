package com.flowbot.app.core.gesture

import android.graphics.Bitmap
import com.flowbot.app.data.model.SwipeDirection

/**
 * Abstraction over system-level gesture injection.
 *
 * Every method returns [Result] so callers can distinguish transient failures
 * (Shizuku disconnected, permission revoked) from programming errors.
 */
interface GestureExecutor {

    /** Inject a single tap at the given screen coordinates. */
    suspend fun tap(x: Int, y: Int): Result<Unit>

    /** Inject a long-press at the given screen coordinates. */
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 1000): Result<Unit>

    /** Inject a swipe gesture between two points. */
    suspend fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Long = 300,
    ): Result<Unit>

    /** Convenience: swipe in a cardinal direction from screen center. */
    suspend fun swipeDirection(
        direction: SwipeDirection,
        distance: Int = 500,
        durationMs: Long = 300,
    ): Result<Unit>

    /** Pinch (zoom) gesture around a center point. [scale] < 1 = pinch-in, > 1 = pinch-out. */
    suspend fun pinch(
        centerX: Int,
        centerY: Int,
        scale: Float,
        durationMs: Long = 500,
    ): Result<Unit>

    /** Drag from one point to another (longer hold before move than swipe). */
    suspend fun drag(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Long = 500,
    ): Result<Unit>

    /** Type arbitrary text via InputManager. */
    suspend fun typeText(text: String): Result<Unit>

    /** Send a single hardware key code (see [android.view.KeyEvent]). */
    suspend fun pressKey(keyCode: Int): Result<Unit>

    /** Press the HOME button. */
    suspend fun pressHome(): Result<Unit>

    /** Press the BACK button. */
    suspend fun pressBack(): Result<Unit>

    /** Press the RECENTS / app-switcher button. */
    suspend fun pressRecent(): Result<Unit>

    /** Read the current system clipboard text. */
    suspend fun readClipboard(): Result<String>

    /** Set the system clipboard to [text]. */
    suspend fun setClipboard(text: String): Result<Unit>

    /** Launch an app by package name via an Intent. */
    suspend fun openApp(packageName: String): Result<Unit>

    /** Capture the current screen as a [Bitmap]. */
    suspend fun takeScreenshot(): Result<Bitmap>
}
