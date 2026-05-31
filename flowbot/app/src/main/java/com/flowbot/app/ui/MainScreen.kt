package com.flowbot.app.ui

import android.provider.Settings
import android.text.TextUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowbot.app.core.shizuku.ShizukuBridge
import com.flowbot.app.core.shizuku.ShizukuConnectionState
import com.flowbot.app.data.model.WorkflowMetadata
import com.flowbot.app.data.repository.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── ViewModel ───────────────────────────────────────────────────────────────────

@HiltViewModel
class MainViewModel @Inject constructor(
    private val workflowRepository: WorkflowRepository,
    private val shizukuBridge: ShizukuBridge,
) : ViewModel() {

    val shizukuState: StateFlow<ShizukuConnectionState> = shizukuBridge.connectionState

    val workflows: StateFlow<List<WorkflowMetadata>> =
        workflowRepository.getWorkflowsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            workflowRepository.refreshWorkflows()
            _isRefreshing.value = false
        }
    }

    fun importWorkflow(jsonContent: String, fileName: String) {
        viewModelScope.launch {
            workflowRepository.importWorkflow(jsonContent, fileName)
        }
    }
}

// ── Composable ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onWorkflowClick: (fileName: String) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val workflows by viewModel.workflows.collectAsState()
    val shizukuState by viewModel.shizukuState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current

    val accessibilityEnabled = isAccessibilityServiceEnabled(
        context,
        "com.flowbot.app/.service.FlowBotAccessibilityService",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlowBot") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: launch file picker and call viewModel.importWorkflow() */ },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import workflow")
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // ── Status cards ────────────────────────────────────────────
                item {
                    StatusBar(
                        shizukuState = shizukuState,
                        accessibilityEnabled = accessibilityEnabled,
                    )
                }

                if (workflows.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(workflows, key = { it.fileName }) { meta ->
                        WorkflowCard(
                            metadata = meta,
                            onClick = { onWorkflowClick(meta.fileName) },
                        )
                    }
                }
            }
        }
    }
}

// ── Status bar ──────────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(
    shizukuState: ShizukuConnectionState,
    accessibilityEnabled: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow(
                label = "Shizuku",
                connected = shizukuState == ShizukuConnectionState.CONNECTED,
                statusText = when (shizukuState) {
                    ShizukuConnectionState.CONNECTED -> "Connected"
                    ShizukuConnectionState.DISCONNECTED -> "Disconnected"
                    ShizukuConnectionState.NOT_INSTALLED -> "Not installed"
                    ShizukuConnectionState.PERMISSION_DENIED -> "Permission denied"
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(
                label = "Accessibility",
                connected = accessibilityEnabled,
                statusText = if (accessibilityEnabled) "Enabled" else "Disabled",
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, connected: Boolean, statusText: String) {
    val dotColor by animateColorAsState(
        targetValue = if (connected) Color(0xFF4CAF50) else Color(0xFFF44336),
        animationSpec = tween(durationMillis = 400),
        label = "statusDot",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: $statusText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Workflow card ────────────────────────────────────────────────────────────────

@Composable
private fun WorkflowCard(metadata: WorkflowMetadata, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("Runs: ${metadata.runCount}")
                        metadata.lastRunAt?.let { ts ->
                            append(" · Last: ${formatTimestamp(ts)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No workflows yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to import a workflow JSON file,\nor place .json files in the FlowBot directory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────────

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun isAccessibilityServiceEnabled(
    context: android.content.Context,
    service: String,
): Boolean {
    val enabledServices: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        if (colonSplitter.next().equals(service, ignoreCase = true)) return true
    }
    return false
}
