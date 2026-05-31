package com.flowbot.app.core.engine

import android.graphics.Rect
import com.flowbot.app.core.detection.DetectedElement
import com.flowbot.app.core.detection.ElementDetector
import com.flowbot.app.core.gesture.GestureExecutor
import com.flowbot.app.data.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import android.graphics.Bitmap

class WorkflowEngineTest {

    private lateinit engine: WorkflowEngine
    private lateinit mockGestureExecutor: MockGestureExecutor
    private lateinit mockElementDetector: MockElementDetector
    private lateinit mockLogger: MockLogger

    @Before
    fun setup() {
        mockGestureExecutor = MockGestureExecutor()
        mockElementDetector = MockElementDetector()
        mockLogger = MockLogger()
        engine = WorkflowEngine(mockGestureExecutor, mockElementDetector, mockLogger)
    }

    @Test
    fun `test simple tap workflow succeeds`() = runTest {
        val workflow = Workflow(
            name = "Test",
            steps = listOf(
                WorkflowStep(
                    id = "step1",
                    action = StepAction.TAP,
                    params = StepParams(x = 100, y = 200),
                    delayAfterMs = 0
                )
            )
        )

        val result = engine.execute(workflow, "run1")

        assertTrue(result.success)
        assertEquals(1, result.stepsExecuted)
        assertEquals(0, result.stepsFailed)
        
        assertEquals(1, mockGestureExecutor.taps.size)
        assertEquals(Pair(100, 200), mockGestureExecutor.taps[0])
    }
}

class MockGestureExecutor : GestureExecutor {
    val taps = mutableListOf<Pair<Int, Int>>()
    
    override suspend fun tap(x: Int, y: Int): Result<Unit> {
        taps.add(Pair(x, y))
        return Result.success(Unit)
    }

    override suspend fun longPress(x: Int, y: Int, durationMs: Long): Result<Unit> = Result.success(Unit)
    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Result<Unit> = Result.success(Unit)
    override suspend fun swipeDirection(direction: SwipeDirection, distance: Int, durationMs: Long): Result<Unit> = Result.success(Unit)
    override suspend fun pinch(centerX: Int, centerY: Int, scale: Float, durationMs: Long): Result<Unit> = Result.success(Unit)
    override suspend fun drag(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Result<Unit> = Result.success(Unit)
    override suspend fun typeText(text: String): Result<Unit> = Result.success(Unit)
    override suspend fun pressKey(keyCode: Int): Result<Unit> = Result.success(Unit)
    override suspend fun pressHome(): Result<Unit> = Result.success(Unit)
    override suspend fun pressBack(): Result<Unit> = Result.success(Unit)
    override suspend fun pressRecent(): Result<Unit> = Result.success(Unit)
    override suspend fun readClipboard(): Result<String> = Result.success("mock_clipboard")
    override suspend fun setClipboard(text: String): Result<Unit> = Result.success(Unit)
    override suspend fun openApp(packageName: String): Result<Unit> = Result.success(Unit)
    override suspend fun takeScreenshot(): Result<Bitmap> = Result.failure(Exception("mock"))
}

class MockElementDetector : ElementDetector {
    override suspend fun findElement(selector: ElementSelector, timeoutMs: Long): Result<DetectedElement> {
        return Result.success(DetectedElement(Rect(0,0,100,100), "mock", null, null))
    }

    override suspend fun findAllElements(selector: ElementSelector): Result<List<DetectedElement>> {
        return Result.success(emptyList())
    }

    override fun isServiceRunning(): Boolean = true
}

class MockLogger : WorkflowEngine.ExecutionLogCallback {
    override suspend fun onStepStarted(stepId: String, action: StepAction) {}
    override suspend fun onStepCompleted(stepId: String, action: StepAction, durationMs: Long) {}
    override suspend fun onStepFailed(stepId: String, action: StepAction, error: String, durationMs: Long) {}
    override suspend fun onStepSkipped(stepId: String, action: StepAction) {}
}
