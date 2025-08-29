package com.jacksen168.syncclipboard.presentation.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.data.model.ClipboardItem
import com.jacksen168.syncclipboard.data.model.ClipboardType
import com.jacksen168.syncclipboard.data.model.SyncStatus
import com.jacksen168.syncclipboard.presentation.viewmodel.MainViewModel
import com.jacksen168.syncclipboard.service.ClipboardSyncService
import java.text.SimpleDateFormat
import java.util.*

/**
 * 主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardItems by viewModel.clipboardItems.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    
    // 错误提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // 这里可以显示SnackBar或其他错误提示
        }
    }
    
    // 成功提示
    LaunchedEffect(uiState.lastSyncMessage) {
        uiState.lastSyncMessage?.let {
            // 这里可以显示SnackBar或其他成功提示
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    // 状态文字提示
                    Text(
                        text = if (uiState.isConnected) "运行中" else "未运行",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    
                    // 手动同步按钮（同步到服务器）
                    IconButton(
                        onClick = { viewModel.performSync() },
                        enabled = uiState.isConnected
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "手动同步到服务器",
                            tint = if (uiState.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // 刷新按钮（刷新本地列表）
                    IconButton(
                        onClick = { viewModel.refreshClipboardList() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新列表"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // 启动/停止服务的悬浮按钮
            FloatingActionButton(
                onClick = {
                    viewModel.toggleSyncService(!uiState.isConnected)
                },
                containerColor = if (uiState.isConnected) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (uiState.isConnected) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isConnected) "停止同步" else "开始同步"
                )
            }
        }
    ) { paddingValues ->
        // 直接显示剪贴板历史列表
        ClipboardHistoryList(
            items = clipboardItems,
            onItemClick = { item ->
                viewModel.copyToClipboard(item)
            },
            onItemDelete = { item ->
                viewModel.deleteClipboardItem(item)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

/**
 * 状态芯片
 */
@Composable
fun StatusChip(
    status: String,
    color: Color
) {
    Surface(
        modifier = Modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 剪贴板历史列表
 */
@Composable
fun ClipboardHistoryList(
    items: List<ClipboardItem>,
    onItemClick: (ClipboardItem) -> Unit,
    onItemDelete: (ClipboardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.clipboard_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(items) { item ->
                ClipboardItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onDelete = { onItemDelete(item) }
                )
            }
        }
    }
}

/**
 * 剪贴板项目卡片
 */
@Composable
fun ClipboardItemCard(
    item: ClipboardItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = getTypeIcon(item.type),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = getTypeText(item.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row {
                    IconButton(
                        onClick = onClick // 使用传入的onClick回调
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileCopy,
                            contentDescription = stringResource(R.string.copy_to_clipboard),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 内容预览
            when (item.type) {
                ClipboardType.TEXT -> {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                ClipboardType.IMAGE -> {
                    ImagePreview(
                        imagePath = item.localPath ?: item.content,
                        fileName = item.fileName,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ClipboardType.FILE -> {
                    Text(
                        text = "文件: ${item.fileName ?: "未知文件"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 底部信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(item.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 显示同步状态标签
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (item.isSynced) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = if (item.isSynced) "已同步" else "未同步",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isSynced) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * 获取类型图标
 */
@Composable
fun getTypeIcon(type: ClipboardType): ImageVector {
    return when (type) {
        ClipboardType.TEXT -> Icons.Default.Description
        ClipboardType.IMAGE -> Icons.Default.PhotoLibrary
        ClipboardType.FILE -> Icons.Default.Attachment
    }
}

/**
 * 获取类型文本
 */
@Composable
fun getTypeText(type: ClipboardType): String {
    return when (type) {
        ClipboardType.TEXT -> stringResource(R.string.clipboard_text)
        ClipboardType.IMAGE -> stringResource(R.string.clipboard_image)
        ClipboardType.FILE -> stringResource(R.string.clipboard_file)
    }
}

/**
 * 格式化时间
 */
fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * 图片预览组件
 */
@Composable
fun ImagePreview(
    imagePath: String,
    fileName: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 检查文件是否存在
    val file = remember(imagePath) { java.io.File(imagePath) }
    val fileExists = remember(imagePath) { file.exists() }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 图片显示区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            if (fileExists) {
                // 使用 AsyncImage 显示图片
                var isLoading by remember { mutableStateOf(true) }
                var hasError by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = fileName ?: "剪贴板图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                        onLoading = { isLoading = true; hasError = false },
                        onSuccess = { isLoading = false; hasError = false },
                        onError = { isLoading = false; hasError = true }
                    )
                    
                    // 加载状态覆盖层
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "加载中...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // 错误状态覆盖层
                    if (hasError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "加载失败",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                // 文件不存在的状态
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "图片不存在",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    fileName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        // 文件名显示
        fileName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

