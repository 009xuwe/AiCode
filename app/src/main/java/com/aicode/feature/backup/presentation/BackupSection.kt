package com.aicode.feature.backup.presentation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.aicode.core.ui.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.aicode.core.ui.AppTextField
import com.aicode.core.ui.dialogTextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.SwipeToDeleteRow
import com.aicode.feature.backup.domain.BackupOptions
import com.aicode.feature.backup.domain.WorkspaceBackupMeta
import com.aicode.feature.backup.domain.remote.BackupTarget
import com.aicode.feature.backup.domain.remote.BackupTargetType
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsGroupHeader
import com.aicode.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.Download
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.aicode.R

@Composable
internal fun BackupSection(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingExportOptions by remember { mutableStateOf(BackupOptions()) }
    val exportOptions by viewModel.exportOptions.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()

    var editingTarget by remember { mutableStateOf<BackupTarget?>(null) }
    var showTargetSheet by remember { mutableStateOf(false) }
    var targetToDelete by remember { mutableStateOf<BackupTarget?>(null) }
    var targetPickerMode by remember { mutableStateOf<NetworkPickerMode?>(null) }
    var uploadTarget by remember { mutableStateOf<BackupTarget?>(null) }
    var uploadPassword by remember { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<BackupTarget?>(null) }
    var restoreName by remember { mutableStateOf<String?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var networkWorkingTextRes by remember { mutableStateOf(R.string.backup_net_loading) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val pw = pendingExportPassword
            val opts = pendingExportOptions
            scope.launch {
                val os = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) }
                if (os != null) {
                    viewModel.export(pw, opts, os)
                } else {
                    Toast.makeText(context, context.getString(R.string.backup_write_failed, uri.toString()), Toast.LENGTH_LONG).show()
                    viewModel.reset()
                }
            }
        } else {
            viewModel.reset()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            pendingAction = null
            return@rememberLauncherForActivityResult
        }
        pendingImportUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
    ) {
        // 导出数据范围：开关即持久化
        SettingsGroupHeader(text = stringResource(R.string.backup_select_data))
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.common_ai_providers),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.providers,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(providers = it)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.backup_data_remote),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.remoteConnections,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(remoteConnections = it)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.backup_data_chat_history),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.chatHistory,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(chatHistory = it)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.backup_data_mcp),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.mcpServers,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(mcpServers = it)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.backup_data_permissions),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.permissionRules,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(permissionRules = it)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.backup_data_app_settings),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.appSettings,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(appSettings = it)) }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.backup_data_workspace),
                trailing = {
                    AppSwitch(
                        checked = exportOptions.workspaceFiles,
                        onCheckedChange = { viewModel.updateExportOptions(exportOptions.copy(workspaceFiles = it)) }
                    )
                }
            )
            WorkspaceBackupHint()
        }

        SettingsGroupHeader(text = stringResource(R.string.backup_actions))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Download,
                title = stringResource(R.string.backup_export_title),
                subtitle = stringResource(R.string.backup_export_subtitle),
                enabled = state !is BackupState.Working,
                onClick = { pendingAction = PendingAction.ExportPassword }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Upload,
                title = stringResource(R.string.backup_import_title),
                subtitle = stringResource(R.string.backup_import_subtitle),
                enabled = state !is BackupState.Working,
                onClick = {
                    pendingAction = PendingAction.Import
                    importLauncher.launch(arrayOf("application/octet-stream", "application/gzip", "*/*"))
                }
            )
        }

        // 网络备份：目标管理 + 上传/恢复
        SettingsGroupHeader(text = stringResource(R.string.backup_net_section))
        SettingsGroup {
            if (targets.isEmpty()) {
                SettingsRow(
                    title = stringResource(R.string.backup_net_no_target),
                    enabled = false
                )
            } else {
                targets.forEach { target ->
                    SwipeToDeleteRow(
                        onDelete = { targetToDelete = target },
                        onClick = {
                            editingTarget = target
                            showTargetSheet = true
                        }
                    ) {
                        SettingsRow(
                            title = target.name,
                            subtitle = targetSubtitle(target)
                        )
                    }
                    SettingsDivider()
                }
            }
            SettingsRow(
                icon = FeatherIcons.Plus,
                title = stringResource(R.string.backup_net_add_target),
                onClick = {
                    editingTarget = null
                    showTargetSheet = true
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Upload,
                title = stringResource(R.string.backup_net_upload),
                subtitle = stringResource(R.string.backup_net_upload_subtitle),
                enabled = targets.isNotEmpty() && state !is BackupState.Working && networkState !is NetworkBackupState.Working,
                onClick = { targetPickerMode = NetworkPickerMode.Upload }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Download,
                title = stringResource(R.string.backup_net_restore),
                subtitle = stringResource(R.string.backup_net_restore_subtitle),
                enabled = targets.isNotEmpty() && state !is BackupState.Working && networkState !is NetworkBackupState.Working,
                onClick = { targetPickerMode = NetworkPickerMode.Restore }
            )
        }
    }

    // 导出：输口令（可留空）
    if (pendingAction == PendingAction.ExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_set_password),
            subtitle = stringResource(R.string.backup_password_hint),
            confirmText = stringResource(R.string.backup_export_btn),
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                pendingExportPassword = password
                pendingExportOptions = exportOptions
                password = ""
                pendingAction = null
                exportLauncher.launch("aicode-backup-${System.currentTimeMillis()}.tar.gz")
            },
            onDismiss = {
                password = ""
                pendingAction = null
            }
        )
    }

    // 导入口令弹窗（SAF 选完文件后弹出）
    if (pendingAction == PendingAction.Import && pendingImportUri != null) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_input),
            subtitle = stringResource(R.string.backup_password_optional_hint),
            confirmText = stringResource(R.string.backup_import_btn),
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                val pw = password
                val uri = pendingImportUri
                password = ""
                pendingAction = null
                pendingImportUri = null
                if (uri != null) viewModel.import(uri, pw)
            },
            onDismiss = {
                password = ""
                pendingAction = null
                pendingImportUri = null
            }
        )
    }

    // 目标编辑底部弹窗
    if (showTargetSheet) {
        BackupTargetSheet(
            initial = editingTarget,
            onDismiss = { showTargetSheet = false },
            onSave = { viewModel.saveTarget(it); showTargetSheet = false },
            onTest = { target, callback -> viewModel.testTarget(target, callback) }
        )
    }

    // 目标删除确认
    targetToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { targetToDelete = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.backup_net_delete_target_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTarget(target.id)
                    targetToDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { targetToDelete = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // 目标选择（上传 / 恢复）
    targetPickerMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { targetPickerMode = null },
            title = { Text(stringResource(R.string.backup_net_select_target)) },
            text = {
                Column {
                    targets.forEach { target ->
                        TextButton(onClick = {
                            targetPickerMode = null
                            when (mode) {
                                NetworkPickerMode.Upload -> {
                                    uploadTarget = target
                                    uploadPassword = ""
                                }
                                NetworkPickerMode.Restore -> {
                                    networkWorkingTextRes = R.string.backup_net_loading
                                    viewModel.loadRemoteBackups(target)
                                }
                            }
                        }) { Text(target.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { targetPickerMode = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // 上传口令
    uploadTarget?.let { target ->
        PasswordDialog(
            title = stringResource(R.string.backup_set_password),
            subtitle = stringResource(R.string.backup_password_hint),
            confirmText = stringResource(R.string.backup_net_upload),
            password = uploadPassword,
            onPasswordChange = { uploadPassword = it },
            onConfirm = {
                val t = uploadTarget
                val pw = uploadPassword
                uploadTarget = null
                uploadPassword = ""
                if (t != null) {
                    networkWorkingTextRes = R.string.backup_net_uploading
                    viewModel.uploadToTarget(t, pw)
                }
            },
            onDismiss = { uploadTarget = null; uploadPassword = "" }
        )
    }

    // 云端恢复口令
    if (restoreTarget != null && restoreName != null) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_input),
            subtitle = stringResource(R.string.backup_password_optional_hint),
            confirmText = stringResource(R.string.backup_import_btn),
            password = restorePassword,
            onPasswordChange = { restorePassword = it },
            onConfirm = {
                val t = restoreTarget
                val n = restoreName
                val pw = restorePassword
                restoreTarget = null
                restoreName = null
                restorePassword = ""
                if (t != null && n != null) viewModel.restoreFromRemote(t, n, pw)
            },
            onDismiss = { restoreTarget = null; restoreName = null; restorePassword = "" }
        )
    }

    // 上传完成提示
    LaunchedEffect(networkState) {
        val done = networkState as? NetworkBackupState.UploadDone
        if (done != null) {
            Toast.makeText(
                context,
                context.getString(R.string.backup_net_upload_done, done.targetName),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.dismissNetworkState()
        }
    }

    if (networkState is NetworkBackupState.Working) {
        NetworkProgressDialog(networkWorkingTextRes)
    }

    val remoteList = networkState as? NetworkBackupState.RemoteList
    if (remoteList != null) {
        RemoteBackupDialog(
            targetName = remoteList.targetName,
            items = remoteList.items,
            onRestore = { item ->
                val target = targets.firstOrNull { it.id == remoteList.targetId }
                if (target != null) {
                    restoreTarget = target
                    restoreName = item.name
                    restorePassword = ""
                } else {
                    viewModel.dismissNetworkState()
                }
            },
            onDelete = { item ->
                val target = targets.firstOrNull { it.id == remoteList.targetId }
                if (target != null) {
                    networkWorkingTextRes = R.string.backup_net_loading
                    viewModel.deleteRemoteBackup(target, item.name)
                } else {
                    viewModel.dismissNetworkState()
                }
            },
            onDismiss = { viewModel.dismissNetworkState() }
        )
    }

    val netError = networkState as? NetworkBackupState.Error
    if (netError != null) {
        ResultDialog(
            title = stringResource(R.string.backup_operation_failed),
            message = netError.message,
            onDismiss = { viewModel.dismissNetworkState() }
        )
    }

    // 导出完成 → 提示并复位
    LaunchedEffect(state) {
        if (state is BackupState.ExportDone) {
            Toast.makeText(context, context.getString(R.string.backup_exported), Toast.LENGTH_SHORT).show()
            viewModel.reset()
        }
    }

    if (state is BackupState.Working) {
        ProgressDialog()
    }

    when (state) {
        is BackupState.Error -> ResultDialog(
            title = stringResource(R.string.backup_operation_failed),
            message = (state as BackupState.Error).message,
            onDismiss = { viewModel.reset() }
        )
        is BackupState.ImportSuccess -> ResultDialog(
            title = stringResource(R.string.backup_import_done),
            message = buildImportSummary(context, (state as BackupState.ImportSuccess).stats),
            onDismiss = { viewModel.reset() }
        )
        is BackupState.WorkspaceSelection -> WorkspaceRestoreDialog(
            workspaces = (state as BackupState.WorkspaceSelection).workspaces,
            onConfirm = { selected -> viewModel.confirmImportSelection(selected) },
            onDismiss = { viewModel.cancelImportSelection() }
        )
        else -> {}
    }
}

private enum class PendingAction { ExportPassword, Import }

private enum class NetworkPickerMode { Upload, Restore }

@Composable
private fun WorkspaceBackupHint() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Icon(
            imageVector = FeatherIcons.AlertCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = stringResource(R.string.backup_workspace_git_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkspaceRestoreDialog(
    workspaces: List<WorkspaceBackupMeta>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var checked by remember { mutableStateOf(workspaces.associate { it.name to true }) }
    val anyChecked = checked.values.any { it }
    val allChecked = workspaces.isNotEmpty() && checked.values.all { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_workspaces_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_restore_workspaces_warning),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(Spacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = { v -> checked = workspaces.associate { it.name to v } }
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_workspaces_select_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                workspaces.forEach { ws ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = checked[ws.name] ?: false,
                            onCheckedChange = { v -> checked = checked + (ws.name to v) }
                        )
                        Text(
                            text = ws.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.backup_restore_workspaces_files, ws.fileCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(checked.filterValues { it }.keys) },
                enabled = anyChecked
            ) {
                Text(stringResource(R.string.backup_restore_workspaces_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    subtitle: String,
    confirmText: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                AppTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.backup_password_label),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun ProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_processing)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.backup_processing_data))
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun NetworkProgressDialog(messageRes: Int) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_processing)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(messageRes))
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun targetSubtitle(target: BackupTarget): String = when (target.type) {
    BackupTargetType.WEBDAV -> "${stringResource(R.string.backup_net_type_webdav)} · ${target.endpoint}"
    BackupTargetType.S3 ->
        "${stringResource(R.string.backup_net_type_s3)} · ${target.endpoint.trimEnd('/')}/${target.bucket}"
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_got_it)) } }
    )
}

