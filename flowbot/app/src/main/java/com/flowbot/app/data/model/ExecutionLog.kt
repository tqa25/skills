package com.flowbot.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowName: String,
    val runId: String,
    val stepId: String,
    val action: String,
    val status: StepStatus,
    val durationMs: Long,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class StepStatus {
    SUCCESS, FAILED, SKIPPED
}

@Entity(tableName = "workflow_metadata")
data class WorkflowMetadata(
    @PrimaryKey val fileName: String,
    val name: String,
    val lastRunAt: Long? = null,
    val runCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
