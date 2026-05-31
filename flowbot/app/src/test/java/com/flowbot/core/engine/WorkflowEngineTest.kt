package com.flowbot.core.engine

import com.flowbot.core.model.ErrorPolicy
import com.flowbot.core.model.Step
import com.flowbot.core.model.Workflow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowEngineTest {

    private class MockGestureExecutor(var success: Boolean = true) : GestureExecutor {
        val executedActions = mutableListOf<String>()
        var executionCount = 0

        override suspend fun execute(action: String, params: JsonObject): Result<Unit> {
            executedActions.add(action)
            executionCount++
            return if (success) Result.success(Unit) else Result.failure(Exception("Failed"))
        }
    }

    private class MockElementDetector(var success: Boolean = true) : ElementDetector {
        override suspend fun find(params: JsonObject): Result<Unit> {
            return if (success) Result.success(Unit) else Result.failure(Exception("Failed"))
        }
    }

    private class MockExecutionLogCallback : ExecutionLogCallback {
        val startedSteps = mutableListOf<String>()
        val completedSteps = mutableListOf<StepResult>()

        override suspend fun onStepStarted(stepId: String) {
            startedSteps.add(stepId)
        }

        override suspend fun onStepCompleted(result: StepResult) {
            completedSteps.add(result)
        }
    }

    @Test
    fun `execute sequential steps successfully`() = runTest {
        val executor = MockGestureExecutor()
        val detector = MockElementDetector()
        val callback = MockExecutionLogCallback()
        val engine = WorkflowEngine(executor, detector, callback)

        val workflow = Workflow(
            name = "Test",
            steps = listOf(
                Step(id = "1", action = "tap"),
                Step(id = "2", action = "swipe")
            )
        )

        val result = engine.execute(workflow)

        assertTrue(result)
        assertEquals(listOf("tap", "swipe"), executor.executedActions)
        assertEquals(listOf("1", "2"), callback.startedSteps)
        assertEquals(2, callback.completedSteps.size)
        assertTrue(callback.completedSteps.all { it.success })
    }

    @Test
    fun `stop on error policy`() = runTest {
        val executor = MockGestureExecutor(success = false)
        val detector = MockElementDetector()
        val callback = MockExecutionLogCallback()
        val engine = WorkflowEngine(executor, detector, callback)

        val workflow = Workflow(
            name = "Test",
            steps = listOf(
                Step(id = "1", action = "tap", onError = ErrorPolicy.STOP),
                Step(id = "2", action = "swipe")
            )
        )

        val result = engine.execute(workflow)

        assertFalse(result)
        assertEquals(listOf("tap"), executor.executedActions)
        assertEquals(listOf("1"), callback.startedSteps)
        assertEquals(1, callback.completedSteps.size)
        assertFalse(callback.completedSteps[0].success)
    }

    @Test
    fun `skip on error policy`() = runTest {
        val executor = MockGestureExecutor(success = false)
        val detector = MockElementDetector()
        val callback = MockExecutionLogCallback()
        val engine = WorkflowEngine(executor, detector, callback)

        val workflow = Workflow(
            name = "Test",
            steps = listOf(
                Step(id = "1", action = "tap", onError = ErrorPolicy.SKIP),
                Step(id = "2", action = "swipe")
            )
        )

        val result = engine.execute(workflow)

        assertTrue(result)
        assertEquals(listOf("tap", "swipe"), executor.executedActions)
        assertEquals(listOf("1", "2"), callback.startedSteps)
        assertEquals(2, callback.completedSteps.size)
        assertFalse(callback.completedSteps[0].success)
        assertFalse(callback.completedSteps[1].success)
    }

    @Test
    fun `retry on error policy`() = runTest {
        val executor = MockGestureExecutor(success = false)
        val detector = MockElementDetector()
        val callback = MockExecutionLogCallback()
        val engine = WorkflowEngine(executor, detector, callback)

        val workflow = Workflow(
            name = "Test",
            steps = listOf(
                Step(id = "1", action = "tap", onError = ErrorPolicy.RETRY)
            )
        )

        val result = engine.execute(workflow)

        assertFalse(result)
        assertEquals(3, executor.executionCount) // Retried 3 times
        assertEquals(listOf("1"), callback.startedSteps)
        assertEquals(1, callback.completedSteps.size)
        assertFalse(callback.completedSteps[0].success)
    }

    @Test
    fun `loop execution`() = runTest {
        val executor = MockGestureExecutor()
        val detector = MockElementDetector()
        val callback = MockExecutionLogCallback()
        val engine = WorkflowEngine(executor, detector, callback)

        val workflow = Workflow(
            name = "Test",
            steps = listOf(
                Step(
                    id = "loop_1",
                    action = "loop",
                    params = JsonObject(mapOf("count" to JsonPrimitive(2))),
                    steps = listOf(
                        Step(id = "sub_1", action = "tap")
                    )
                )
            )
        )

        val result = engine.execute(workflow)

        assertTrue(result)
        assertEquals(listOf("tap", "tap"), executor.executedActions)
        assertEquals(listOf("loop_1", "sub_1", "sub_1"), callback.startedSteps)
    }
}
