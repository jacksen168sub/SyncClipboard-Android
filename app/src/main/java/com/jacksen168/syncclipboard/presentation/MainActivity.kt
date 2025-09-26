package com.jacksen168.syncclipboard.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.data.model.AppSettings
import com.jacksen168.syncclipboard.SyncClipboardApplication
import com.jacksen168.syncclipboard.presentation.component.PermissionRequestDialog
import com.jacksen168.syncclipboard.presentation.component.UpdateDialog
import com.jacksen168.syncclipboard.presentation.navigation.SyncClipboardNavigation
import com.jacksen168.syncclipboard.presentation.theme.SyncClipboardTheme
import com.jacksen168.syncclipboard.presentation.viewmodel.UpdateViewModel
import com.jacksen168.syncclipboard.presentation.viewmodel.SettingsViewModel
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
    
    // 文件夹选择启动器
    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 保存选择的文件夹URI
            saveDownloadLocation(selectedUri)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 应用隐藏在多任务页面设置（必须在最早期设置）
        applyHideInRecentsSettings()
        
        // 检查并恢复URI权限
        checkAndRestoreUriPermissions()
        
        // 检查并请求通知权限（Android 13+）
        checkNotificationPermission()
        
        // 检查设置并自动启动服务
        autoStartServiceIfEnabled()
        
        // 清理图片缓存（异步执行，不阻塞启动）
        cleanupImageCacheOnStartup()
        
        // 检查应用更新（异步执行，不阻塞启动）
        checkForUpdateOnStartup()
        
        setContent {
            SyncClipboardTheme {
                // 权限状态
                var showPermissionDialog by remember { mutableStateOf(false) }
                var permissionStatus by remember { 
                    mutableStateOf(PermissionManager.checkAllPermissions(this@MainActivity)) 
                }
                
                // 更新检查
                val updateViewModel = remember { UpdateViewModel(this@MainActivity) }
                val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsState()
                val updateInfo by updateViewModel.updateInfo.collectAsState()
                
                // SettingsViewModel
                val settingsViewModel: SettingsViewModel = viewModel()
                
                // 检查权限
                LaunchedEffect(Unit) {
                    permissionStatus = PermissionManager.checkAllPermissions(this@MainActivity)
                    if (!permissionStatus.allGranted) {
                        showPermissionDialog = true
                    }
                    
                    // 启动时检查更新
                    updateViewModel.checkForUpdateSilently()
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SyncClipboardNavigation(
                        onDownloadLocationRequest = {
                            // 启动文件夹选择器
                            selectFolderLauncher.launch(null)
                        }
                    )
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
                
                // 更新对话框
                updateInfo?.let { info ->
                    if (showUpdateDialog && info.hasUpdate) {
                        UpdateDialog(
                            updateInfo = info,
                            onUpdateClick = updateViewModel::goToUpdate,
                            onLaterClick = updateViewModel::remindLater,
                            onDismiss = updateViewModel::dismissUpdateDialog
                        )
                    }
                }
            }
        }
    }
    
    /**
     * 检查并恢复URI权限
     */
    private fun checkAndRestoreUriPermissions() {
        lifecycleScope.launch {
            try {
                val settingsRepository = (application as SyncClipboardApplication).settingsRepository
                val appSettings = settingsRepository.appSettingsFlow.first()
                
                // 检查下载位置是否为URI格式
                if (appSettings.downloadLocation.startsWith("content://")) {
                    val clipboardRepository = (application as SyncClipboardApplication).clipboardRepository
                    if (!clipboardRepository.checkAndRestoreUriPermission(appSettings.downloadLocation)) {
                        Log.w(TAG, "下载位置URI权限无效，可能需要重新选择文件夹")
                        // 可以在这里显示一个提示，让用户重新选择下载位置
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查URI权限时出错", e)
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
    
    /**
     * 启动时检查应用更新
     */
    private fun checkForUpdateOnStartup() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "开始检查应用更新...")
                // 延迟一小会再检查，避免阻塞应用启动
                kotlinx.coroutines.delay(2000)
                
                val updateRepository = (application as SyncClipboardApplication).updateRepository
                val updateInfo = updateRepository.checkForUpdate()
                
                if (updateInfo.hasUpdate) {
                    Log.i(TAG, "发现新版本: ${updateInfo.latestVersion}")
                    // 启动时的更新检查由UpdateViewModel处理
                } else {
                    Log.d(TAG, "已是最新版本: ${updateInfo.currentVersion}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查应用更新时出错", e)
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
    
    /**
     * 保存下载位置
     */
    private fun saveDownloadLocation(uri: Uri) {
        // 获取持久化权限
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.d(TAG, "成功获取持久化URI权限: $uri")
        } catch (e: SecurityException) {
            Log.e(TAG, "获取持久化URI权限失败", e)
        }
        
        // 保存URI到应用设置
        lifecycleScope.launch {
            try {
                val settingsRepository = (application as SyncClipboardApplication).settingsRepository
                val appSettings = settingsRepository.appSettingsFlow.first()
                
                // 获取可读的路径描述
                val readablePath = getReadablePathFromUri(uri)
                
                val updatedSettings = appSettings.copy(downloadLocation = uri.toString())
                settingsRepository.saveAppSettings(updatedSettings)
                Log.d(TAG, "下载位置已保存: $uri (可读路径: $readablePath)")
                
                // 验证设置是否正确保存
                kotlinx.coroutines.delay(100) // 等待一下确保保存完成
                val savedSettings = settingsRepository.appSettingsFlow.first()
                if (savedSettings.downloadLocation == uri.toString()) {
                    Log.d(TAG, "设置保存验证成功")
                } else {
                    Log.e(TAG, "设置保存验证失败，期望: ${uri.toString()}，实际: ${savedSettings.downloadLocation}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存下载位置时出错", e)
            }
        }
    }
    
    /**
     * 从URI获取可读的路径描述
     */
    private fun getReadablePathFromUri(uri: Uri): String {
        return try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                // 获取文档树的根路径
                val docId = DocumentsContract.getTreeDocumentId(uri)
                // 解码文档ID
                val decodedId = java.net.URLDecoder.decode(docId, "UTF-8")
                // 尝试获取更友好的路径表示
                when {
                    decodedId.startsWith("primary:") -> {
                        "/存储/下载"
                    }
                    decodedId.startsWith("raw:") -> {
                        decodedId.substring(4)
                    }
                    else -> {
                        "已选择文件夹"
                    }
                }
            } else {
                uri.toString()
            }
        } catch (e: Exception) {
            uri.toString()
        }
    }
}