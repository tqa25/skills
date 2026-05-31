package com.flowbot.app.core.gesture

import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import com.flowbot.app.data.model.SwipeDirection
import kotlinx.coroutines.delay
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Gesture executor that injects touch/key events through the system InputManager
 * via Shizuku's binder access.
 *
 * Phase 1 implements: tap, longPress, swipe, swipeDirection, pressHome/Back/Recent,
 * pressKey, and typeText.
 * Other methods return [Result.failure] with a "Not implemented" message.
 */
class ShizukuGestureExecutor : GestureExecutor {

    companion object {
        private const val TAG = "ShizukuGesture"

        /** InputManager.injectInputEvent inject mode: wait for finish. */
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2

        /** Default display for touch events. */
        private const val DEFAULT_DISPLAY_ID = 0

        /** Number of intermediate move events for a smooth swipe. */
        private const val SWIPE_MOVE_STEPS = 10
    }

    // ── InputManager binder access ──────────────────────────────────────

    /**
     * Lazily resolved IInputManager binder stub.
     *
     * We use [SystemServiceHelper] to get the raw binder for "input", wrap it
     * with [ShizukuBinderWrapper], and then create a proxy via the hidden
     * IInputManager.Stub.asInterface.
     */
    private val inputManager: Any by lazy { resolveInputManager() }

    private val injectMethod by lazy {
        inputManager.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
    }

    private fun resolveInputManager(): Any {
        val binder = SystemServiceHelper.getSystemService("input")
        val wrapped = ShizukuBinderWrapper(binder)
        // IInputManager.Stub.asInterface(IBinder)
        val clazz = Class.forName("android.hardware.input.IInputManager\$Stub")
        val asInterface = clazz.getMethod("asInterface", android.os.IBinder::class.java)
        return asInterface.invoke(null, wrapped)!!
    }