private fun buildImportSummary(context: android.content.Context, stats: com.aicode.feature.backup.domain.RestoreStats): String = buildString {
    appendLine(context.getString(R.string.backup_restored_data))
    if (stats.providers > 0) appendLine(context.getString(R.string.backup_stat_providers, stats.providers))
    if (stats.remoteConnections > 0) appendLine(context.getString(R.string.backup_stat_remote_connections, stats.remoteConnections))
    if (stats.remoteMounts > 0) appendLine(context.getString(R.string.backup_stat_remote_mounts, stats.remoteMounts))
    if (stats.chatSessions > 0) appendLine(context.getString(R.string.backup_stat_chat_sessions, stats.chatSessions))
    if (stats.agentMessages > 0) appendLine(context.getString(R.string.backup_stat_chat_messages, stats.agentMessages))
    if (stats.todoItems > 0) appendLine(context.getString(R.string.backup_stat_todo_items, stats.todoItems))
    if (stats.mcpServers > 0) appendLine(context.getString(R.string.backup_stat_mcp_servers, stats.mcpServers))
    if (stats.globalPermissionRules > 0) appendLine(context.getString(R.string.backup_stat_permission_rules, stats.globalPermissionRules))
    if (stats.workspaceFiles > 0) appendLine(context.getString(R.string.backup_stat_workspace_files, stats.workspaceFiles))
    append(context.getString(R.string.backup_settings_covered))
}
