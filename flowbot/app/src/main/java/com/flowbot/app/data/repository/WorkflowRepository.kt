package com.flowbot.app.data.repository

import android.content.Context
import com.flowbot.app.data.model.Workflow
import com.flowbot.app.data.model.WorkflowMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages workflow JSON files on disk and their [WorkflowMetadata] rows in Room.
 *
 * Workflow files live under `context.filesDir/workflows/` and are serialised
 * with [kotlinx.serialization].
 */
@Singleton
class WorkflowRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataDao: WorkflowMetadataDao,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Parse a workflow JSON file from the workflows directory.
     */
    suspend fun loadWorkflow(fileName: String): Result<Workflow> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(getWorkflowDir(), fileName)
            require(file.exists()) { "Workflow file not found: $fileName" }
            json.decodeFromString<Workflow>(file.readText())
        }
    }

    /**
     * Persist a [Workflow] as JSON and upsert its metadata row.
     */
    suspend fun saveWorkflow(fileName: String, workflow: Workflow): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(getWorkflowDir(), fileName)
                file.writeText(json.encodeToString(workflow))

                metadataDao.upsert(
                    WorkflowMetadata(
                        fileName = fileName,
                        name = workflow.name,
                    )
                )
            }
        }

    /**
     * Delete both the JSON file and its metadata row.
     */
    suspend fun deleteWorkflow(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(getWorkflowDir(), fileName).delete()
            metadataDao.getByFileName(fileName)?.let { metadataDao.delete(it) }
            Unit
        }
    }

    /**
     * One-shot list of all tracked workflows (suspend, non-reactive).
     */
    suspend fun listWorkflows(): List<WorkflowMetadata> = withContext(Dispatchers.IO) {
        // Ensure metadata is in sync with what is actually on disk.
        syncMetadataWithDisk()
        // Collect the current snapshot from the Flow.
        val existing = metadataDao.getByFileName("") // dummy – use getAll snapshot
        // A simpler approach: just query all.
        val dir = getWorkflowDir()
        dir.listFiles { f -> f.extension == "json" }
            ?.map { f ->
                metadataDao.getByFileName(f.name) ?: WorkflowMetadata(
                    fileName = f.name,
                    name = f.nameWithoutExtension,
                )
            }
            .orEmpty()
    }

    /**
     * Copies default workflow JSON files from assets/workflows/ into the
     * app-internal workflows directory.  Safe to call on every launch –
     * existing files are never overwritten.
     */
    suspend fun copyDefaultWorkflows(): Unit = withContext(Dispatchers.IO) {
        val dir = getWorkflowDir()
        val assetManager = context.assets

        val assetFiles = runCatching {
            assetManager.list("workflows")
        }.getOrNull().orEmpty()

        for (name in assetFiles) {
            val target = File(dir, name)
            if (target.exists()) continue // never clobber user edits

            assetManager.open("workflows/$name").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Seed metadata row so the UI picks it up immediately.
            val workflow = runCatching {
                json.decodeFromString<Workflow>(target.readText())
            }.getOrNull()

            metadataDao.upsert(
                WorkflowMetadata(
                    fileName = name,
                    name = workflow?.name ?: name.removeSuffix(".json"),
                )
            )
        }
    }

    /**
     * Observe all workflow metadata reactively (Room + Flow).
     */
    fun getWorkflowsFlow(): Flow<List<WorkflowMetadata>> = metadataDao.getAll()

    // ── Internals ───────────────────────────────────────────────────────

    private fun getWorkflowDir(): File {
        val dir = File(context.filesDir, "workflows")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Walk the workflows directory and ensure every JSON file has a
     * corresponding [WorkflowMetadata] row.
     */
    private suspend fun syncMetadataWithDisk() {
        val dir = getWorkflowDir()
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()

        for (file in files) {
            if (metadataDao.getByFileName(file.name) == null) {
                val workflow = runCatching {
                    json.decodeFromString<Workflow>(file.readText())
                }.getOrNull()

                metadataDao.upsert(
                    WorkflowMetadata(
                        fileName = file.name,
                        name = workflow?.name ?: file.nameWithoutExtension,
                    )
                )
            }
        }
    }
}
