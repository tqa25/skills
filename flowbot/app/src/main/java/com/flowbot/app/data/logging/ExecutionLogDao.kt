package com.flowbot.app.data.logging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.flowbot.app.data.model.ExecutionLog

@Dao
interface ExecutionLogDao {

    @Insert
    suspend fun insert(log: ExecutionLog)

    @Query("SELECT * FROM execution_logs WHERE runId = :runId ORDER BY timestamp ASC")
    suspend fun getLogsByRunId(runId: String): List<ExecutionLog>

    @Query("SELECT * FROM execution_logs WHERE workflowName = :name ORDER BY timestamp DESC")
    suspend fun getLogsByWorkflow(name: String): List<ExecutionLog>

    @Query("SELECT DISTINCT runId FROM execution_logs WHERE workflowName = :name ORDER BY timestamp DESC")
    suspend fun getRunIds(name: String): List<String>

    @Query("DELETE FROM execution_logs WHERE runId = :runId")
    suspend fun deleteByRunId(runId: String)

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<ExecutionLog>
}
