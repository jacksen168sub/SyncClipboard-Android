package com.jacksen168.syncclipboard.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.data.model.AppSettings
import com.jacksen168.syncclipboard.SyncClipboardApplication
import com.jacksen168.syncclipboard.presentation.component.PermissionRequestDialog
import com.jacksen168.syncclipboard.presentation.navigation.SyncClipboardNavigation
import com.jacksen168.syncclipboard.presentation.theme.SyncClipboardTheme
import com.jacksen168.syncclipboard.service.ClipboardSyncService
import com.jacksen168.syncclipboard.util.PermissionManager

/**
 * 主活动
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限被授予，可以显示通知
        } else {
            // 权限被拒绝，可以显示解释信息
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 应用隐藏在多任务页面设置（必须在最早期设置）
        applyHideInRecentsSettings()
        
        // 检查并请求通知权限（Android 13+）
        checkNotificationPermission()
        
        // 检查设置并自动启动服务
        autoStartServiceIfEnabled()
        
        // 清理图片缓存（异步执行，不阻塞启动）
        cleanupImageCacheOnStartup()
        
        setContent {
            SyncClipboardTheme {
                // 权限状态
                var showPermissionDialog by remember { mutableStateOf(false) }
                var permissionStatus by remember { 
                    mutableStateOf(PermissionManager.checkAllPermissions(this@MainActivity)) 
                }
                
                // 检查权限
                LaunchedEffect(Unit) {
                    permissionStatus = PermissionManager.checkAllPermissions(this@MainActivity)
                    if (!permissionStatus.allGranted) {
                        showPermissionDialog = true
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SyncClipboardNavigation()
                }
                
                // 权限请求对话框
                if (showPermissionDialog) {
                    PermissionRequestDialog(
                        onDismiss = { showPermissionDialog = false },
                        onAllPermissionsGranted = { 
                            showPermissionDialog = false
                            permissionStatus = PermissionManager.checkAllPermissions(this@MainActivity)
                        }
                    )
                }
            }
        }
    }
    
    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    /**
     * 应用隐藏在多任务页面设置
     */
    private fun applyHideInRecentsSettings() {
        lifecycleScope.launch {
            try {
                val settingsRepository = (application as SyncClipboardApplication).settingsRepository
                
                // 先获取初始设置并立即应用
                val initialSettings = settingsRepository.appSettingsFlow.first()
                applyHideFlags(initialSettings.hideInRecents)
                
                // 然后监听设置变化
                settingsRepository.appSettingsFlow.collect { appSettings ->
                    applyHideFlags(appSettings.hideInRecents)
                }
            } catch (e: Exception) {
                Log.e(TAG, "应用隐藏设置时出错", e)
            }
        }
    }
    
    /**
     * 应用隐藏标志
     */
    private fun applyHideFlags(hideInRecents: Boolean) {
        try {
            if (hideInRecents) {
                // 使用多种方式确保从多任务页面隐藏
                // 注意：不使用 FLAG_SECURE，因为它会让多任务页面显示白屏
                
                // 为 Intent 添加标志
                intent?.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                
                // 在任务管理器中隐藏
                hideFromTaskManager()
                
                Log.d(TAG, "应用已从多任务页面隐藏")
            } else {
                // 恢复显示在任务管理器中
                showInTaskManager()
                
                Log.d(TAG, "应用可在多任务页面显示")
            }
        } catch (e: Exception) {
            Log.e(TAG, "应用隐藏标志时出错", e)
        }
    }
    
    /**
     * 从任务管理器中隐藏
     */
    private fun hideFromTaskManager() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val appTasks = activityManager.appTasks
            for (task in appTasks) {
                try {
                    task.setExcludeFromRecents(true)
                    Log.d(TAG, "任务已隐藏: ${task.taskInfo?.taskId}")
                } catch (e: Exception) {
                    Log.w(TAG, "设置任务隐藏失败", e)
                }
            }
            
            // 尝试设置任务描述为空，进一步隐藏
            val taskDescription = android.app.ActivityManager.TaskDescription(
                "", // 空标签
                null, // 无图标
                0 // 透明颜色
            )
            setTaskDescription(taskDescription)
            
        } catch (e: Exception) {
            Log.w(TAG, "从任务管理器隐藏失败", e)
        }
    }
    
    /**
     * 在任务管理器中显示
     */
    private fun showInTaskManager() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val appTasks = activityManager.appTasks
            for (task in appTasks) {
                try {
                    task.setExcludeFromRecents(false)
                } catch (e: Exception) {
                    Log.w(TAG, "恢复任务显示失败", e)
                }
            }
            
            // 恢复正常的任务描述
            val taskDescription = android.app.ActivityManager.TaskDescription(
                getString(R.string.app_name),
                null,
                0 // 使用默认颜色
            )
            setTaskDescription(taskDescription)
            
        } catch (e: Exception) {
            Log.w(TAG, "在任务管理器显示失败", e)
        }
    }
    
    /**
     * 检查设置并自动启动服务
     */
    private fun autoStartServiceIfEnabled() {
        lifecycleScope.launch {
            try {
                val settingsRepository = (application as SyncClipboardApplication).settingsRepository
                val appSettings = settingsRepository.appSettingsFlow.first()
                
                Log.d(TAG, "检查应用启动时服务设置: syncOnBoot=${appSettings.syncOnBoot}")
                
                // 检查是否启用应用启动时自动开启服务
                if (appSettings.syncOnBoot) {
                    // 检查服务是否已经在运行
                    val isServiceRunning = ClipboardSyncService.isServiceRunning(this@MainActivity)

                    if (!isServiceRunning) {
                        Log.d(TAG, "启用应用启动时自动开启服务，正在启动剪贴板同步服务")
                        ClipboardSyncService.startService(this@MainActivity)
                        
                        // 验证服务是否成功启动
                        kotlinx.coroutines.delay(1000)
                        val actuallyRunning = ClipboardSyncService.isServiceRunning(this@MainActivity)
                        if (actuallyRunning) {
                            Log.i(TAG, "服务启动成功")
                        } else {
                            Log.w(TAG, "服务启动失败，可能需要用户手动授权相关权限")
                        }
                    } else {
                        Log.d(TAG, "剪贴板同步服务已在运行")
                    }
                } else {
                    Log.d(TAG, "未启用应用启动时自动开启服务")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动服务时出错", e)
            }
        }
    }
    
    /**
     * 启动时清理图片缓存
     */
    private fun cleanupImageCacheOnStartup() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始清理图片缓存...")
                val clipboardRepository = (application as SyncClipboardApplication).clipboardRepository
                clipboardRepository.cleanupImageCache()
                Log.d(TAG, "图片缓存清理完成")
            } catch (e: Exception) {
                Log.e(TAG, "清理图片缓存时出错", e)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 在 Activity 恢复时重新应用隐藏设置
        reapplyHideInRecentsIfNeeded()
    }
    
    override fun onPause() {
        super.onPause()
        // 在 Activity 暂停时确保隐藏设置生效
        reapplyHideInRecentsIfNeeded()
    }
    
    /**
     * 重新应用隐藏设置（如果需要）
     */
    private fun reapplyHideInRecentsIfNeeded() {
        lifecycleScope.launch {
            try {
                val settingsRepository = (application as SyncClipboardApplication).settingsRepository
                val appSettings = settingsRepository.appSettingsFlow.first()
                if (appSettings.hideInRecents) {
                    applyHideFlags(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "重新应用隐藏设置失败", e)
            }
        }
    }
}