    private fun injectEvent(event: InputEvent): Boolean {
        return try {
            injectMethod.invoke(
                inputManager,
                event,
                INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH,
            ) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "injectEvent failed", e)
            false
        }
    }

    // ── Touch helpers ───────────────────────────────────────────────────

    private fun createTouchEvent(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1, // pointerCount
            properties,
            coords,
            0, // metaState
            0, // buttonState
            1f, // xPrecision
            1f, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_TOUCHSCREEN,
            0, // flags
        ).also {
            it.setSource(InputDevice.SOURCE_TOUCHSCREEN)
        }
    }

    private fun injectKeyEvent(keyCode: Int): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(
            downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0,
            0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            InputDevice.SOURCE_KEYBOARD,
        )
        val up = KeyEvent(
            downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0,
            0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            InputDevice.SOURCE_KEYBOARD,
        )
        return injectEvent(down) && injectEvent(up)
    }

    // ── GestureExecutor implementation ──────────────────────────────────

    override suspend fun tap(x: Int, y: Int): Result<Unit> = runCatching {
        val downTime = SystemClock.uptimeMillis()
        val down = createTouchEvent(
            MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), downTime, downTime,
        )
        val up = createTouchEvent(
            MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), downTime,
            SystemClock.uptimeMillis(),
        )
        check(injectEvent(down)) { "Failed to inject ACTION_DOWN" }
        check(injectEvent(up)) { "Failed to inject ACTION_UP" }
        down.recycle()
        up.recycle()
    }

    override suspend fun longPress(x: Int, y: Int, durationMs: Long): Result<Unit> = runCatching {
        val downTime = SystemClock.uptimeMillis()
        val down = createTouchEvent(
            MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), downTime, downTime,
        )
        check(injectEvent(down)) { "Failed to inject ACTION_DOWN" }
        down.recycle()

        delay(durationMs)

        val up = createTouchEvent(
            MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), downTime,
            SystemClock.uptimeMillis(),
        )
        check(injectEvent(up)) { "Failed to inject ACTION_UP" }
        up.recycle()
    }

    override suspend fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Long,
    ): Result<Unit> = runCatching {
        val downTime = SystemClock.uptimeMillis()
        val stepDelay = durationMs / SWIPE_MOVE_STEPS

        // DOWN at start position
        val down = createTouchEvent(
            MotionEvent.ACTION_DOWN, fromX.toFloat(), fromY.toFloat(), downTime, downTime,
        )
        check(injectEvent(down)) { "Swipe: failed ACTION_DOWN" }
        down.recycle()

        // Intermediate MOVE events
        for (i in 1..SWIPE_MOVE_STEPS) {
            val fraction = i.toFloat() / SWIPE_MOVE_STEPS
            val curX = fromX + (toX - fromX) * fraction
            val curY = fromY + (toY - fromY) * fraction

            delay(stepDelay)

            val move = createTouchEvent(
                MotionEvent.ACTION_MOVE, curX, curY, downTime, SystemClock.uptimeMillis(),
            )
            check(injectEvent(move)) { "Swipe: failed ACTION_MOVE at step $i" }
            move.recycle()
        }

        // UP at end position
        val up = createTouchEvent(
            MotionEvent.ACTION_UP, toX.toFloat(), toY.toFloat(), downTime,
            SystemClock.uptimeMillis(),
        )
        check(injectEvent(up)) { "Swipe: failed ACTION_UP" }
        up.recycle()
    }

    override suspend fun swipeDirection(
        direction: SwipeDirection,
        distance: Int,
        durationMs: Long,
    ): Result<Unit> {
        // Use a fixed center point — real implementation would query display metrics.
        val centerX = 540
        val centerY = 1200
        val half = distance / 2

        val (fromX, fromY, toX, toY) = when (direction) {
            SwipeDirection.UP -> listOf(centerX, centerY + half, centerX, centerY - half)
            SwipeDirection.DOWN -> listOf(centerX, centerY - half, centerX, centerY + half)
            SwipeDirection.LEFT -> listOf(centerX + half, centerY, centerX - half, centerY)
            SwipeDirection.RIGHT -> listOf(centerX - half, centerY, centerX + half, centerY)
        }
        return swipe(fromX, fromY, toX, toY, durationMs)
    }

    override suspend fun pressHome(): Result<Unit> = runCatching {
        check(injectKeyEvent(KeyEvent.KEYCODE_HOME)) { "Failed to inject HOME" }
    }

    override suspend fun pressBack(): Result<Unit> = runCatching {
        check(injectKeyEvent(KeyEvent.KEYCODE_BACK)) { "Failed to inject BACK" }
    }

    override suspend fun pressRecent(): Result<Unit> = runCatching {
        check(injectKeyEvent(KeyEvent.KEYCODE_APP_SWITCH)) { "Failed to inject RECENTS" }
    }

    override suspend fun pressKey(keyCode: Int): Result<Unit> = runCatching {
        check(injectKeyEvent(keyCode)) { "Failed to inject keyCode=$keyCode" }
    }

    override suspend fun typeText(text: String): Result<Unit> = runCatching {
        val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = kcm.getEvents(text.toCharArray())
            ?: error("Cannot map text to key events: \"$text\"")
        for (event in events) {
            check(injectEvent(event)) { "Failed to inject key event for typeText" }
        }
    }

    // ── Phase 2 stubs ───────────────────────────────────────────────────

    override suspend fun pinch(
        centerX: Int,
        centerY: Int,
        scale: Float,
        durationMs: Long,
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("pinch: Not implemented in Phase 1"))

    override suspend fun drag(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Long,
    ): Result<Unit> =
        Result.failure(UnsupportedOperationException("drag: Not implemented in Phase 1"))

    override suspend fun readClipboard(): Result<String> =
        Result.failure(UnsupportedOperationException("readClipboard: Not implemented in Phase 1"))

    override suspend fun setClipboard(text: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("setClipboard: Not implemented in Phase 1"))

    override suspend fun openApp(packageName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("openApp: Not implemented in Phase 1"))

    override suspend fun takeScreenshot(): Result<Bitmap> =
        Result.failure(UnsupportedOperationException("takeScreenshot: Not implemented in Phase 1"))
}
