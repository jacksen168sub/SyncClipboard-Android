package com.jacksen168.syncclipboard.presentation.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jacksen168.syncclipboard.SyncClipboardApplication
import com.jacksen168.syncclipboard.data.model.*
import com.jacksen168.syncclipboard.data.repository.ClipboardRepository
import com.jacksen168.syncclipboard.data.repository.SettingsRepository
import com.jacksen168.syncclipboard.service.ClipboardSyncService
import com.jacksen168.syncclipboard.util.ContentLimiter
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 主界面ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val settingsRepository = (application as SyncClipboardApplication).settingsRepository
    private val clipboardRepository = ClipboardRepository(application, settingsRepository)
    
    // UI状态
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // 剪贴板历史列表
    private val _clipboardItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardItems: StateFlow<List<ClipboardItem>> = _clipboardItems.asStateFlow()
    
    // 同步状态
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    // 服务运行状态
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    // 服务器配置和应用设置
    val serverConfig = settingsRepository.serverConfigFlow
    val appSettings = settingsRepository.appSettingsFlow
    
    // 广播接收器，监听后台同步完成事件
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.jacksen168.syncclipboard.REFRESH_LIST") {
                // 后台同步完成，刷新列表
                refreshClipboardListSilently()
            }
        }
    }
    
    init {
        observeData()
        loadClipboardHistory()
        startRealtimeUpdates()
        registerRefreshReceiver()
        checkServiceRunningStatus()
        
        // 初始化时执行一次数据清理
        performDataCleanup()
    }
    
    override fun onCleared() {
        super.onCleared()
        // 取消注册广播接收器
        try {
            getApplication<Application>().unregisterReceiver(refreshReceiver)
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 注册广播接收器
     */
    private fun registerRefreshReceiver() {
        val filter = IntentFilter("com.jacksen168.syncclipboard.REFRESH_LIST")
        getApplication<Application>().registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }
    
    /**
     * 检查服务运行状态
     */
    private fun checkServiceRunningStatus() {
        viewModelScope.launch {
            try {
                // 初始检查服务状态
                val isRunning = ClipboardSyncService.isServiceRunning(getApplication())
                _isServiceRunning.value = isRunning
                
                // 如果服务正在运行，尝试获取同步状态
                if (isRunning) {
                    // 等待一下让服务初始化完成
                    delay(1000)
                    testConnection()
                }
                
                // 定期检查服务状态（每1秒检查一次）
                while (true) {
                    delay(1000) // 1秒
                    val currentStatus = ClipboardSyncService.isServiceRunning(getApplication())
                    if (_isServiceRunning.value != currentStatus) {
                        _isServiceRunning.value = currentStatus
                        if (!currentStatus) {
                            _syncStatus.value = SyncStatus.IDLE
                        }
                    }
                }
            } catch (e: Exception) {
                // 静默失败，不影响用户体验
            }
        }
    }
    

    /**
     * 静默刷新列表（不显示加载状态）
     */
    private fun refreshClipboardListSilently() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.appSettingsFlow.first()
                val items = clipboardRepository.getRecentItems(settings.clipboardHistoryCount)
                _clipboardItems.value = items
            } catch (e: Exception) {
                // 静默失败，不显示错误
            }
        }
    }
    
    /**
     * 观察数据变化
     */
    private fun observeData() {
        viewModelScope.launch {
            combine(
                serverConfig,
                appSettings,
                syncStatus,
                _isServiceRunning
            ) { server, app, sync, serviceRunning ->
                _uiState.value = _uiState.value.copy(
                    isConnected = serviceRunning, // 使用服务运行状态
                    autoSyncEnabled = app.autoSync,
                    syncStatus = sync,
                    lastSyncTime = server.lastSyncTime
                )
            }.collect()
        }
    }
    
    /**
     * 加载剪贴板历史
     */
    private fun loadClipboardHistory() {
        viewModelScope.launch {
            // 监听设置变化，当历史数量设置变化时重新加载
            appSettings
                .map { it.clipboardHistoryCount }
                .distinctUntilChanged()
                .collect { count ->
                    try {
                        val items = clipboardRepository.getRecentItems(count)
                        _clipboardItems.value = items
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            error = "加载历史失败: ${e.message}"
                        )
                    }
                }
        }
    }
    
    /**
     * 开始实时更新
     */
    private fun startRealtimeUpdates() {
        // 每5秒更新一次列表
        viewModelScope.launch {
            while (true) {
                delay(5000)
                try {
                    val settings = settingsRepository.appSettingsFlow.first()
                    val items = clipboardRepository.getRecentItems(settings.clipboardHistoryCount)
                    _clipboardItems.value = items
                } catch (e: Exception) {
                    // 静默忽略错误
                }
            }
        }
    }
    
    /**
     * 刷新剪贴板列表（从本地数据库重新加载）
     */
    fun refreshClipboardList() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val settings = settingsRepository.appSettingsFlow.first()
                val items = clipboardRepository.getRecentItems(settings.clipboardHistoryCount)
                _clipboardItems.value = items
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastSyncMessage = "列表已刷新"
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "刷新失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 手动同步到服务器
     */
    fun performSync() {
        viewModelScope.launch {
            try {
                _syncStatus.value = SyncStatus.SYNCING
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // 上传未同步的项目
                val unsyncedItems = clipboardRepository.getUnsyncedItems()
                for (item in unsyncedItems) {
                    val result = clipboardRepository.uploadToServer(item)
                    result.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            error = "上传失败: ${e.message}"
                        )
                        return@launch
                    }
                }
                
                // 从服务器获取最新内容
                val result = clipboardRepository.fetchFromServer()
                result.onSuccess { item ->
                    _syncStatus.value = SyncStatus.CONNECTED
                    
                    // 刷新列表，去重逻辑在getRecentItems中处理
                    val settings = settingsRepository.appSettingsFlow.first()
                    val updatedItems = clipboardRepository.getRecentItems(settings.clipboardHistoryCount)
                    _clipboardItems.value = updatedItems
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        lastSyncMessage = if (item != null) "同步成功" else "暂无新内容"
                    )
                }.onFailure { e ->
                    _syncStatus.value = SyncStatus.ERROR
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "同步失败: ${e.message}"
                    )
                }
                
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.ERROR
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "同步出错: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 测试连接
     */
    fun testConnection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val result = clipboardRepository.testConnection()
                result.onSuccess { isConnected ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        lastSyncMessage = if (isConnected) "连接成功" else "连接失败"
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "连接测试失败: ${e.message}"
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "连接测试出错: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 复制内容到系统剪贴板（不保存到数据库，避免重复）
     */
    fun copyToClipboard(item: ClipboardItem) {
        try {
            // 检查内容大小，如果太大则裁剪
            var processedItem = item
            if (item.type == ClipboardType.TEXT && ContentLimiter.isContentTooLargeForClipboard(item.content)) {
                Logger.w("MainViewModel", "检测到过大的文本内容，正在进行裁剪: ${item.content.length} 字符")
                val truncatedContent = ContentLimiter.truncateForClipboard(item.content)
                processedItem = item.copy(content = truncatedContent)
                Logger.d("MainViewModel", "裁剪后内容大小: ${processedItem.content.length} 字符")
            }
            
            Logger.d("MainViewModel", "用户复制剪贴板项目到系统剪贴板: id=${processedItem.id}, type=${processedItem.type}, content=${processedItem.content.take(50)}...")
            
            val context = getApplication<Application>().applicationContext
            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            
            when (processedItem.type) {
                ClipboardType.TEXT -> {
                    val clip = android.content.ClipData.newPlainText("SyncClipboard", processedItem.content)
                    clipboardManager.setPrimaryClip(clip)
                    Logger.d("MainViewModel", "文本内容已复制到剪贴板: id=${processedItem.id}")
                }
                ClipboardType.IMAGE -> {
                    // 对于图片，如果有本地路径，尝试复制图片
                    processedItem.localPath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val clip = android.content.ClipData.newUri(context.contentResolver, "SyncClipboard Image", uri)
                                clipboardManager.setPrimaryClip(clip)
                                Logger.d("MainViewModel", "图片内容已复制到剪贴板: id=${processedItem.id}, path=$path")
                            } catch (e: Exception) {
                                // 如果图片复制失败，降级为文本复制
                                val clip = android.content.ClipData.newPlainText("SyncClipboard", "图片: ${processedItem.fileName ?: "未知文件"}")
                                clipboardManager.setPrimaryClip(clip)
                                Logger.w("MainViewModel", "图片复制失败，降级为文本复制: id=${processedItem.id}", e)
                            }
                        } else {
                            // 文件不存在，复制文件名
                            val clip = android.content.ClipData.newPlainText("SyncClipboard", "图片: ${processedItem.fileName ?: "未知文件"}")
                            clipboardManager.setPrimaryClip(clip)
                            Logger.w("MainViewModel", "图片文件不存在，复制文件名: id=${processedItem.id}, path=$path")
                        }
                    } ?: run {
                        // 没有本地路径，复制文件名或内容
                        val clip = android.content.ClipData.newPlainText("SyncClipboard", "图片: ${processedItem.fileName ?: processedItem.content}")
                        clipboardManager.setPrimaryClip(clip)
                        Logger.d("MainViewModel", "复制图片文件名或内容: id=${processedItem.id}")
                    }
                }
                ClipboardType.FILE -> {
                    // 文件类型，复制文件名
                    val clip = android.content.ClipData.newPlainText("SyncClipboard", "文件: ${processedItem.fileName ?: processedItem.content}")
                    clipboardManager.setPrimaryClip(clip)
                    Logger.d("MainViewModel", "文件名已复制到剪贴板: id=${processedItem.id}, fileName=${processedItem.fileName}")
                }
            }
            
            _uiState.value = _uiState.value.copy(
                lastSyncMessage = "已复制到剪贴板"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "复制失败: ${e.message}"
            )
            Logger.e("MainViewModel", "复制到剪贴板失败: id=${item.id}", e)
        }
    }
    
    /**
     * 删除剪贴板项目
     */
    fun deleteClipboardItem(item: ClipboardItem) {
        viewModelScope.launch {
            try {
                Logger.d("MainViewModel", "用户删除剪贴板项目: id=${item.id}, type=${item.type}, content=${item.uiContent.take(50)}...")
                val result = clipboardRepository.deleteItem(item)
                result.onSuccess {
                    // 删除成功后刷新列表，去重逻辑在getRecentItems中处理
                    val settings = settingsRepository.appSettingsFlow.first()
                    val updatedItems = clipboardRepository.getRecentItems(settings.clipboardHistoryCount)
                    _clipboardItems.value = updatedItems
                    
                    _uiState.value = _uiState.value.copy(
                        lastSyncMessage = "删除成功"
                    )
                    Logger.d("MainViewModel", "剪贴板项目删除成功: id=${item.id}")
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = "删除失败: ${e.message}"
                    )
                    Logger.e("MainViewModel", "剪贴板项目删除失败: id=${item.id}", e)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "删除出错: ${e.message}"
                )
                Logger.e("MainViewModel", "剪贴板项目删除出错: id=${item.id}", e)
            }
        }
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 清除提示信息
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(lastSyncMessage = null)
    }
    
    /**
     * 启动/停止同步服务
     */
    fun toggleSyncService(start: Boolean) {
        viewModelScope.launch {
            try {
                if (start) {
                    ClipboardSyncService.startService(getApplication())
                    // 等待一下让服务启动
                    delay(500)
                    // 检查服务是否成功启动
                    val actuallyRunning = ClipboardSyncService.isServiceRunning(getApplication())
                    _isServiceRunning.value = actuallyRunning
                    
                    if (actuallyRunning) {
                        _uiState.value = _uiState.value.copy(
                            lastSyncMessage = "已启动同步服务"
                        )
                        // 测试连接
                        delay(1000) // 等待服务初始化完成
                        testConnection()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "服务启动失败"
                        )
                    }
                } else {
                    ClipboardSyncService.stopService(getApplication())
                    // 等待一下让服务停止
                    delay(500)
                    // 检查服务是否成功停止
                    val actuallyStopped = !ClipboardSyncService.isServiceRunning(getApplication())
                    _isServiceRunning.value = !actuallyStopped
                    
                    if (actuallyStopped) {
                        _uiState.value = _uiState.value.copy(
                            lastSyncMessage = "已停止同步服务"
                        )
                        _syncStatus.value = SyncStatus.IDLE
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "服务停止失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "服务操作失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 执行数据清理
     */
    private fun performDataCleanup() {
        viewModelScope.launch {
            try {
                Logger.d("MainViewModel", "开始执行初始化数据清理...")
                
                // 延迟一下，让其他初始化完成
                delay(2000)
                
                clipboardRepository.forceCleanupExcessData()
                
                Logger.d("MainViewModel", "初始化数据清理完成")
            } catch (e: Exception) {
                Logger.e("MainViewModel", "数据清理时出错", e)
            }
        }
    }
    
    /**
     * 下载文件
     */
    fun downloadFile(item: ClipboardItem, context: android.content.Context) {
        viewModelScope.launch {
            try {
                Logger.d("MainViewModel", "用户请求下载文件: id=${item.id}, type=${item.type}, fileName=${item.fileName}")
                
                // 检查是否为文件类型
                if (item.type != ClipboardType.FILE && item.type != ClipboardType.IMAGE) {
                    Logger.w("MainViewModel", "只能下载文件类型的内容，当前类型: ${item.type}")
                    _uiState.value = _uiState.value.copy(error = "只能下载文件类型的内容")
                    return@launch
                }
                
                // 获取应用设置
                val settings = settingsRepository.appSettingsFlow.first()
                Logger.d("MainViewModel", "当前设置: downloadLocation=${settings.downloadLocation}, autoSaveFiles=${settings.autoSaveFiles}")
                
                // 检查是否有设置下载位置
                val downloadLocation = if (settings.downloadLocation.isNotEmpty()) {
                    settings.downloadLocation
                } else {
                    // 使用默认下载目录
                    val defaultDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                    Logger.d("MainViewModel", "使用默认下载目录: $defaultDir")
                    defaultDir
                }
                
                Logger.d("MainViewModel", "下载位置: $downloadLocation")
                
                // 构造目标文件路径
                val fileName = item.fileName ?: "clipboard_file_${System.currentTimeMillis()}"
                val targetPath = if (downloadLocation.startsWith("content://")) {
                    // 如果是URI格式，直接使用
                    Logger.d("MainViewModel", "使用URI格式下载位置: $downloadLocation")
                    downloadLocation
                } else {
                    // 如果是普通路径格式，构造完整路径
                    val fullPath = "$downloadLocation/$fileName"
                    Logger.d("MainViewModel", "使用文件路径下载位置: $fullPath")
                    fullPath
                }
                
                Logger.d("MainViewModel", "目标文件路径: $targetPath")
                
                // 调用Repository下载文件
                Logger.d("MainViewModel", "调用Repository下载文件")
                val result = clipboardRepository.downloadFile(item, targetPath)
                result.onSuccess { path ->
                    Logger.d("MainViewModel", "文件下载成功: $path")
                    _uiState.value = _uiState.value.copy(lastSyncMessage = "文件已下载到: $path")
                }.onFailure { e ->
                    Logger.e("MainViewModel", "文件下载失败", e)
                    _uiState.value = _uiState.value.copy(error = "下载失败: ${e.message}")
                }
                
            } catch (e: Exception) {
                Logger.e("MainViewModel", "下载文件时出错", e)
                _uiState.value = _uiState.value.copy(error = "下载失败: ${e.message}")
            }
        }
    }
}

/**
 * 主界面UI状态
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastSyncTime: Long = 0L,
    val lastSyncMessage: String? = null,
    val error: String? = null
)