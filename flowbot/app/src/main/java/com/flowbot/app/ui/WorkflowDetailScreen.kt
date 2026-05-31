package com.flowbot.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowbot.app.core.engine.WorkflowEngine
import com.flowbot.app.core.engine.WorkflowResult
import com.flowbot.app.data.logging.ExecutionLogger
import com.flowbot.app.data.model.ExecutionLog
import com.flowbot.app.data.model.StepAction
import com.flowbot.app.data.model.StepStatus
import com.flowbot.app.data.model.Workflow
import com.flowbot.app.data.model.WorkflowStep
import com.flowbot.app.data.repository.WorkflowRepository
import com.flowbot.app.service.WorkflowRunnerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ── State types ─────────────────────────────────────────────────────────────────

data class WorkflowDetailState(
    val workflow: Workflow? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val executionState: ExecutionState = ExecutionState.IDLE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val liveLog: List<String> = emptyList(),
)

enum class ExecutionState { IDLE, RUNNING, COMPLETED, FAILED }

// ── Run history item ────────────────────────────────────────────────────────────

data class RunHistoryItem(
    val runId: String,
    val timestamp: Long,
    val stepCount: Int,
    val successCount: Int,
    val failedCount: Int,
)

// ── ViewModel ───────────────────────────────────────────────────────────────────

@HiltViewModel
class WorkflowDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workflowRepository: WorkflowRepository,
    private val executionLogger: ExecutionLogger,
    private val workflowEngine: WorkflowEngine,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val fileName: String = checkNotNull(savedStateHandle["fileName"])

    private val _state = MutableStateFlow(WorkflowDetailState())
    val state: StateFlow<WorkflowDetailState> = _state.asStateFlow()

    val runHistory: StateFlow<List<RunHistoryItem>> =
        getRunHistoryFlow(fileName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadWorkflow()
    }

    private fun loadWorkflow() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = workflowRepository.loadWorkflow(fileName)
            if (result.isSuccess) {
                val wf = result.getOrThrow()
                _state.update {
                    it.copy(
                        workflow = wf,
                        isLoading = false,
                        totalSteps = wf.steps.size,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to load workflow",
                    )
                }
            }
        }
    }

    fun runWorkflow() {
        val workflow = _state.value.workflow ?: return
        if (_state.value.executionState == ExecutionState.RUNNING) return

        // Delegate to foreground service for wake-lock & notification support
        val intent = Intent(appContext, WorkflowRunnerService::class.java).apply {
            putExtra(WorkflowRunnerService.EXTRA_FILE_NAME, fileName)
        }
        appContext.startForegroundService(intent)

        // Also track in-process for live UI feedback
        viewModelScope.launch {
            _state.update {
                it.copy(
                    executionState = ExecutionState.RUNNING,
                    currentStepIndex = 0,
                    liveLog = emptyList(),
                )
            }

            val runId = UUID.randomUUID().toString()
            val result = workflowEngine.execute(workflow, runId)

            _state.update {
                it.copy(
                    executionState = when (result) {
                        is WorkflowResult.Success -> ExecutionState.COMPLETED
                        is WorkflowResult.Failure -> ExecutionState.FAILED
                    },
                    liveLog = it.liveLog + when (result) {
                        is WorkflowResult.Success ->
                            "✓ Workflow completed (${result.stepsExecuted} steps)"
                        is WorkflowResult.Failure ->
                            "✗ ${result.error}"
                    },
                )
            }
        }
    }

    fun deleteWorkflow() {
        viewModelScope.launch {
            workflowRepository.deleteWorkflow(fileName)
        }
    }

    /** Build run history from the logger's raw logs, grouped by runId. */
    private fun getRunHistoryFlow(wfFileName: String): Flow<List<RunHistoryItem>> = flow {
        val runIds = executionLogger.getRunIds(wfFileName.removeSuffix(".json"))
        val history = runIds.mapNotNull { runId ->
            val logs = executionLogger.getLogsByRunId(runId)
            if (logs.isEmpty()) return@mapNotNull null
            RunHistoryItem(
                runId = runId,
                timestamp = logs.first().timestamp,
                stepCount = logs.size,
                successCount = logs.count { it.status == StepStatus.SUCCESS },
                failedCount = logs.count { it.status == StepStatus.FAILED },
            )
        }
        emit(history)
    }
}

