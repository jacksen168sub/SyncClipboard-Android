package com.jacksen168.syncclipboard.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.SyncClipboardApplication
import com.jacksen168.syncclipboard.data.model.ClipboardType
import com.jacksen168.syncclipboard.data.model.ClipboardSource
import com.jacksen168.syncclipboard.data.model.ClipboardItem
import com.jacksen168.syncclipboard.data.model.SyncStatus
import com.jacksen168.syncclipboard.data.repository.ClipboardRepository
import com.jacksen168.syncclipboard.data.repository.SettingsRepository
import com.jacksen168.syncclipboard.presentation.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay

/**
 * 剪贴板同步前台服务
 */
class ClipboardSyncService : Service() {
    
    private val binder = LocalBinder()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var clipboardManager: ClipboardManager
    
    // 服务协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 同步状态
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    // 自动同步作业
    private var autoSyncJob: Job? = null
    
    companion object {
        private const val TAG = "ClipboardSyncService"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_SERVICE = "com.jacksen168.syncclipboard.STOP_SERVICE"
        private const val ACTION_SYNC_NOW = "com.jacksen168.syncclipboard.SYNC_NOW"
        private const val ACTION_RESTART_SERVICE = "com.jacksen168.syncclipboard.RESTART_SERVICE"
        
        /**
         * 启动服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java)
            context.startForegroundService(intent)
        }
        
        /**
         * 停止服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java)
            context.stopService(intent)
        }
        
        /**
         * 重启服务
         */
        fun restartService(context: Context) {
            val intent = Intent(context, ClipboardSyncService::class.java).apply {
                action = ACTION_RESTART_SERVICE
            }
            context.startForegroundService(intent)
        }
        
        /**
         * 检查服务是否正在运行
         */
        fun isServiceRunning(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            return services.any { it.service.className == ClipboardSyncService::class.java.name }
        }
    }
    
