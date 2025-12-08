package com.jacksen168.syncclipboard.presentation.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 日志查看页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onCreateLogFile: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val logger = Logger.getInstance(context)
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var autoRefresh by remember { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 定期刷新日志
    LaunchedEffect(autoRefresh) {
        if (autoRefresh) {
            while (true) {
                delay(1000) // 每秒刷新一次
                logs = logger.getRecentLogs(1000)
                
                // 自动滚动到底部
                if (logs.isNotEmpty()) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(maxOf(0, logs.size - 1))
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.logs),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    // 自动刷新开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.auto_refresh),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoRefresh,
                            onCheckedChange = { autoRefresh = it },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    // 调用回调函数，让用户选择保存位置
                    onCreateLogFile?.invoke("sync_clipboard_logs.txt")
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = stringResource(R.string.export_logs)
                )
            }
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.no_logs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = log,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}