// ── Composable ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailScreen(
    onNavigateBack: () -> Unit,
    onViewLog: (runId: String) -> Unit,
    viewModel: WorkflowDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val runHistory by viewModel.runHistory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.workflow?.name ?: "Workflow",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteWorkflow()
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            else -> {
                val workflow = state.workflow!!
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // ── Description ─────────────────────────────────────────
                    if (workflow.description.isNotBlank()) {
                        item {
                            Text(
                                text = workflow.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // ── Run button ──────────────────────────────────────────
                    item {
                        RunButton(
                            executionState = state.executionState,
                            onClick = { viewModel.runWorkflow() },
                        )
                    }

                    // ── Progress ────────────────────────────────────────────
                    if (state.executionState == ExecutionState.RUNNING) {
                        item {
                            ExecutionProgress(
                                currentStep = state.currentStepIndex,
                                totalSteps = state.totalSteps,
                            )
                        }
                    }

                    // ── Live log ────────────────────────────────────────────
                    if (state.liveLog.isNotEmpty()) {
                        item {
                            LiveLogCard(lines = state.liveLog)
                        }
                    }

                    // ── Steps ───────────────────────────────────────────────
                    item {
                        Text(
                            text = "Steps (${workflow.steps.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    itemsIndexed(workflow.steps) { index, step ->
                        StepCard(
                            index = index,
                            step = step,
                            isActive = state.executionState == ExecutionState.RUNNING && index == state.currentStepIndex,
                        )
                    }

                    // ── Run history ─────────────────────────────────────────
                    if (runHistory.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Recent Runs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        items(runHistory) { run ->
                            RunHistoryCard(item = run, onClick = { onViewLog(run.runId) })
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────────

@Composable
private fun RunButton(executionState: ExecutionState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = executionState != ExecutionState.RUNNING,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(
            imageVector = if (executionState == ExecutionState.RUNNING) Icons.Default.Refresh
            else Icons.Default.PlayArrow,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when (executionState) {
                ExecutionState.IDLE -> "Run Workflow"
                ExecutionState.RUNNING -> "Running…"
                ExecutionState.COMPLETED -> "Run Again"
                ExecutionState.FAILED -> "Retry"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ExecutionProgress(currentStep: Int, totalSteps: Int) {
    Column {
        LinearProgressIndicator(
            progress = { if (totalSteps > 0) (currentStep + 1).toFloat() / totalSteps else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Step ${currentStep + 1} of $totalSteps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveLogCard(lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Live Output",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            lines.takeLast(10).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StepCard(index: Int, step: WorkflowStep, isActive: Boolean) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isActive) 4.dp else 1.dp,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stepActionIcon(step.action),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${step.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stepParamsSummary(step),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunHistoryCard(item: RunHistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusIcon = if (item.failedCount == 0) Icons.Default.Check else Icons.Default.Close
            val statusColor = if (item.failedCount == 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTimestampFull(item.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${item.successCount}✓ ${item.failedCount}✗ of ${item.stepCount} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────────

private fun stepActionIcon(action: StepAction): String = when (action) {
    StepAction.TAP -> "👆"
    StepAction.LONG_PRESS -> "👇"
    StepAction.SWIPE -> "👈"
    StepAction.PINCH -> "🤏"
    StepAction.DRAG -> "✊"
    StepAction.TYPE_TEXT -> "⌨️"
    StepAction.PRESS_KEY -> "🔑"
    StepAction.COPY_CLIPBOARD -> "📋"
    StepAction.READ_CLIPBOARD -> "📖"
    StepAction.OPEN_APP -> "📱"
    StepAction.SCREENSHOT -> "📸"
    StepAction.DELAY -> "⏱️"
    StepAction.WAIT_FOR_ELEMENT -> "👁️"
    StepAction.LOOP -> "🔁"
    StepAction.PRESS_HOME -> "🏠"
    StepAction.PRESS_BACK -> "◀️"
    StepAction.PRESS_RECENT -> "📑"
    StepAction.SAVE_TO_FILE -> "💾"
}

private fun stepParamsSummary(step: WorkflowStep): String {
    val parts = mutableListOf(step.action.name.lowercase().replace('_', ' '))
    step.params.text?.let { parts.add("\"$it\"") }
    step.params.appPackage?.let { parts.add(it) }
    if (step.params.x != null && step.params.y != null) {
        parts.add("(${step.params.x}, ${step.params.y})")
    }
    step.params.direction?.let { parts.add(it.name.lowercase()) }
    step.params.count?.let { parts.add("×$it") }
    return parts.joinToString(" · ")
}

private fun formatTimestampFull(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
