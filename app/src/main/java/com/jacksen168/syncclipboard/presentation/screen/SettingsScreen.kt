package com.jacksen168.syncclipboard.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import android.provider.DocumentsContract
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.presentation.component.UpdateDialog
import com.jacksen168.syncclipboard.presentation.component.NoUpdateDialog
import com.jacksen168.syncclipboard.presentation.component.ErrorDialog
import com.jacksen168.syncclipboard.presentation.viewmodel.SettingsViewModel
import com.jacksen168.syncclipboard.presentation.viewmodel.UpdateViewModel

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onDownloadLocationRequest: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverConfig by viewModel.serverConfig.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val requestFileSelection by viewModel.requestFileSelection.collectAsState()
    val context = LocalContext.current
    
    // 错误提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // 这里可以显示SnackBar
        }
    }
    
    // 成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            // 这里可以显示SnackBar
        }
    }
    
    // 监听文件选择请求
    LaunchedEffect(requestFileSelection) {
        if (requestFileSelection) {
            // 清除请求状态
            viewModel.clearFileSelectionRequest()
            // 调用外部传入的回调
            onDownloadLocationRequest()
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 服务器设置
        item {
            ServerSettingsCard(
                serverConfig = serverConfig,
                uiState = uiState,
                onUrlChange = viewModel::updateServerUrl,
                onUsernameChange = viewModel::updateUsername,
                onPasswordChange = viewModel::updatePassword,
                onTrustUnsafeSSLChange = viewModel::updateTrustUnsafeSSL,
                onTestConnection = viewModel::testConnection
            )
        }
        
        // 同步设置
        item {
            SyncSettingsCard(
                appSettings = appSettings,
                onAutoSyncChange = viewModel::updateAutoSync,
                onSyncIntervalChange = viewModel::updateSyncInterval,
                onSyncOnBootChange = viewModel::updateSyncOnBoot,
                onRewriteAfterUnlockChange = viewModel::updateRewriteAfterUnlock,
                onDeviceNameChange = viewModel::updateDeviceName,
                onClipboardHistoryCountChange = viewModel::updateClipboardHistoryCount,
                onDownloadLocationChange = viewModel::updateDownloadLocation,
                onRequestDownloadLocationSelection = viewModel::requestDownloadLocationSelection,
                onAutoSaveFilesChange = viewModel::updateAutoSaveFiles,
                syncIntervalSeconds = viewModel.getSyncIntervalSeconds()
            )
        }
        
        // 其他设置
        item {
            OtherSettingsCard(
                appSettings = appSettings,
                onShowNotificationsChange = viewModel::updateShowNotifications,
                onHideInRecentsChange = viewModel::updateHideInRecents
            )
        }
        
        // 实验性功能
        item {
            ExperimentalFeaturesCard(
                appSettings = appSettings,
                onForegroundServiceKeepaliveChange = viewModel::updateForegroundServiceKeepalive
            )
        }
        
        // 杂项
        item {
            MiscellaneousCard(viewModel = viewModel)
        }
        
        // 关于信息
        item {
            AboutCard()
        }
    }
}

/**
 * 服务器设置卡片
 */
