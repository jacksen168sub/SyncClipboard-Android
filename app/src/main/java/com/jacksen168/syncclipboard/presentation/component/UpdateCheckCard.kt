package com.jacksen168.syncclipboard.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jacksen168.syncclipboard.presentation.viewmodel.UpdateViewModel

/**
 * 检查更新卡片
 */
@Composable
fun UpdateCheckCard() {
    val context = LocalContext.current
    val updateViewModel = remember { UpdateViewModel(context) }
    
    val isChecking by updateViewModel.isChecking.collectAsState()
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsState()
    
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
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "应用更新",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 当前版本信息
            updateInfo?.let { info ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "v${info.currentVersion}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (info.hasUpdate) {
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "最新版本",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${info.latestVersion}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // 检查更新按钮
            Button(
                onClick = { updateViewModel.checkForUpdate() },
                enabled = !isChecking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检查中...")
                } else {
                    Text("检查更新")
                }
            }
            
            // 更新状态提示
            updateInfo?.let { info ->
                if (info.hasUpdate) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "🎉 发现新版本 v${info.latestVersion}，点击上方按钮查看详情",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "✅ 已是最新版本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}