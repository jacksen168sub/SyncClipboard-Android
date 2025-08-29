package com.jacksen168.syncclipboard.presentation

import android.Manifest
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
}