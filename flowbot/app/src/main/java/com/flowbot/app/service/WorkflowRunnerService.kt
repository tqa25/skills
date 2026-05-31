package com.flowbot.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.flowbot.app.MainActivity
import com.flowbot.app.core.engine.WorkflowEngine
import com.flowbot.app.core.engine.WorkflowResult
import com.flowbot.app.data.repository.WorkflowRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkflowRunnerService : Service() {

    companion object {
        const val EXTRA_FILE_NAME = "extra_file_name"
        private const val TAG = "WorkflowRunner"
        private const val CHANNEL_ID = "workflow_runner"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "FlowBot::WorkflowRunner"
    }

    @Inject lateinit var workflowEngine: WorkflowEngine
    @Inject lateinit var workflowRepository: WorkflowRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME)
        if (fileName == null) {
            Log.w(TAG, "No fileName provided, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Preparing…"))
        acquireWakeLock()

        serviceScope.launch {
            runWorkflow(fileName)
            releaseWakeLock()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    // ── Core execution ──────────────────────────────────────────────────────────

    private suspend fun runWorkflow(fileName: String) {
        try {
            val workflow = workflowRepository.loadWorkflow(fileName)
            updateNotification("Running: ${workflow.name}")

            val result = workflowEngine.execute(workflow) { stepIndex, stepId, message ->
                updateNotification("Step ${stepIndex + 1}/${workflow.steps.size}: $stepId")
            }

            when (result) {
                is WorkflowResult.Success -> {
                    updateNotification("✓ ${workflow.name} completed")
                }
                is WorkflowResult.Failure -> {
                    updateNotification("✗ ${workflow.name} failed: ${result.error}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed", e)
            updateNotification("✗ Error: ${e.message}")
        }
    }

    // ── Notification helpers ────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workflow Runner",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress while running workflows"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FlowBot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Wake lock ───────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG,
        ).apply {
            acquire(30 * 60 * 1000L) // 30-minute timeout safety net
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
