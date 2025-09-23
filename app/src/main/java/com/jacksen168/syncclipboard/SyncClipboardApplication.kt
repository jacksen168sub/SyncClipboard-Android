package com.jacksen168.syncclipboard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.jacksen168.syncclipboard.data.repository.SettingsRepository
import com.jacksen168.syncclipboard.data.repository.ClipboardRepository
import com.jacksen168.syncclipboard.data.repository.UpdateRepository
import com.jacksen168.syncclipboard.work.SyncWorkManagerFactory

class SyncClipboardApplication : Application(), Configuration.Provider {
    
    // 延迟初始化仓库
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }
    
    val clipboardRepository: ClipboardRepository by lazy {
        ClipboardRepository(applicationContext, settingsRepository)
    }
    
    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(applicationContext)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 创建通知渠道
        createNotificationChannels()
        
        // 安全地初始化WorkManager
        try {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this, workManagerConfiguration)
            }
        } catch (e: Exception) {
            // WorkManager已经初始化，使用默认配置
        }
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkManagerFactory(settingsRepository))
            .build()
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建同步服务通知渠道
            val syncChannel = NotificationChannel(
                CHANNEL_ID_SYNC_SERVICE,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableVibration(false) // 禁用震动
                setSound(null, null) // 禁用声音
            }
            
            // 创建同步状态通知渠道（用于显示同步结果）
            val syncStatusChannel = NotificationChannel(
                CHANNEL_ID_SYNC_STATUS,
                "同步状态",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "显示剪贴板同步结果通知"
                setShowBadge(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannels(listOf(syncChannel, syncStatusChannel))
        }
    }
    
    companion object {
        const val CHANNEL_ID_SYNC_SERVICE = "sync_service_channel"
        const val CHANNEL_ID_SYNC_STATUS = "sync_status_channel"
    }
}