package com.flowbot.app.data.logging

import com.flowbot.app.core.engine.WorkflowEngine
import com.flowbot.app.data.model.ExecutionLog
import com.flowbot.app.data.model.StepAction
import com.flowbot.app.data.model.StepStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists every workflow-engine callback as an [ExecutionLog] row in Room.
 *
 * Inject this into anything that needs to start logging or query past runs.
 * Pass it as the [WorkflowEngine.ExecutionLogCallback] when you create an engine
 * instance.
 */
@Singleton
class ExecutionLogger @Inject constructor(
    private val dao: ExecutionLogDao,
) : WorkflowEngine.ExecutionLogCallback {

    // ── WorkflowEngine.ExecutionLogCallback implementation ──────────────

    override suspend fun onStepStarted(
        workflowName: String,
        runId: String,
        stepId: String,
        action: StepAction,
    ) {
        dao.insert(
            ExecutionLog(
                workflowName = workflowName,
                runId = runId,
                stepId = stepId,
                action = action.name,
                status = StepStatus.SUCCESS, // placeholder — updated on completion
                durationMs = 0,
            ),
        )
    }

    override suspend fun onStepCompleted(
        workflowName: String,
        runId: String,
        stepId: String,
        action: StepAction,
        durationMs: Long,
    ) {
        dao.insert(
            ExecutionLog(
                workflowName = workflowName,
                runId = runId,
                stepId = stepId,
                action = action.name,
                status = StepStatus.SUCCESS,
                durationMs = durationMs,
            ),
        )
    }

    override suspend fun onStepFailed(
        workflowName: String,
        runId: String,
        stepId: String,
        action: StepAction,
        error: String,
        durationMs: Long,
    ) {
        dao.insert(
            ExecutionLog(
                workflowName = workflowName,
                runId = runId,
                stepId = stepId,
                action = action.name,
                status = StepStatus.FAILED,
                durationMs = durationMs,
                errorMessage = error,
            ),
        )
    }

    override suspend fun onStepSkipped(
        workflowName: String,
        runId: String,
        stepId: String,
        action: StepAction,
    ) {
        dao.insert(
            ExecutionLog(
                workflowName = workflowName,
                runId = runId,
                stepId = stepId,
                action = action.name,
                status = StepStatus.SKIPPED,
                durationMs = 0,
            ),
        )
    }

    // ── Query helpers (delegated straight to DAO) ───────────────────────

    suspend fun getLogsByRunId(runId: String): List<ExecutionLog> =
        dao.getLogsByRunId(runId)

    suspend fun getLogsByWorkflow(name: String): List<ExecutionLog> =
        dao.getLogsByWorkflow(name)

    suspend fun getRunIds(workflowName: String): List<String> =
        dao.getRunIds(workflowName)

    suspend fun deleteRun(runId: String) =
        dao.deleteByRunId(runId)

    suspend fun getRecentLogs(limit: Int = 100): List<ExecutionLog> =
        dao.getRecentLogs(limit)
}
