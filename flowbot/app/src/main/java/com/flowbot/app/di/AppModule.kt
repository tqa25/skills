package com.flowbot.app.di

import android.content.Context
import android.graphics.Rect
import androidx.room.Room
import com.flowbot.app.core.detection.DetectedElement
import com.flowbot.app.core.detection.ElementDetector
import com.flowbot.app.core.detection.FlowBotAccessibilityService
import com.flowbot.app.core.engine.WorkflowEngine
import com.flowbot.app.core.gesture.GestureExecutor
import com.flowbot.app.core.gesture.ShizukuGestureExecutor
import com.flowbot.app.data.logging.ExecutionLogDao
import com.flowbot.app.data.logging.ExecutionLogger
import com.flowbot.app.data.model.ElementSelector
import com.flowbot.app.data.repository.AppDatabase
import com.flowbot.app.data.repository.WorkflowMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "flowbot_db",
        ).build()
    }

    @Provides
    fun provideExecutionLogDao(db: AppDatabase): ExecutionLogDao = db.executionLogDao()

    @Provides
    fun provideWorkflowMetadataDao(db: AppDatabase): WorkflowMetadataDao = db.workflowMetadataDao()

    @Provides
    @Singleton
    fun provideGestureExecutor(): GestureExecutor = ShizukuGestureExecutor()

    @Provides
    @Singleton
    fun provideElementDetector(): ElementDetector {
        return object : ElementDetector {
            override suspend fun findElement(
                selector: ElementSelector,
                timeoutMs: Long,
            ): Result<DetectedElement> {
                // Delegates to the live AccessibilityService if running.
                val service = FlowBotAccessibilityService.instance
                    ?: return Result.failure(IllegalStateException("Accessibility service not running"))
                return service.findElement(selector, timeoutMs)
            }

            override suspend fun findAllElements(
                selector: ElementSelector,
            ): Result<List<DetectedElement>> {
                val service = FlowBotAccessibilityService.instance
                    ?: return Result.failure(IllegalStateException("Accessibility service not running"))
                return service.findAllElements(selector)
            }

            override fun isServiceRunning(): Boolean =
                FlowBotAccessibilityService.instance != null
        }
    }

    @Provides
    @Singleton
    fun provideWorkflowEngine(
        gestureExecutor: GestureExecutor,
        elementDetector: ElementDetector,
        executionLogger: ExecutionLogger,
    ): WorkflowEngine = WorkflowEngine(gestureExecutor, elementDetector, executionLogger)
}
