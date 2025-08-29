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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.presentation.viewmodel.SettingsViewModel

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val serverConfig by viewModel.serverConfig.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
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
                onShowNotificationsChange = viewModel::updateShowNotifications,
                onDeviceNameChange = viewModel::updateDeviceName,
                onClipboardHistoryCountChange = viewModel::updateClipboardHistoryCount,
                syncIntervalSeconds = viewModel.getSyncIntervalSeconds()
            )
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
    onTestConnection: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
            
            // 状态信息区域（预留固定空间避免布局跳动）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp) // 增加固定高度以确保足够空间
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
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
            
            // 操作按钮（只保留测试连接）
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
    onShowNotificationsChange: (Boolean) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onClipboardHistoryCountChange: (Int) -> Unit,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
            
            // 显示通知
            SettingItem(
                title = "显示通知",
                description = "显示同步状态通知",
                icon = Icons.Default.Notifications,
                trailing = {
                    Switch(
                        checked = appSettings.showNotifications,
                        onCheckedChange = onShowNotificationsChange
                    )
                }
            )
            
            // 剪贴板历史显示数量
            SettingItem(
                title = "历史显示数量",
                description = "设置剪贴板历史列表显示的数量",
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
        }
    }
    
    // 同步间隔对话框
    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("设置同步间隔") },
            text = {
                OutlinedTextField(
                    value = tempInterval,
                    onValueChange = { tempInterval = it },
                    label = { Text("间隔时间（秒）") },
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
            title = { Text("设置历史显示数量") },
            text = {
                OutlinedTextField(
                    value = tempHistoryCount,
                    onValueChange = { tempHistoryCount = it },
                    label = { Text("显示数量(1-100条)") },
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                    text = "关于",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 应用信息
            SettingItem(
                title = stringResource(R.string.app_name),
                description = "版本 $versionName ($versionCode)",
                icon = Icons.Default.Build,
                trailing = {}
            )
            
            // GitHub链接
            SettingItem(
                title = "GitHub项目",
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
                            contentDescription = "打开链接"
                        )
                    }
                }
            )
            
            // 服务端项目链接
            SettingItem(
                title = "服务端",
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
                            contentDescription = "打开链接"
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
                            text = "重要说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "由于安卓系统限制,在安卓10及以上的系统应用无法在后台读取剪贴板,但可以使用基于Root权限的工具(Magisk/Xposed)解除应用后台读取剪贴版的权限,如Riru-ClipboardWhitelist或Clipboard Whitelist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}