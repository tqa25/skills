package com.flowbot.app.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowbot.app.data.model.WorkflowMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: WorkflowMetadata)

    @Query("SELECT * FROM workflow_metadata ORDER BY lastRunAt DESC")
    fun getAll(): Flow<List<WorkflowMetadata>>

    @Query("SELECT * FROM workflow_metadata WHERE fileName = :fileName")
    suspend fun getByFileName(fileName: String): WorkflowMetadata?

    @Delete
    suspend fun delete(metadata: WorkflowMetadata)
}
