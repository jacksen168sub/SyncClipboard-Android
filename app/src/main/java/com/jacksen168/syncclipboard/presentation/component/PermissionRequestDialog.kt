package com.jacksen168.syncclipboard.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.util.PermissionManager
import com.jacksen168.syncclipboard.util.PermissionStatus

/**
 * 权限请求对话框
 */
@Composable
fun PermissionRequestDialog(
    onDismiss: () -> Unit,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var permissionStatus by remember { mutableStateOf(PermissionManager.checkAllPermissions(context)) }
    
    // 检查权限状态变化
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // 每秒检查一次
            val newStatus = PermissionManager.checkAllPermissions(context)
            if (newStatus != permissionStatus) {
                permissionStatus = newStatus
                if (newStatus.allGranted) {
                    onAllPermissionsGranted()
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "权限设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            PermissionRequestContent(
                permissionStatus = permissionStatus,
                onRequestPermission = { type ->
                    when (type) {
                        PermissionType.NOTIFICATION -> {
                            if (context is androidx.activity.ComponentActivity) {
                                PermissionManager.requestNotificationPermission(context)
                            }
                        }
                        PermissionType.BATTERY_OPTIMIZATION -> {
                            if (context is androidx.activity.ComponentActivity) {
                                PermissionManager.requestIgnoreBatteryOptimizations(context)
                            }
                        }
                        PermissionType.AUTO_START -> {
                            PermissionManager.openAutoStartSettings(context)
                        }
                    }
                }
            )
        },
        confirmButton = {
            if (permissionStatus.allGranted) {
                Button(onClick = onAllPermissionsGranted) {
                    Text(stringResource(R.string.ok))
                }
            } else {
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.skip))
                }
            }
        },
        dismissButton = {
            if (!permissionStatus.allGranted) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/**
 * 权限请求内容
 */
@Composable
fun PermissionRequestContent(
    permissionStatus: PermissionStatus,
    onRequestPermission: (PermissionType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "为了正常使用应用功能，需要授予以下权限：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(3) { index ->
                val item = when (index) {
                    0 -> PermissionItemData(
                        type = PermissionType.NOTIFICATION,
                        title = stringResource(R.string.permission_notification_title),
                        description = stringResource(R.string.permission_notification_desc),
                        icon = Icons.Default.Notifications,
                        isGranted = permissionStatus.hasNotification
                    )
                    1 -> PermissionItemData(
                        type = PermissionType.BATTERY_OPTIMIZATION,
                        title = stringResource(R.string.permission_battery_title),
                        description = stringResource(R.string.permission_battery_desc),
                        icon = Icons.Default.BatteryFull,
                        isGranted = permissionStatus.hasIgnoreBatteryOptimization
                    )
                    else -> PermissionItemData(
                        type = PermissionType.AUTO_START,
                        title = "自启动权限",
                        description = "允许应用在后台自动启动",
                        icon = Icons.Default.Power,
                        isGranted = permissionStatus.hasAutoStart
                    )
                }
                PermissionItem(
                    item = item,
                    onRequestPermission = { onRequestPermission(item.type) }
                )
            }
        }
        
        if (permissionStatus.allGranted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "所有权限已授予",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 权限项目组件
 */
@Composable
fun PermissionItem(
    item: PermissionItemData,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (item.isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已授权",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = stringResource(R.string.grant_permission),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * 权限类型枚举
 */
enum class PermissionType {
    NOTIFICATION,
    BATTERY_OPTIMIZATION,
    AUTO_START
}

/**
 * 权限项目数据
 */
data class PermissionItemData(
    val type: PermissionType,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isGranted: Boolean
)
