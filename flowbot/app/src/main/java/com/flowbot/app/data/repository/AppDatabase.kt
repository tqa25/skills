package com.flowbot.app.data.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flowbot.app.data.logging.ExecutionLogDao
import com.flowbot.app.data.model.ExecutionLog
import com.flowbot.app.data.model.WorkflowMetadata

@Database(
    entities = [ExecutionLog::class, WorkflowMetadata::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun workflowMetadataDao(): WorkflowMetadataDao
}
