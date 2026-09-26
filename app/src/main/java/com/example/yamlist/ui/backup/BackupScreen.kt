package com.example.yamlist.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BackupUiState(
    val message: String? = null,
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
                    (openStream() ?: throw IOException("保存先を開けませんでした。"))
                        .use { backupManager.createBackup(it) }
                }
                _state.value = _state.value.copy(busy = false, message = "バックアップを作成しました。")
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, message = "バックアップに失敗しました: ${e.message}")
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
                        _state.value = _state.value.copy(message = result.reason, pendingRestore = null)
                    null -> _state.value = _state.value.copy(message = "ファイルを開けませんでした。")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "検証に失敗しました: ${e.message}")
            }
        }
    }

    fun confirmRestore() {
        val archive = _state.value.pendingRestore ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                withContext(Dispatchers.IO) { backupManager.restore(archive) }
                _state.value = BackupUiState(message = "復元しました。")
            } catch (e: Exception) {
                // Transaction rolled back; existing data preserved (spec §26.5).
                _state.value = _state.value.copy(busy = false, pendingRestore = null,
                    message = "復元に失敗しました。既存データは変更されていません: ${e.message}")
            }
        }
    }

    fun cancelRestore() { _state.value = _state.value.copy(pendingRestore = null, pendingManifest = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

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
                    IconButton(onClick = onBack) {
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

            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    val manifest = state.pendingManifest
    if (state.pendingRestore != null && manifest != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
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
