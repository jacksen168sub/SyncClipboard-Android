package com.jacksen168.syncclipboard.presentation.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 日志等级枚举
 */
enum class LogLevel(val levelChar: Char) {
    VERBOSE('V'),
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E')
}

/**
 * 日志条目数据类
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val thread: String,
    val tag: String,
    val message: String
)

/**
 * 解析日志行
 */
fun parseLogLine(logLine: String): LogEntry? {
    // 日志格式: [时间] [等级] [线程] [标签] 消息
    val regex = Regex("""^\[(.*?)\] \[([VDIWE])\] \[(.*?)\] \[(.*?)\] (.*)$""")
    val matchResult = regex.find(logLine)
    
    return if (matchResult != null) {
        val (_, levelChar, thread, tag, message) = matchResult.destructured
        val level = LogLevel.values().find { it.levelChar == levelChar[0] } ?: LogLevel.DEBUG
        LogEntry(
            timestamp = matchResult.groupValues[1],
            level = level,
            thread = thread,
            tag = tag,
            message = message
        )
    } else {
        // 如果解析失败，返回一个默认的DEBUG日志条目
        LogEntry(
            timestamp = "",
            level = LogLevel.DEBUG,
            thread = "",
            tag = "",
            message = logLine
        )
    }
}

/**
 * 日志查看页面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    onCreateLogFile: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val logger = Logger.getInstance(context)
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var autoRefresh by remember { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // 日志等级筛选状态
    var verboseFilter by remember { mutableStateOf(true) }
    var debugFilter by remember { mutableStateOf(true) }
    var infoFilter by remember { mutableStateOf(true) }
    var warnFilter by remember { mutableStateOf(true) }
    var errorFilter by remember { mutableStateOf(true) }
    
    // 构建筛选列表
    val levelFilters = buildList {
        if (verboseFilter) add(LogLevel.VERBOSE)
        if (debugFilter) add(LogLevel.DEBUG)
        if (infoFilter) add(LogLevel.INFO)
        if (warnFilter) add(LogLevel.WARN)
        if (errorFilter) add(LogLevel.ERROR)
    }
    
    // 定期刷新日志
    LaunchedEffect(autoRefresh) {
        if (autoRefresh) {
            while (true) {
                delay(1000) // 每秒刷新一次
                val rawLogs = logger.getRecentLogs(1000)
                logEntries = rawLogs.mapNotNull { parseLogLine(it) }
                
                // 自动滚动到底部
                if (logEntries.isNotEmpty()) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(maxOf(0, logEntries.size - 1))
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 筛选栏
            LogFilterBar(
                verboseFilter = verboseFilter,
                onVerboseFilterChange = { verboseFilter = it },
                debugFilter = debugFilter,
                onDebugFilterChange = { debugFilter = it },
                infoFilter = infoFilter,
                onInfoFilterChange = { infoFilter = it },
                warnFilter = warnFilter,
                onWarnFilterChange = { warnFilter = it },
                errorFilter = errorFilter,
                onErrorFilterChange = { errorFilter = it }
            )
            
            if (logEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
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
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logEntries.filter { levelFilters.contains(it.level) }) { entry ->
                        LogEntryItem(entry = entry)
                    }
                }
            }
        }
    }
}

/**
 * 获取日志等级对应的颜色
 */
@Composable
fun LogLevel.getColor(): Color {
    return when (this) {
        LogLevel.VERBOSE -> Color(0xFF607D8B) // 蓝灰色，更柔和的VERBOSE颜色
        LogLevel.DEBUG -> Color(0xFF2196F3)   // 经典蓝色，用于DEBUG
        LogLevel.INFO -> Color(0xFF4CAF50)    // 绿色，用于INFO
        LogLevel.WARN -> Color(0xFFFF9800)    // 橙色，用于WARN
        LogLevel.ERROR -> Color(0xFFF44336)   // 红色，用于ERROR
    }
}

/**
 * 日志筛选栏
 */
@Composable
fun LogFilterBar(
    verboseFilter: Boolean,
    onVerboseFilterChange: (Boolean) -> Unit,
    debugFilter: Boolean,
    onDebugFilterChange: (Boolean) -> Unit,
    infoFilter: Boolean,
    onInfoFilterChange: (Boolean) -> Unit,
    warnFilter: Boolean,
    onWarnFilterChange: (Boolean) -> Unit,
    errorFilter: Boolean,
    onErrorFilterChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = verboseFilter,
                onClick = { onVerboseFilterChange(!verboseFilter) },
                label = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "V",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },  // 等级首字母并在正方形内居中
                modifier = Modifier.size(40.dp, 40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LogLevel.VERBOSE.getColor(),
                    containerColor = LogLevel.VERBOSE.getColor().copy(alpha = 0.3f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = verboseFilter,
                    borderColor = LogLevel.VERBOSE.getColor()
                )
            )
            
            FilterChip(
                selected = debugFilter,
                onClick = { onDebugFilterChange(!debugFilter) },
                label = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "D",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },  // 等级首字母并在正方形内居中
                modifier = Modifier.size(40.dp, 40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LogLevel.DEBUG.getColor(),
                    containerColor = LogLevel.DEBUG.getColor().copy(alpha = 0.3f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = debugFilter,
                    borderColor = LogLevel.DEBUG.getColor()
                )
            )
            
            FilterChip(
                selected = infoFilter,
                onClick = { onInfoFilterChange(!infoFilter) },
                label = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "I",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },  // 等级首字母并在正方形内居中
                modifier = Modifier.size(40.dp, 40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LogLevel.INFO.getColor(),
                    containerColor = LogLevel.INFO.getColor().copy(alpha = 0.3f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = infoFilter,
                    borderColor = LogLevel.INFO.getColor()
                )
            )
            
            FilterChip(
                selected = warnFilter,
                onClick = { onWarnFilterChange(!warnFilter) },
                label = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "W",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },  // 等级首字母并在正方形内居中
                modifier = Modifier.size(40.dp, 40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LogLevel.WARN.getColor(),
                    containerColor = LogLevel.WARN.getColor().copy(alpha = 0.3f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = warnFilter,
                    borderColor = LogLevel.WARN.getColor()
                )
            )
            
            FilterChip(
                selected = errorFilter,
                onClick = { onErrorFilterChange(!errorFilter) },
                label = { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "E",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                },  // 等级首字母并在正方形内居中
                modifier = Modifier.size(40.dp, 40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LogLevel.ERROR.getColor(),
                    containerColor = LogLevel.ERROR.getColor().copy(alpha = 0.3f)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = errorFilter,
                    borderColor = LogLevel.ERROR.getColor()
                )
            )

        }
    }
}

/**
 * 日志条目项
 */
@Composable
fun LogEntryItem(entry: LogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp), // 钝角
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // 固定高度
        ) {
            // 左侧边框颜色标识
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(entry.level.getColor())
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // 时间戳和标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 日志消息（最多5行）
                Text(
                    text = entry.message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}