    /**
     * 本地绑定器
     */
    inner class LocalBinder : Binder() {
        fun getService(): ClipboardSyncService = this@ClipboardSyncService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        
        // 初始化仓库
        settingsRepository = (application as SyncClipboardApplication).settingsRepository
        clipboardRepository = ClipboardRepository(this, settingsRepository)
        clipboardManager = ClipboardManager(this)
        
        // 监听剪贴板变化
        startClipboardMonitoring()
        
        // 监听设置变化
        observeSettings()
        
        // 启动前台通知
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动命令: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "接收到停止服务命令")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SYNC_NOW -> {
                Log.d(TAG, "接收到手动同步命令")
                performManualSync()
            }
            ACTION_RESTART_SERVICE -> {
                Log.d(TAG, "接收到重启服务命令")
                // 重新初始化服务
                restartService()
            }
            else -> {
                Log.d(TAG, "服务正常启动")
                // 确保通知正常显示
                try {
                    val notification = createNotification()
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.e(TAG, "创建前台通知失败", e)
                }
            }
        }
        
        return START_STICKY // 服务被杀死后会重启
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        
        // 停止剪贴板监听
        clipboardManager.stopListening()
        
        // 取消所有协程
        serviceScope.cancel()
        
        // 清理缓存
        clipboardManager.cleanupCache()
    }
    
    /**
     * 开始监听剪贴板变化
     */
    private fun startClipboardMonitoring() {
        clipboardManager.startListening()
        
        // 监听剪贴板变化并自动同步
        serviceScope.launch {
            clipboardManager.clipboardChangeFlow
                .filterNotNull()
                .collect { clipboardItem ->
                    Log.d(TAG, "检测到剪贴板变化: ${clipboardItem.type}")
                    
                    // 保存到本地数据库（明确标记为本地来源）
                    val savedItem = clipboardRepository.saveClipboardItem(
                        content = clipboardItem.content,
                        type = clipboardItem.type,
                        fileName = clipboardItem.fileName,
                        mimeType = clipboardItem.mimeType,
                        localPath = clipboardItem.localPath,
                        source = ClipboardSource.LOCAL // 明确标记为本地来源
                    )
                    
                    // 检查是否需要自动同步
                    val settings = settingsRepository.appSettingsFlow.first()
                    if (settings.autoSync) {
                        // 添加小延迟，确保本地保存完成
                        delay(500)
                        syncToServer(savedItem)
                    }
                }
        }
    }
    
    /**
     * 监听设置变化
     */
    private fun observeSettings() {
        serviceScope.launch {
            combine(
                settingsRepository.appSettingsFlow,
                settingsRepository.serverConfigFlow
            ) { appSettings, serverConfig ->
                Pair(appSettings, serverConfig)
            }.collect { (appSettings, serverConfig) ->
                // 更新自动同步
                updateAutoSync(appSettings.autoSync, appSettings.syncInterval)
                
                // 更新通知
                updateNotification()
            }
        }
    }
    
    /**
     * 更新自动同步设置
     */
    private fun updateAutoSync(enabled: Boolean, interval: Long) {
        autoSyncJob?.cancel()
        
        if (enabled) {
            autoSyncJob = serviceScope.launch {
                while (isActive) {
                    delay(interval)
                    performPeriodicSync()
                }
            }
            Log.d(TAG, "启用自动同步，间隔: ${interval}ms")
        } else {
            Log.d(TAG, "禁用自动同步")
        }
    }
    
    /**
     * 执行定期同步
     */
    private suspend fun performPeriodicSync() {
        try {
            _syncStatus.value = SyncStatus.SYNCING
            
            // 首先上传未同步的本地项目（优先级更高）
            val unsyncedItems = clipboardRepository.getUnsyncedItems()
            for (item in unsyncedItems) {
                clipboardRepository.uploadToServer(item)
            }
            
            // 然后从服务器获取最新内容
            val result = clipboardRepository.fetchFromServer()
            result.onSuccess { item ->
                if (item != null) {
                    // 检查内容是否与当前剪贴板不同，避免重复设置导致循环
                    val shouldUpdate = when (item.type) {
                        ClipboardType.TEXT -> {
                            val currentClipboard = clipboardManager.getCurrentClipboardText()
                            currentClipboard != item.content
                        }
                        ClipboardType.IMAGE -> {
                            // 对于图片类型，检查当前剪贴板内容
                            val currentContent = clipboardManager.getCurrentClipboardContent()
                            when {
                                currentContent == null -> true
                                currentContent.type != ClipboardType.IMAGE -> true
                                currentContent.content != item.content -> true // 比较哈希值
                                else -> false
                            }
                        }
                        else -> true
                    }
                    
                    if (shouldUpdate) {
                        // 只有当内容不同时才更新剪贴板
                        updateClipboardFromServer(item)
                        showSyncNotification("已从服务器同步: ${item.content.take(20)}...")
                    } else {
                        Log.d(TAG, "服务器内容与当前剪贴板相同，跳过更新")
                    }
                    
                    // 向UI发送刷新信号，通知页面去重已完成
                    sendBroadcast(Intent("com.jacksen168.syncclipboard.REFRESH_LIST"))
                } else {
                    // 没有新内容或内容已存在，但仍需通知UI刷新以反映去重结果
                    sendBroadcast(Intent("com.jacksen168.syncclipboard.REFRESH_LIST"))
                }
            }.onFailure { error ->
                Log.w(TAG, "定期同步失败", error)
            }
            
            _syncStatus.value = SyncStatus.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "定期同步出错", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }
    
    /**
     * 从服务器更新剪贴板内容（带防循环机制）
     */
    private suspend fun updateClipboardFromServer(item: ClipboardItem) {
        try {
            // 暂时停止监听剪贴板变化，避免循环
            clipboardManager.stopListening()
            
            // 将服务器内容设置到剪贴板
            when (item.type) {
                ClipboardType.TEXT -> {
                    clipboardManager.setClipboardText(item.content)
                }
                ClipboardType.IMAGE -> {
                    // 图片类型：如果有本地路径，则设置图片到剪贴板
                    item.localPath?.let { path ->
                        val file = java.io.File(path)
                        if (file.exists()) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                this@ClipboardSyncService,
                                "${packageName}.fileprovider",
                                file
                            )
                            clipboardManager.setClipboardImage(uri)
                        } else {
                            Log.w(TAG, "图片文件不存在: $path")
                        }
                    }
                }
                ClipboardType.FILE -> {
                    // 文件类型的处理
                    Log.d(TAG, "文件类型尚未完全实现")
                }
            }
            
            // 延迟后重新开始监听（确保剪贴板更新完成）
            delay(2000)
            clipboardManager.startListening()
            
        } catch (e: Exception) {
            Log.e(TAG, "更新剪贴板时出错", e)
            // 确保重新开始监听
            clipboardManager.startListening()
        }
    }
    
    /**
     * 执行手动同步
     */
    private fun performManualSync() {
        serviceScope.launch {
            try {
                _syncStatus.value = SyncStatus.SYNCING
                
                // 优先上传未同步的项目
                val unsyncedItems = clipboardRepository.getUnsyncedItems()
                for (item in unsyncedItems) {
                    clipboardRepository.uploadToServer(item)
                }
                
                // 然后从服务器获取最新内容
                val result = clipboardRepository.fetchFromServer()
                result.onSuccess { item ->
                    if (item != null) {
                        val shouldUpdate = when (item.type) {
                            ClipboardType.TEXT -> {
                                val currentClipboard = clipboardManager.getCurrentClipboardText()
                                currentClipboard != item.content
                            }
                            ClipboardType.IMAGE -> {
                                val currentContent = clipboardManager.getCurrentClipboardContent()
                                when {
                                    currentContent == null -> true
                                    currentContent.type != ClipboardType.IMAGE -> true
                                    currentContent.content != item.content -> true
                                    else -> false
                                }
                            }
                            else -> true
                        }
                        
                        if (shouldUpdate) {
                            updateClipboardFromServer(item)
                        }
                    }
                }
                
                _syncStatus.value = SyncStatus.CONNECTED
                showSyncNotification("手动同步完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "手动同步出错", e)
                _syncStatus.value = SyncStatus.ERROR
                showSyncNotification("同步失败: ${e.message}")
            }
        }
    }
    
    /**
     * 同步到服务器
     */
    private suspend fun syncToServer(item: com.jacksen168.syncclipboard.data.model.ClipboardItem) {
        try {
            _syncStatus.value = SyncStatus.SYNCING
            
            val result = clipboardRepository.uploadToServer(item)
            result.onSuccess {
                _syncStatus.value = SyncStatus.CONNECTED
                Log.d(TAG, "同步成功: ${item.type}")
                
                // 同步成功后发送刷新广播，确保UI立即反映去重结果
                sendBroadcast(Intent("com.jacksen168.syncclipboard.REFRESH_LIST"))
            }.onFailure { error ->
                _syncStatus.value = SyncStatus.ERROR
                Log.w(TAG, "同步失败", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步出错", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }
    
    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        // 点击通知打开应用的意图
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        // 停止服务的意图
        val stopIntent = Intent(this, ClipboardSyncService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        // 手动同步的意图
        val syncIntent = Intent(this, ClipboardSyncService::class.java).apply {
            action = ACTION_SYNC_NOW
        }
        val syncPendingIntent = PendingIntent.getService(
            this, 1, syncIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, SyncClipboardApplication.CHANNEL_ID_SYNC_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_service_running))
            .setSmallIcon(android.R.drawable.ic_menu_share) // 使用系统图标
            .setContentIntent(mainPendingIntent) // 点击通知打开应用
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                getString(R.string.refresh),
                syncPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel),
                stopPendingIntent
            )
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 显示同步通知
     */
    private suspend fun showSyncNotification(message: String) {
        try {
            // 检查是否启用通知
            val appSettings = settingsRepository.appSettingsFlow.first()
            if (!appSettings.showNotifications) {
                return
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 点击通知打开应用的意图
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, SyncClipboardApplication.CHANNEL_ID_SYNC_STATUS)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(mainPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID + 1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "显示同步通知时出错", e)
        }
    }
    
    /**
     * 测试连接
     */
    fun testConnection(): Flow<Result<Boolean>> = flow {
        emit(clipboardRepository.testConnection())
    }.flowOn(Dispatchers.IO)
    
    /**
     * 获取剪贴板历史
     */
    fun getClipboardHistory(): Flow<List<com.jacksen168.syncclipboard.data.model.ClipboardItem>> {
        return clipboardRepository.getLocalClipboardItems()
    }
    
    /**
     * 重启服务
     */
    private fun restartService() {
        try {
            Log.d(TAG, "正在重启服务...")
            
            // 停止当前监听
            clipboardManager.stopListening()
            
            // 取消当前的自动同步作业
            autoSyncJob?.cancel()
            
            // 重新初始化服务组件
            startClipboardMonitoring()
            observeSettings()
            
            // 更新通知
            updateNotification()
            
            Log.d(TAG, "服务重启完成")
        } catch (e: Exception) {
            Log.e(TAG, "重启服务失败", e)
        }
    }
}