package com.jacksen168.syncclipboard.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jacksen168.syncclipboard.SyncClipboardApplication
import com.jacksen168.syncclipboard.data.model.AppSettings
import com.jacksen168.syncclipboard.data.model.ServerConfig
import com.jacksen168.syncclipboard.data.repository.ClipboardRepository
import com.jacksen168.syncclipboard.data.repository.SettingsRepository
import com.jacksen168.syncclipboard.service.ClipboardSyncService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 设置页面ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val settingsRepository = (application as SyncClipboardApplication).settingsRepository
    private val clipboardRepository = ClipboardRepository(application, settingsRepository)
    
    // UI状态
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 服务器配置
    private val _serverConfig = MutableStateFlow(ServerConfig())
    val serverConfig: StateFlow<ServerConfig> = _serverConfig.asStateFlow()
    
    // 应用设置
    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()
    
    init {
        loadSettings()
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.serverConfigFlow,
                settingsRepository.appSettingsFlow
            ) { server, app ->
                _serverConfig.value = server
                _appSettings.value = app
                Pair(server, app)
            }.collect()
        }
    }
    
    /**
     * 更新服务器URL
     */
    fun updateServerUrl(url: String) {
        _serverConfig.value = _serverConfig.value.copy(url = url.trim())
        saveServerConfigInternal()
    }
    
    /**
     * 更新用户名
     */
    fun updateUsername(username: String) {
        _serverConfig.value = _serverConfig.value.copy(username = username.trim())
        saveServerConfigInternal()
    }
    
    /**
     * 更新密码
     */
    fun updatePassword(password: String) {
        _serverConfig.value = _serverConfig.value.copy(password = password)
        saveServerConfigInternal()
    }
    
    /**
     * 内部保存服务器配置（实时保存）
     */
    private fun saveServerConfigInternal() {
        viewModelScope.launch {
            try {
                settingsRepository.saveServerConfig(_serverConfig.value)
            } catch (e: Exception) {
                // 静默失败，不影响用户体验
            }
        }
    }
    
    /**
     * 保存服务器配置
     */
    fun saveServerConfig() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // 验证输入
                if (_serverConfig.value.url.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请输入服务器地址"
                    )
                    return@launch
                }
                
                if (_serverConfig.value.username.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请输入用户名"
                    )
                    return@launch
                }
                
                if (_serverConfig.value.password.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请输入密码"
                    )
                    return@launch
                }
                
                // 保存配置
                settingsRepository.saveServerConfig(_serverConfig.value)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "服务器配置已保存"
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "保存失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 测试服务器连接
     */
    fun testConnection() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true, 
                    error = null, 
                    successMessage = null
                )
                
                // 先保存配置
                settingsRepository.saveServerConfig(_serverConfig.value)
                
                // 测试连接（简化版本，只测试网络可达性）
                val result = clipboardRepository.testConnection()
                result.onSuccess { isConnected ->
                    if (isConnected) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = null,
                            successMessage = "连接成功"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "连接失败",
                            successMessage = null
                        )
                    }
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "连接失败: ${e.message}",
                        successMessage = null
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "连接测试出错: ${e.message}",
                    successMessage = null
                )
            }
        }
    }
    
    /**
     * 更新自动同步设置
     */
    fun updateAutoSync(enabled: Boolean) {
        _appSettings.value = _appSettings.value.copy(autoSync = enabled)
        saveAppSettings()
    }
    
    /**
     * 更新同步间隔
     */
    fun updateSyncInterval(intervalSeconds: Long) {
        val intervalMs = intervalSeconds * 1000L
        val oldInterval = _appSettings.value.syncInterval
        
        _appSettings.value = _appSettings.value.copy(syncInterval = intervalMs)
        saveAppSettings()
        
        // 如果间隔发生变化且服务正在运行，则重启服务
        if (intervalMs != oldInterval && ClipboardSyncService.isServiceRunning(getApplication())) {
            viewModelScope.launch {
                try {
                    ClipboardSyncService.restartService(getApplication())
                    android.util.Log.d("SettingsViewModel", "同步间隔已更新，服务已重启")
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "重启服务失败", e)
                }
            }
        }
    }
    
    /**
     * 更新开机自启设置
     */
    fun updateSyncOnBoot(enabled: Boolean) {
        _appSettings.value = _appSettings.value.copy(syncOnBoot = enabled)
        saveAppSettings()
    }
    
    /**
     * 更新通知设置
     */
    fun updateShowNotifications(enabled: Boolean) {
        _appSettings.value = _appSettings.value.copy(showNotifications = enabled)
        saveAppSettings()
    }
    
    /**
     * 更新在多任务页面隐藏设置
     */
    fun updateHideInRecents(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _appSettings.value = _appSettings.value.copy(hideInRecents = enabled)
                
                // 确保设置被保存
                settingsRepository.saveAppSettings(_appSettings.value)
                
                android.util.Log.d("SettingsViewModel", "隐藏在多任务页面设置已更改为: $enabled 并已保存")
                
                // 等待一下确保保存完成
                kotlinx.coroutines.delay(100)
                
                // 再次验证设置是否正确保存
                val savedSettings = settingsRepository.appSettingsFlow.first()
                if (savedSettings.hideInRecents == enabled) {
                    android.util.Log.d("SettingsViewModel", "设置保存验证成功")
                } else {
                    android.util.Log.w("SettingsViewModel", "设置保存验证失败，期望: $enabled，实际: ${savedSettings.hideInRecents}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "更新隐藏设置失败", e)
            }
        }
    }
    
    /**
     * 更新解锁后自动重新写入设置
     */
    fun updateRewriteAfterUnlock(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _appSettings.value = _appSettings.value.copy(rewriteAfterUnlock = enabled)
                
                // 确保设置被保存
                settingsRepository.saveAppSettings(_appSettings.value)
                
                android.util.Log.d("SettingsViewModel", "解锁后自动重新写入设置已更改为: $enabled 并已保存")
                
                // 等待一下确保保存完成
                kotlinx.coroutines.delay(100)
                
                // 再次验证设置是否正确保存
                val savedSettings = settingsRepository.appSettingsFlow.first()
                if (savedSettings.rewriteAfterUnlock == enabled) {
                    android.util.Log.d("SettingsViewModel", "设置保存验证成功")
                } else {
                    android.util.Log.w("SettingsViewModel", "设置保存验证失败，期望: $enabled，实际: ${savedSettings.rewriteAfterUnlock}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "更新解锁后重新写入设置失败", e)
            }
        }
    }
    
    /**
     * 更新前台服务保活设置
     */
    fun updateForegroundServiceKeepalive(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _appSettings.value = _appSettings.value.copy(foregroundServiceKeepalive = enabled)
                
                // 确保设置被保存
                settingsRepository.saveAppSettings(_appSettings.value)
                
                android.util.Log.d("SettingsViewModel", "前台服务保活设置已更改为: $enabled 并已保存")
                
                // 等待一下确保保存完成
                kotlinx.coroutines.delay(100)
                
                // 再次验证设置是否正确保存
                val savedSettings = settingsRepository.appSettingsFlow.first()
                if (savedSettings.foregroundServiceKeepalive == enabled) {
                    android.util.Log.d("SettingsViewModel", "设置保存验证成功")
                } else {
                    android.util.Log.w("SettingsViewModel", "设置保存验证失败，期望: $enabled，实际: ${savedSettings.foregroundServiceKeepalive}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "更新前台服务保活设置失败", e)
            }
        }
    }
    
    /**
     * 更新设备名称
     */
    fun updateDeviceName(name: String) {
        _appSettings.value = _appSettings.value.copy(deviceName = name.trim())
        saveAppSettings()
    }
    
    /**
     * 更新剪贴板历史显示数量
     */
    fun updateClipboardHistoryCount(count: Int) {
        val validCount = count.coerceIn(1, 100) // 限制在1-100条之间
        val oldCount = _appSettings.value.clipboardHistoryCount
        
        _appSettings.value = _appSettings.value.copy(clipboardHistoryCount = validCount)
        saveAppSettings()
        
        // 如果新的数量小于旧的数量，需要清理超出限制的数据
        if (validCount < oldCount) {
            viewModelScope.launch {
                try {
                    android.util.Log.d("SettingsViewModel", "历史显示数量从 $oldCount 更改为 $validCount，开始清理超出限制的数据")
                    clipboardRepository.forceCleanupExcessData()
                    android.util.Log.d("SettingsViewModel", "数据清理完成")
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "清理超出限制数据时出错", e)
                }
            }
        }
    }
    
    /**
     * 保存应用设置
     */
    private fun saveAppSettings() {
        viewModelScope.launch {
            try {
                settingsRepository.saveAppSettings(_appSettings.value)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "保存设置失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 获取同步间隔（秒）
     */
    fun getSyncIntervalSeconds(): Long {
        return _appSettings.value.syncInterval / 1000L
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 清除成功信息
     */
    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    /**
     * 重置服务器配置
     */
    fun resetServerConfig() {
        _serverConfig.value = ServerConfig()
        viewModelScope.launch {
            settingsRepository.saveServerConfig(_serverConfig.value)
        }
    }
    
    /**
     * 重置应用设置
     */
    fun resetAppSettings() {
        _appSettings.value = AppSettings()
        saveAppSettings()
    }
}

/**
 * 设置页面UI状态
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)