@Composable
fun ServerSettingsCard(
    serverConfig: com.jacksen168.syncclipboard.data.model.ServerConfig,
    uiState: com.jacksen168.syncclipboard.presentation.viewmodel.SettingsUiState,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTrustUnsafeSSLChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.server_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 服务器地址
            OutlinedTextField(
                value = serverConfig.url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.server_url)) },
                placeholder = { Text(stringResource(R.string.server_url_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            
            // 用户名
            OutlinedTextField(
                value = serverConfig.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text(stringResource(R.string.username_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 密码
            OutlinedTextField(
                value = serverConfig.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                placeholder = { Text(stringResource(R.string.password_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            
            // 信任不安全SSL设置
            SettingItem(
                title = stringResource(R.string.trust_unsafe_ssl),
                description = stringResource(R.string.trust_unsafe_ssl_desc),
                icon = Icons.Default.Security,
                trailing = {
                    Switch(
                        checked = serverConfig.trustUnsafeSSL,
                        onCheckedChange = onTrustUnsafeSSLChange
                    )
                }
            )
            
            // 状态信息区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 10.dp, max = 45.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在测试连接...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    uiState.error != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp) // 稍微向下偏移以对齐文本首行
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item {
                                    Text(
                                        text = uiState.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                    uiState.successMessage != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = uiState.successMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {
                        // 占位符，保持布局稳定
                        Text(
                            text = " ", // 使用空格而不是空字符串，确保占用空间
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // 测试连接
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.test_connection))
                }
            }
        }
    }
}

/**
 * 同步设置卡片
 */
@Composable
fun SyncSettingsCard(
    appSettings: com.jacksen168.syncclipboard.data.model.AppSettings,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncIntervalChange: (Long) -> Unit,
    onSyncOnBootChange: (Boolean) -> Unit,
    onRewriteAfterUnlockChange: (Boolean) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onClipboardHistoryCountChange: (Int) -> Unit,
    onDownloadLocationChange: (String) -> Unit,
    onRequestDownloadLocationSelection: () -> Unit,
    onAutoSaveFilesChange: (Boolean) -> Unit,
    syncIntervalSeconds: Long
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var tempInterval by remember { mutableStateOf(syncIntervalSeconds.toString()) }
    var showHistoryCountDialog by remember { mutableStateOf(false) }
    var tempHistoryCount by remember { mutableStateOf(appSettings.clipboardHistoryCount.toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.sync_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 自动同步开关
            SettingItem(
                title = stringResource(R.string.auto_sync),
                description = stringResource(R.string.auto_sync_desc),
                icon = Icons.Default.PlayArrow,
                trailing = {
                    Switch(
                        checked = appSettings.autoSync,
                        onCheckedChange = onAutoSyncChange
                    )
                }
            )
            
            // 同步间隔
            SettingItem(
                title = stringResource(R.string.sync_interval),
                description = stringResource(R.string.sync_interval_desc),
                icon = Icons.Default.AccessTime,
                trailing = {
                    TextButton(
                        onClick = { 
                            tempInterval = syncIntervalSeconds.toString()
                            showIntervalDialog = true 
                        }
                    ) {
                        Text("${syncIntervalSeconds}秒")
                    }
                }
            )
            
            // 启动时自动开启服务
            SettingItem(
                title = stringResource(R.string.sync_on_boot),
                description = stringResource(R.string.sync_on_boot_desc),
                icon = Icons.Default.Power,
                trailing = {
                    Switch(
                        checked = appSettings.syncOnBoot,
                        onCheckedChange = onSyncOnBootChange
                    )
                }
            )
            
            // 解锁后自动重新写入
            SettingItem(
                title = stringResource(R.string.rewrite_after_unlock),
                description = stringResource(R.string.rewrite_after_unlock_desc),
                icon = Icons.Default.LockOpen,
                trailing = {
                    Switch(
                        checked = appSettings.rewriteAfterUnlock,
                        onCheckedChange = onRewriteAfterUnlockChange
                    )
                }
            )
            
            // 剪贴板历史显示数量
            SettingItem(
                title = stringResource(R.string.clipboard_history_count),
                description = stringResource(R.string.clipboard_history_count_desc),
                icon = Icons.Default.List,
                trailing = {
                    TextButton(
                        onClick = { 
                            tempHistoryCount = appSettings.clipboardHistoryCount.toString()
                            showHistoryCountDialog = true 
                        }
                    ) {
                        Text("${appSettings.clipboardHistoryCount}条")
                    }
                }
            )
            
            // 文件下载位置
            SettingItem(
                title = stringResource(R.string.download_location),
                description = if (appSettings.downloadLocation.isEmpty()) stringResource(R.string.download_location_empty) else getReadablePathFromUri(appSettings.downloadLocation),
                icon = Icons.Default.Folder,
                trailing = {
                    TextButton(
                        onClick = { 
                            // 触发文件选择请求
                            onRequestDownloadLocationSelection()
                        }
                    ) {
                        Text(if (appSettings.downloadLocation.isEmpty()) stringResource(R.string.select) else stringResource(R.string.change))
                    }
                }
            )
            
            // 文件自动保存开关
            SettingItem(
                title = stringResource(R.string.auto_save_files),
                description = stringResource(R.string.auto_save_files_desc),
                icon = Icons.Default.Save,
                trailing = {
                    Switch(
                        checked = appSettings.autoSaveFiles,
                        onCheckedChange = onAutoSaveFilesChange
                    )
                }
            )
        }
    }
    
    // 同步间隔对话框
    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.set_sync_interval)) },
            text = {
                OutlinedTextField(
                    value = tempInterval,
                    onValueChange = { tempInterval = it },
                    label = { Text(stringResource(R.string.interval_time_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tempInterval.toLongOrNull()?.let { interval ->
                            if (interval > 0) {
                                onSyncIntervalChange(interval)
                                showIntervalDialog = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // 历史显示数量对话框
    if (showHistoryCountDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryCountDialog = false },
            title = { Text(stringResource(R.string.set_clipboard_history_count)) },
            text = {
                OutlinedTextField(
                    value = tempHistoryCount,
                    onValueChange = { tempHistoryCount = it },
                    label = { Text(stringResource(R.string.display_clipboard_history_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tempHistoryCount.toIntOrNull()?.let { count ->
                            if (count > 0 && count <= 200) {
                                onClipboardHistoryCountChange(count)
                                showHistoryCountDialog = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryCountDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 设置项组件
 */
@Composable
fun SettingItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        trailing()
    }
}

/**
 * 关于卡片
 */
@Composable
fun AboutCard() {
    val context = LocalContext.current
    
    // 更新检查相关
    val updateViewModel = remember { UpdateViewModel(context) }
    val isChecking by updateViewModel.isChecking.collectAsState()
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsState()
    val showNoUpdateMessage by updateViewModel.showNoUpdateMessage.collectAsState()
    val errorMessage by updateViewModel.errorMessage.collectAsState()
    
    // 显示更新对话框
    updateInfo?.let { info ->
        if (showUpdateDialog && info.hasUpdate) {
            UpdateDialog(
                updateInfo = info,
                onUpdateClick = updateViewModel::goToUpdate,
                onLaterClick = updateViewModel::remindLater,
                onDismiss = updateViewModel::dismissUpdateDialog
            )
        }
    }
    
    // 显示无更新提示
    if (showNoUpdateMessage) {
        NoUpdateDialog(
            onDismiss = updateViewModel::dismissNoUpdateMessage
        )
    }
    
    // 显示错误提示
    errorMessage?.let { error ->
        ErrorDialog(
            message = error,
            onDismiss = updateViewModel::dismissErrorMessage
        )
    }
    
    // 获取应用版本信息
    val packageInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: Exception) {
        null
    }
    
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "1"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "1"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 应用信息
            SettingItem(
                title = stringResource(R.string.app_name),
                description = stringResource(R.string.version_info, versionName, versionCode),
                icon = Icons.Default.Build,
                trailing = {
                    IconButton(
                        onClick = { updateViewModel.checkForUpdate() },
                        enabled = !isChecking
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = stringResource(R.string.check_for_updates),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
            
            // GitHub链接
            SettingItem(
                title = stringResource(R.string.github_project),
                description = "https://github.com/jacksen168sub/SyncClipboard-Android",
                icon = Icons.Default.Star,
                trailing = {
                    IconButton(onClick = { 
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/jacksen168sub/SyncClipboard-Android"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 处理失败
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = stringResource(R.string.open_link)
                        )
                    }
                }
            )
            
            // 服务端项目链接
            SettingItem(
                title = stringResource(R.string.server_side),
                description = "https://github.com/Jeric-X/SyncClipboard",
                icon = Icons.Default.Storage,
                trailing = {
                    IconButton(onClick = { 
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Jeric-X/SyncClipboard"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 处理失败
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = stringResource(R.string.open_link)
                        )
                    }
                }
            )
            
            // 系统限制说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.important_notice),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = stringResource(R.string.system_limitations_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * 其他设置卡片
 */
@Composable
fun OtherSettingsCard(
    appSettings: com.jacksen168.syncclipboard.data.model.AppSettings,
    onShowNotificationsChange: (Boolean) -> Unit,
    onHideInRecentsChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.other_settings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 常驻通知
            SettingItem(
                title = stringResource(R.string.persistent_notification),
                description = stringResource(R.string.persistent_notification_desc),
                icon = Icons.Default.Notifications,
                trailing = {
                    Switch(
                        checked = appSettings.showNotifications,
                        onCheckedChange = onShowNotificationsChange
                    )
                }
            )
            
            // 在多任务页面隐藏
            SettingItem(
                title = stringResource(R.string.hide_in_recents),
                description = stringResource(R.string.hide_in_recents_desc),
                icon = Icons.Default.VisibilityOff,
                trailing = {
                    Switch(
                        checked = appSettings.hideInRecents,
                        onCheckedChange = onHideInRecentsChange
                    )
                }
            )
        }
    }
}

/**
 * 实验性功能卡片
 */
@Composable
fun ExperimentalFeaturesCard(
    appSettings: com.jacksen168.syncclipboard.data.model.AppSettings,
    onForegroundServiceKeepaliveChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.experimental_features),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 警告信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.experimental_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // 前台服务保活
            SettingItem(
                title = stringResource(R.string.foreground_service_keepalive),
                description = stringResource(R.string.foreground_service_keepalive_desc),
                icon = Icons.Default.Shield,
                trailing = {
                    Switch(
                        checked = appSettings.foregroundServiceKeepalive,
                        onCheckedChange = onForegroundServiceKeepaliveChange
                    )
                }
            )
        }
    }
}

/**
 * 杂项卡片
 */
@Composable
fun MiscellaneousCard(
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.miscellaneous),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 日志显示数量
            var showLogDisplayCountDialog by remember { mutableStateOf(false) }
            var tempLogDisplayCount by remember { mutableStateOf(viewModel.appSettings.value.logDisplayCount) }
            
            SettingItem(
                title = stringResource(R.string.log_display_count),
                description = stringResource(R.string.log_display_count_desc),
                icon = Icons.Default.List,
                trailing = {
                    TextButton(
                        onClick = { 
                            tempLogDisplayCount = viewModel.appSettings.value.logDisplayCount
                            showLogDisplayCountDialog = true 
                        }
                    ) {
                        Text(if (viewModel.appSettings.value.logDisplayCount == -1) "全部" else "${viewModel.appSettings.value.logDisplayCount}条")
                    }
                }
            )
            
            // 日志显示数量对话框
            if (showLogDisplayCountDialog) {
                AlertDialog(
                    onDismissRequest = { showLogDisplayCountDialog = false },
                    title = { Text(stringResource(R.string.set_log_display_count)) },
                    text = {
                        OutlinedTextField(
                            value = tempLogDisplayCount.toString(),
                            onValueChange = { tempLogDisplayCount = it.toIntOrNull() ?: tempLogDisplayCount },
                            label = { Text(stringResource(R.string.display_log_count)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (tempLogDisplayCount == -1 || (tempLogDisplayCount > 0 && tempLogDisplayCount <= 1000)) {
                                    viewModel.updateLogDisplayCount(tempLogDisplayCount)
                                    showLogDisplayCountDialog = false
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogDisplayCountDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            
            // 重置数据库
            SettingItem(
                title = stringResource(R.string.reset_database),
                description = stringResource(R.string.reset_database_desc),
                icon = Icons.Default.Restore,
                trailing = {
                    Button(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.reset_button))
                    }
                }
            )
        }
    }
    
    // 重置确认对话框
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.confirm_reset_title)) },
            text = { Text(stringResource(R.string.confirm_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetDatabase()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 从URI获取可读的路径描述
 */
fun getReadablePathFromUri(uriString: String): String {
    return try {
        if (uriString.startsWith("content://")) {
            val uri = Uri.parse(uriString)
            // 获取文档树的根路径
            val docId = DocumentsContract.getTreeDocumentId(uri)
            // 解码文档ID
            val decodedId = java.net.URLDecoder.decode(docId, "UTF-8")
            // 尝试获取更友好的路径表示
            when {
                decodedId.startsWith("primary:") -> {
                    "/存储/下载"
                }
                decodedId.startsWith("raw:") -> {
                    decodedId.substring(4)
                }
                else -> {
                    "已选择文件夹"
                }
            }
        } else {
            uriString
        }
    } catch (e: Exception) {
        uriString
    }
}