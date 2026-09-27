package com.example.yamlist.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.R
import com.example.yamlist.data.file.backup.BackupManager
import com.example.yamlist.ui.common.DestinationUnavailable
import com.example.yamlist.ui.common.UiText
import com.example.yamlist.ui.common.resolve
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BackupUiState(
    val message: UiText? = null,
    val pendingRestore: BackupManager.ParsedArchive? = null,
    val pendingManifest: BackupManager.Manifest? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()

    fun suggestedFileName(): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        return "yamlist-backup-$ts.zip"
    }

    fun createBackup(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            try {
                withContext(Dispatchers.IO) {
                    (openStream() ?: throw DestinationUnavailable())
                        .use { backupManager.createBackup(it) }
                }
                _state.value = _state.value.copy(busy = false, message = UiText(R.string.backup_created))
            } catch (e: DestinationUnavailable) {
                _state.value = _state.value.copy(busy = false, message = UiText(R.string.destination_unavailable))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false, message = UiText(R.string.backup_failed_fmt, e.message.orEmpty()),
                )
            }
        }
    }

    fun inspect(openStream: () -> InputStream?) {
        viewModelScope.launch {
            try {
                val (result, archive) = withContext(Dispatchers.IO) {
                    openStream()?.use { backupManager.inspect(it) } ?: (null to null)
                }
                when (result) {
                    is BackupManager.RestoreResult.Preview ->
                        _state.value = _state.value.copy(pendingRestore = archive, pendingManifest = result.manifest, message = null)
                    is BackupManager.RestoreResult.Invalid ->
                        _state.value = _state.value.copy(message = invalidMessage(result), pendingRestore = null)
                    null -> _state.value = _state.value.copy(message = UiText(R.string.file_open_failed))
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = UiText(R.string.backup_inspect_failed_fmt, e.message.orEmpty()))
            }
        }
    }

    fun confirmRestore() {
        if (_state.value.busy) return
        val archive = _state.value.pendingRestore ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                withContext(Dispatchers.IO) { backupManager.restore(archive) }
                _state.value = BackupUiState(message = UiText(R.string.restore_done))
            } catch (e: Exception) {
                // Transaction rolled back; existing data preserved (spec §26.5).
                _state.value = _state.value.copy(busy = false, pendingRestore = null,
                    message = UiText(R.string.restore_failed_fmt, e.message.orEmpty()))
            }
        }
    }

    private fun invalidMessage(result: BackupManager.RestoreResult.Invalid): UiText = when (result.reason) {
        BackupManager.InvalidReason.UNREADABLE -> UiText(R.string.backup_invalid_unreadable_fmt, result.detail)
        BackupManager.InvalidReason.NO_MANIFEST -> UiText(R.string.backup_invalid_no_manifest)
        BackupManager.InvalidReason.UNSUPPORTED_FORMAT -> UiText(R.string.backup_invalid_unsupported_fmt, result.detail)
        BackupManager.InvalidReason.CHECKSUM_MISMATCH -> UiText(R.string.backup_invalid_checksum)
    }

    fun cancelRestore() {
        if (!_state.value.busy) _state.value = _state.value.copy(pendingRestore = null, pendingManifest = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    BackHandler(enabled = state.busy) { /* Keep restore alive until it finishes. */ }

    val createZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.createBackup { context.contentResolver.openOutputStream(uri) }
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.inspect { context.contentResolver.openInputStream(uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { createZip.launch(viewModel.suggestedFileName()) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.create_backup)) }

            OutlinedButton(
                onClick = { openZip.launch(arrayOf("application/zip", "*/*")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.restore_backup)) }

            Text(stringResource(R.string.restore_note), style = MaterialTheme.typography.bodySmall)

            state.message?.let { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium) }
        }
    }

    val manifest = state.pendingManifest
    if (state.pendingRestore != null && manifest != null) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) viewModel.cancelRestore() },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Text(stringResource(
                    R.string.restore_confirm_fmt,
                    manifest.createdAt, manifest.projectCount, manifest.taskCount,
                ))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }, enabled = !state.busy) {
                    Text(stringResource(R.string.restore_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }, enabled = !state.busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
