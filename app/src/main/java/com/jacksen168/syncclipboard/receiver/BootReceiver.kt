package com.jacksen168.syncclipboard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jacksen168.syncclipboard.data.repository.SettingsRepository
import com.jacksen168.syncclipboard.service.ClipboardSyncService
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启广播接收器
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d(TAG, "接收到广播: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                handleBootCompleted(context)
            }
        }
    }
    
    /**
     * 处理开机完成事件
     */
    private fun handleBootCompleted(context: Context) {
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            try {
                // 等待系统稳定
                delay(100)
                
                val settingsRepository = SettingsRepository(context)
                val appSettings = settingsRepository.appSettingsFlow.first()
                
                // 检查是否启用开机自启
                if (appSettings.syncOnBoot) {
                    Logger.d(TAG, "启用开机自启，启动剪贴板同步服务")
                    
                    // 检查服务是否已经运行
                    if (!ClipboardSyncService.isServiceRunning(context)) {
                        ClipboardSyncService.startService(context)
                        Logger.d(TAG, "开机自启服务启动成功")
                    } else {
                        Logger.d(TAG, "服务已经在运行，跳过启动")
                    }
                } else {
                    Logger.d(TAG, "未启用开机自启")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "处理开机完成事件时出错", e)
                
                // 如果出错，尝试再次启动
                try {
                    delay(10000) // 再等待10秒
                    ClipboardSyncService.startService(context)
                    Logger.d(TAG, "重试启动服务成功")
                } catch (retryException: Exception) {
                    Logger.e(TAG, "重试启动服务失败", retryException)
                }
            }
        }
    }
}