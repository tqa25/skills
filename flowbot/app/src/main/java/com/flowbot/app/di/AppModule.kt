package com.flowbot.app.di

import android.content.Context
import androidx.room.Room
import com.flowbot.app.core.detection.ElementDetector
import com.flowbot.app.core.detection.FlowBotAccessibilityService
import com.flowbot.app.core.gesture.GestureExecutor
import com.flowbot.app.core.gesture.ShizukuGestureExecutor
import com.flowbot.app.core.shizuku.ShizukuBridge
import com.flowbot.app.data.logging.ExecutionLogDao
import com.flowbot.app.data.repository.AppDatabase
import com.flowbot.app.data.repository.WorkflowMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.graphics.Rect
import com.flowbot.app.core.detection.DetectedElement
import com.flowbot.app.data.model.ElementSelector

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "flowbot_db"
        ).build()
    }
    
    @Provides
    fun provideExecutionLogDao(db: AppDatabase): ExecutionLogDao {
        return db.executionLogDao()
    }
    
    @Provides
    fun provideWorkflowMetadataDao(db: AppDatabase): WorkflowMetadataDao {
        return db.workflowMetadataDao()
    }
    
    @Provides
    @Singleton
    fun provideGestureExecutor(bridge: ShizukuBridge, @ApplicationContext context: Context): GestureExecutor {
        return ShizukuGestureExecutor(bridge, context)
    }
    
    @Provides
    @Singleton
    fun provideElementDetector(): ElementDetector {
        return object : ElementDetector {
            override suspend fun findElement(selector: ElementSelector, timeoutMs: Long): Result<DetectedElement> {
                // Implementation using FlowBotAccessibilityService.instance
                return Result.failure(Exception("Not implemented yet"))
            }

            override suspend fun findAllElements(selector: ElementSelector): Result<List<DetectedElement>> {
                return Result.failure(Exception("Not implemented yet"))
            }

            override fun isServiceRunning(): Boolean {
                return FlowBotAccessibilityService.instance != null
            }
        }
    }
}
