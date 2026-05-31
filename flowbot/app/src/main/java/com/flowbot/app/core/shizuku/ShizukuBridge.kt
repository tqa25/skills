package com.flowbot.app.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable state of the Shizuku connection.
 *
 * UI layers observe [ShizukuBridge.connectionState] and react accordingly
 * (e.g. showing a "grant permission" button when [PermissionDenied]).
 */
sealed class ShizukuConnectionState {
    /** Shizuku server is not running or we haven't bound yet. */
    data object Disconnected : ShizukuConnectionState()

    /** We detected the Shizuku server; waiting for binder. */
    data object Connecting : ShizukuConnectionState()

    /** Binder is alive and permission has been granted. */
    data object Connected : ShizukuConnectionState()

    /** User explicitly denied the Shizuku permission dialog. */
    data object PermissionDenied : ShizukuConnectionState()

    /** Unexpected error (message is for logging, not direct user display). */
    data class Error(val message: String) : ShizukuConnectionState()
}

/**
 * Singleton bridge between the app and Shizuku.
 *
 * Call [init] once from `Application.onCreate()` and [destroy] in `onTerminate()`
 * (or when the last relevant component is torn down).
 */
@Singleton
class ShizukuBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "ShizukuBridge"
        private const val PERMISSION_REQUEST_CODE = 42_001
    }

    private val _connectionState =
        MutableStateFlow<ShizukuConnectionState>(ShizukuConnectionState.Disconnected)
    val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

    // ── Shizuku callbacks ───────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Binder received")
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Binder dead")
        _connectionState.value = ShizukuConnectionState.Disconnected
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted")
                _connectionState.value = ShizukuConnectionState.Connected
            } else {
                Log.w(TAG, "Permission denied")
                _connectionState.value = ShizukuConnectionState.PermissionDenied
            }
        }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Register Shizuku lifecycle listeners.
     *
     * Safe to call more than once — subsequent calls are no-ops because we
     * unregister before registering (Shizuku silently ignores duplicate removes).
     */
    fun init() {
        try {
            // Ensure no duplicates.
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }

            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)

            _connectionState.value = ShizukuConnectionState.Connecting

            // If Shizuku is already alive (sticky), we'll get the callback
            // immediately inside `addBinderReceivedListenerSticky`.
            if (Shizuku.pingBinder()) {
                checkPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            _connectionState.value =
                ShizukuConnectionState.Error(e.message ?: "Unknown init error")
        }
    }

    /** Show the Shizuku permission dialog. No-op if already granted. */
    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                _connectionState.value = ShizukuConnectionState.Disconnected
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _connectionState.value = ShizukuConnectionState.Connected
                return
            }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed", e)
            _connectionState.value =
                ShizukuConnectionState.Error(e.message ?: "Permission request error")
        }
    }

    /** Quick connectivity + permission check. */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /** Unregister all listeners. Call when the app is shutting down. */
    fun destroy() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
        _connectionState.value = ShizukuConnectionState.Disconnected
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun checkPermission() {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _connectionState.value = ShizukuConnectionState.Connected
            } else {
                _connectionState.value = ShizukuConnectionState.PermissionDenied
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPermission failed", e)
            _connectionState.value =
                ShizukuConnectionState.Error(e.message ?: "Permission check error")
        }
    }
}
