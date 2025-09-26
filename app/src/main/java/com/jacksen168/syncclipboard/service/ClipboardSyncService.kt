package com.jacksen168.syncclipboard.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.jacksen168.syncclipboard.data.model.AppSettings
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
    
    // 自动同步作业
    private var autoSyncJob: Job? = null
    
    // 锁屏状态相关
    private var isScreenLocked = false
    private var pendingClipboardItem: ClipboardItem? = null
    private var screenStateReceiver: BroadcastReceiver? = null
    
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
        
        // 初始化锁屏状态
        initScreenState()
        
        // 注册屏幕状态监听
        registerScreenStateReceiver()
        
        // 监听剪贴板变化
        startClipboardMonitoring()
        
        // 监听设置变化
        observeSettings()
        
        // 启动前台通知（异步创建）
        serviceScope.launch {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        }
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
                // 确保通知正常显示（异步创建）
                serviceScope.launch {
                    try {
                        val notification = createNotification()
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (e: Exception) {
                        Log.e(TAG, "创建前台通知失败", e)
                    }
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
        
        // 注销屏幕状态监听
        unregisterScreenStateReceiver()
        
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
                
                // 更新常驻通知
                updatePersistentNotification(appSettings.showNotifications)
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
            // 检查是否在锁屏状态
            if (isScreenLocked) {
                Log.d(TAG, "设备处于锁屏状态，缓存内容等待解锁后处理")
                pendingClipboardItem = item
                return
            }
            
            // 检查是否需要自动保存文件(需要自动下载保存图片,下方判断加入” || item.type == ClipboardType.IMAGE“。不过我不想自动下载保存照片,以后有人需要该功能再加个开关好了)
            if ((item.type == ClipboardType.FILE) &&
                item.source == ClipboardSource.REMOTE) {
                val settings = settingsRepository.appSettingsFlow.first()
                Log.d(TAG, "检查自动保存设置: ${settings.autoSaveFiles}")
                if (settings.autoSaveFiles) {
                    Log.d(TAG, "自动保存文件: ${item.fileName}")
                    autoSaveFile(item)
                }
            }
            
            // 非锁屏状态，正常处理
            writeToClipboard(item)
            
        } catch (e: Exception) {
            Log.e(TAG, "更新剪贴板时出错", e)
        }
    }
    
    /**
     * 自动保存文件
     */
    private suspend fun autoSaveFile(item: ClipboardItem) {
        try {
            Log.d(TAG, "开始自动保存文件: id=${item.id}, type=${item.type}, fileName=${item.fileName}")

            val settings = settingsRepository.appSettingsFlow.first()
            
            // 检查是否有设置下载位置
            val downloadLocation = if (settings.downloadLocation.isNotEmpty()) {
                settings.downloadLocation
            } else {
                // 使用默认下载目录
                val defaultDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                Log.d(TAG, "使用默认下载目录: $defaultDir")
                defaultDir
            }
            
            Log.d(TAG, "自动保存位置: $downloadLocation")
            
            // 检查URI权限（如果是URI格式）
            if (downloadLocation.startsWith("content://")) {
                if (!clipboardRepository.checkAndRestoreUriPermission(downloadLocation)) {
                    Log.w(TAG, "URI权限无效，使用默认下载目录")
                    // 使用默认下载目录作为回退方案
                    val defaultDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                    val fileName = item.fileName ?: "clipboard_file_${System.currentTimeMillis()}"
                    val targetPath = "$defaultDir/$fileName"
                    Log.d(TAG, "使用默认下载目录: $targetPath")
                    
                    // 调用Repository下载文件
                    val result = clipboardRepository.downloadFile(item, targetPath)
                    result.onSuccess { path -> 
                        Log.d(TAG, "文件自动保存成功: $path")
                        showSyncNotification("文件已自动保存到: ${fileName}")
                    }.onFailure { e -> 
                        Log.e(TAG, "文件自动保存失败", e)
                        showSyncNotification("文件自动保存失败: ${e.message}")
                    }
                    return
                }
            }
            
            // 构造目标文件路径
            val fileName = item.fileName ?: "clipboard_file_${System.currentTimeMillis()}"
            val targetPath = if (downloadLocation.startsWith("content://")) {
                // 如果是URI格式，直接使用
                Log.d(TAG, "使用URI格式下载位置: $downloadLocation")
                downloadLocation
            } else {
                // 如果是普通路径格式，构造完整路径
                val fullPath = "$downloadLocation/$fileName"
                Log.d(TAG, "使用文件路径下载位置: $fullPath")
                fullPath
            }
            
            Log.d(TAG, "自动保存目标路径: $targetPath")
            
            // 调用Repository下载文件
            Log.d(TAG, "调用Repository自动保存文件")
            val result = clipboardRepository.downloadFile(item, targetPath)
            result.onSuccess { path -> 
                Log.d(TAG, "文件自动保存成功: $path")
                showSyncNotification("文件已自动保存到: ${fileName}")
            }.onFailure { e -> 
                Log.e(TAG, "文件自动保存失败", e)
                showSyncNotification("文件自动保存失败: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "自动保存文件时出错", e)
            showSyncNotification("文件自动保存出错: ${e.message}")
        }
    }
    
    /**
     * 将内容写入剪贴板
     */
    private suspend fun writeToClipboard(item: ClipboardItem) {
        try {
            // 暂时停止监听剪贴板变化，防止循环
            clipboardManager.stopListening()
            
            // 将服务器内容设置到剪贴板
            when (item.type) {
                ClipboardType.TEXT -> {
                    clipboardManager.setClipboardText(item.content)
                    Log.d(TAG, "已将文本内容写入剪贴板: ${item.content.take(20)}...")
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
                            Log.d(TAG, "已将图片内容写入剪贴板: $path")
                        } else {
                            Log.w(TAG, "图片文件不存在: $path")
                        }
                    }
                }
                ClipboardType.FILE -> {
                    // 文件类型的处理
                    Log.d(TAG, "文件类型不写入剪贴板")
                    // 清理剪贴板，防止文本内容顶替服务端文件
                    clipboardManager.clearClipboard()
                }
            }
            
            // 延迟后重新开始监听（确保剪贴板更新完成）
            delay(2000)
            clipboardManager.startListening()
            
        } catch (e: Exception) {
            Log.e(TAG, "写入剪贴板时出错", e)
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
    private suspend fun createNotification(showPersistent: Boolean = true): Notification {
        // 获取前台服务保活设置
        val appSettings = settingsRepository.appSettingsFlow.first()
        val keepaliveEnabled = appSettings.foregroundServiceKeepalive
        
        // 点击通知打开应用的意图
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, SyncClipboardApplication.CHANNEL_ID_SYNC_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                when {
                    !showPersistent -> "同步服务已禁用通知(重启生效)"
                    keepaliveEnabled -> "剪贴板同步服务正在运行（保活模式）"
                    else -> getString(R.string.notification_service_running)
                }
            )
            .setSmallIcon(android.R.drawable.ic_menu_share) // 使用系统图标
            .setContentIntent(mainPendingIntent) // 点击通知打开应用
            .setOngoing(true) // 设置为持续通知，无法被用户滑动删除
            .setAutoCancel(false) // 禁止自动取消
            .setPriority(
                when {
                    !showPersistent -> NotificationCompat.PRIORITY_MIN
                    keepaliveEnabled -> NotificationCompat.PRIORITY_LOW // 保活模式使用较低优先级但可见
                    else -> NotificationCompat.PRIORITY_LOW
                }
            )
            
        // 如果启用保活模式，增强通知的持续性
        if (keepaliveEnabled) {
            builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
            
        if (showPersistent) {
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
            
            builder.addAction(
                android.R.drawable.ic_menu_rotate,
                getString(R.string.manual_sync),
                syncPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_serve),
                stopPendingIntent
            )
        }
        
        return builder.build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        serviceScope.launch {
            val notification = createNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * 更新常驻通知
     */
    private fun updatePersistentNotification(showPersistent: Boolean) {
        serviceScope.launch {
            val notification = createNotification(showPersistent)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "常驻通知设置已更新: $showPersistent")
        }
    }
    
    /**
     * 显示同步通知
     */
    private suspend fun showSyncNotification(message: String) {
        try {
            // 检查是否启用同步状态通知（这里不影响常驻通知）
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
    
    /**
     * 初始化屏幕状态
     */
    private fun initScreenState() {
        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            isScreenLocked = keyguardManager.isKeyguardLocked
            Log.d(TAG, "初始屏幕状态: ${if (isScreenLocked) "锁定" else "解锁"}")
        } catch (e: Exception) {
            Log.e(TAG, "获取屏幕状态失败", e)
            isScreenLocked = false
        }
    }
    
    /**
     * 注册屏幕状态监听
     */
    private fun registerScreenStateReceiver() {
        try {
            screenStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            Log.d(TAG, "屏幕关闭")
                            isScreenLocked = true
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            Log.d(TAG, "屏幕点亮")
                            // 屏幕点亮不一定解锁，需要等待USER_PRESENT
                        }
                        Intent.ACTION_USER_PRESENT -> {
                            Log.d(TAG, "用户解锁")
                            isScreenLocked = false
                            // 处理解锁后的逻辑
                            handleScreenUnlocked()
                        }
                    }
                }
            }
            
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "屏幕状态监听已注册")
        } catch (e: Exception) {
            Log.e(TAG, "注册屏幕状态监听失败", e)
        }
    }
    
    /**
     * 注销屏幕状态监听
     */
    private fun unregisterScreenStateReceiver() {
        try {
            screenStateReceiver?.let {
                unregisterReceiver(it)
                screenStateReceiver = null
                Log.d(TAG, "屏幕状态监听已注销")
            }
        } catch (e: Exception) {
            Log.e(TAG, "注销屏幕状态监听失败", e)
        }
    }
    
    /**
     * 处理屏幕解锁后的逻辑
     */
    private fun handleScreenUnlocked() {
        serviceScope.launch {
            try {
                // 检查解锁后自动重新写入设置
                val appSettings = settingsRepository.appSettingsFlow.first()
                if (!appSettings.rewriteAfterUnlock) {
                    Log.d(TAG, "解锁后自动重新写入功能已禁用")
                    pendingClipboardItem = null // 清空缓存
                    return@launch
                }
                
                // 如果有缓存的剪贴板内容，现在写入
                pendingClipboardItem?.let { item ->
                    Log.d(TAG, "解锁后处理缓存的剪贴板内容")
                    writeToClipboard(item)
                    pendingClipboardItem = null // 清空缓存
                    showSyncNotification("解锁后已自动写入: ${item.content.take(20)}...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理解锁后逻辑时出错", e)
            }
        }
    }
}