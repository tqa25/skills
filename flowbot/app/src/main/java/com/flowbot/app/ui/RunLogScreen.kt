package com.flowbot.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowbot.app.data.logging.ExecutionLogger
import com.flowbot.app.data.model.ExecutionLog
import com.flowbot.app.data.model.StepStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ───────────────────────────────────────────────────────────────────────

data class RunLogState(
    val logs: List<ExecutionLog> = emptyList(),
    val isLoading: Boolean = true,
)

data class RunSummary(
    val totalSteps: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val totalDurationMs: Long,
)

// ── ViewModel ───────────────────────────────────────────────────────────────────

@HiltViewModel
class RunLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val executionLogger: ExecutionLogger,
) : ViewModel() {

    val runId: String = checkNotNull(savedStateHandle["runId"])

    private val _state = MutableStateFlow(RunLogState())
    val state: StateFlow<RunLogState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val logs = executionLogger.getLogsForRun(runId)
            _state.update { it.copy(logs = logs, isLoading = false) }
        }
    }
}

// ── Composable ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: RunLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else {
            val logs = state.logs
            val summary = computeSummary(logs)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // ── Summary card ────────────────────────────────────────
                item {
                    SummaryCard(summary = summary)
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Steps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // ── Step log entries ────────────────────────────────────
                items(logs) { log ->
                    StepLogCard(log = log)
                }
            }
        }
    }
}

// ── Summary card ────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(summary: RunSummary) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip(label = "Total", value = "${summary.totalSteps}")
                StatChip(
                    label = "Passed",
                    value = "${summary.passed}",
                    color = Color(0xFF4CAF50),
                )
                StatChip(
                    label = "Failed",
                    value = "${summary.failed}",
                    color = MaterialTheme.colorScheme.error,
                )
                StatChip(
                    label = "Skipped",
                    value = "${summary.skipped}",
                    color = Color(0xFFFFC107),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Duration: ${formatDuration(summary.totalDurationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Step log card ───────────────────────────────────────────────────────────────

@Composable
private fun StepLogCard(log: ExecutionLog) {
    val (icon, iconColor) = when (log.status) {
        StepStatus.SUCCESS -> "✓" to Color(0xFF4CAF50)
        StepStatus.FAILED -> "✗" to MaterialTheme.colorScheme.error
        StepStatus.SKIPPED -> "⊘" to Color(0xFFFFC107)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
                color = iconColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = log.stepId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDuration(log.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = log.action,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (log.errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────────

private fun computeSummary(logs: List<ExecutionLog>): RunSummary {
    return RunSummary(
        totalSteps = logs.size,
        passed = logs.count { it.status == StepStatus.SUCCESS },
        failed = logs.count { it.status == StepStatus.FAILED },
        skipped = logs.count { it.status == StepStatus.SKIPPED },
        totalDurationMs = logs.sumOf { it.durationMs },
    )
}

private fun formatDuration(ms: Long): String = when {
    ms < 1_000 -> "${ms}ms"
    ms < 60_000 -> "%.1fs".format(ms / 1_000.0)
    else -> {
        val mins = ms / 60_000
        val secs = (ms % 60_000) / 1_000
        "${mins}m ${secs}s"
    }
}
