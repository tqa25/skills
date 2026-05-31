package com.flowbot.app.core.engine

import com.flowbot.app.core.detection.ElementDetector
import com.flowbot.app.core.gesture.GestureExecutor
import com.flowbot.app.data.model.*
import kotlinx.coroutines.delay

class WorkflowEngine(
    private val gestureExecutor: GestureExecutor,
    private val elementDetector: ElementDetector,
    private val logger: ExecutionLogCallback,
) {
    interface ExecutionLogCallback {
        suspend fun onStepStarted(workflowName: String, runId: String, stepId: String, action: StepAction)
        suspend fun onStepCompleted(workflowName: String, runId: String, stepId: String, action: StepAction, durationMs: Long)
        suspend fun onStepFailed(workflowName: String, runId: String, stepId: String, action: StepAction, error: String, durationMs: Long)
        suspend fun onStepSkipped(workflowName: String, runId: String, stepId: String, action: StepAction)
    }

    suspend fun execute(workflow: Workflow, runId: String): WorkflowResult {
        var stepsExecuted = 0
        var stepsFailed = 0
        var stepsSkipped = 0
        val startTime = System.currentTimeMillis()
        val variables = workflow.variables.toMutableMap()

        for (step in workflow.steps) {
            val result = executeStep(step, variables, workflow.name, runId)
            when (result.status) {
                StepStatus.SUCCESS -> stepsExecuted++
                StepStatus.FAILED -> {
                    stepsFailed++
                    if (step.onError == ErrorPolicy.STOP) {
                        return WorkflowResult.Failure(
                            error = result.errorMessage ?: "Step ${step.id} failed",
                            stepsExecuted = stepsExecuted,
                            stepsFailed = stepsFailed,
                            stepsSkipped = stepsSkipped,
                            totalDurationMs = System.currentTimeMillis() - startTime,
                        )
                    }
                }
                StepStatus.SKIPPED -> stepsSkipped++
            }
        }

        return WorkflowResult.Success(
            stepsExecuted = stepsExecuted,
            stepsFailed = stepsFailed,
            stepsSkipped = stepsSkipped,
            totalDurationMs = System.currentTimeMillis() - startTime,
        )
    }

    private suspend fun executeStep(
        step: WorkflowStep,
        variables: MutableMap<String, String>,
        workflowName: String,
        runId: String,
    ): StepResult {
        var retries = 0
        val maxRetries = if (step.onError == ErrorPolicy.RETRY) 3 else 0
        var lastError: String? = null

        while (retries <= maxRetries) {
            val startTime = System.currentTimeMillis()
            logger.onStepStarted(workflowName, runId, step.id, step.action)

            try {
                val result = if (step.action == StepAction.LOOP) {
                    executeLoop(step, variables, workflowName, runId)
                } else {
                    executeGesture(step, variables)
                }

                if (result.status == StepStatus.SUCCESS) {
                    delay(step.delayAfterMs)
                    logger.onStepCompleted(workflowName, runId, step.id, step.action, System.currentTimeMillis() - startTime)
                    return result
                } else {
                    lastError = result.errorMessage
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
            }

            if (step.onError == ErrorPolicy.RETRY && retries < maxRetries) {
                retries++
                delay(1000L) // basic backoff
            } else {
                break
            }
        }

        return if (step.onError == ErrorPolicy.SKIP) {
            logger.onStepSkipped(workflowName, runId, step.id, step.action)
            StepResult(StepStatus.SKIPPED, errorMessage = lastError)
        } else {
            logger.onStepFailed(workflowName, runId, step.id, step.action, lastError ?: "Failed", 0)
            StepResult(StepStatus.FAILED, errorMessage = lastError)
        }
    }

    private suspend fun executeLoop(
        step: WorkflowStep,
        variables: MutableMap<String, String>,
        workflowName: String,
        runId: String,
    ): StepResult {
        val count = step.params.count
            ?: return StepResult(StepStatus.FAILED, errorMessage = "Loop missing count")
        val steps = step.steps
            ?: return StepResult(StepStatus.FAILED, errorMessage = "Loop missing steps")

        for (i in 0 until count) {
            variables["loop_index"] = i.toString()
            for (innerStep in steps) {
                val result = executeStep(innerStep, variables, workflowName, runId)
                if (result.status == StepStatus.FAILED && innerStep.onError == ErrorPolicy.STOP) {
                    return result
                }
            }
        }
        return StepResult(StepStatus.SUCCESS)
    }

    private suspend fun executeGesture(
        step: WorkflowStep,
        variables: MutableMap<String, String>,
    ): StepResult {
        // Resolve variables in string params
        val text = resolveVariable(step.params.text, variables)

        // Find element if selector exists
        var x = step.params.x
        var y = step.params.y

        if (step.selector != null) {
            val elementResult = elementDetector.findElement(step.selector)
            if (elementResult.isSuccess) {
                val element = elementResult.getOrNull()
                x = element?.centerX
                y = element?.centerY
            } else {
                return StepResult(StepStatus.FAILED, errorMessage = "Element not found: ${step.selector}")
            }
        }

        val result = when (step.action) {
            StepAction.TAP -> gestureExecutor.tap(x ?: 0, y ?: 0)
            StepAction.LONG_PRESS -> gestureExecutor.longPress(x ?: 0, y ?: 0, step.params.duration ?: 1000L)
            StepAction.SWIPE -> {
                if (step.params.direction != null) {
                    gestureExecutor.swipeDirection(step.params.direction, step.params.distance ?: 500)
                } else {
                    gestureExecutor.swipe(
                        x ?: 0, y ?: 0,
                        step.params.toX ?: 0, step.params.toY ?: 0,
                        step.params.duration ?: 300L,
                    )
                }
            }
            StepAction.PRESS_HOME -> gestureExecutor.pressHome()
            StepAction.PRESS_BACK -> gestureExecutor.pressBack()
            StepAction.PRESS_RECENT -> gestureExecutor.pressRecent()
            StepAction.PRESS_KEY -> gestureExecutor.pressKey(step.params.key?.toIntOrNull() ?: 0)
            StepAction.TYPE_TEXT -> gestureExecutor.typeText(text ?: "")
            StepAction.READ_CLIPBOARD -> {
                val clipRes = gestureExecutor.readClipboard()
                if (clipRes.isSuccess && step.output != null) {
                    variables[step.output] = clipRes.getOrNull() ?: ""
                }
                clipRes.map { Unit }
            }
            StepAction.OPEN_APP -> gestureExecutor.openApp(step.params.appPackage ?: "")
            else -> Result.success(Unit) // DELAY, WAIT_FOR_ELEMENT, SCREENSHOT, etc. – stubs
        }

        return if (result.isSuccess) {
            StepResult(StepStatus.SUCCESS)
        } else {
            StepResult(StepStatus.FAILED, errorMessage = result.exceptionOrNull()?.message)
        }
    }

    private fun resolveVariable(value: String?, variables: Map<String, String>): String? {
        if (value == null) return null
        if (value.startsWith("$")) {
            val key = value.substring(1)
            return variables[key] ?: value
        }
        return value
    }
}

/** Result of running a full [Workflow]. */
sealed class WorkflowResult {
    data class Success(
        val stepsExecuted: Int,
        val stepsFailed: Int,
        val stepsSkipped: Int,
        val totalDurationMs: Long,
    ) : WorkflowResult()

    data class Failure(
        val error: String,
        val stepsExecuted: Int,
        val stepsFailed: Int,
        val stepsSkipped: Int,
        val totalDurationMs: Long,
    ) : WorkflowResult()
}

data class StepResult(
    val status: StepStatus,
    val output: String? = null,
    val errorMessage: String? = null,
)
