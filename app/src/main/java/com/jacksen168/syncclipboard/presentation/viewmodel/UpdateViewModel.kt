package com.jacksen168.syncclipboard.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacksen168.syncclipboard.data.model.UpdateInfo
import com.jacksen168.syncclipboard.data.repository.UpdateRepository
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用更新检查 ViewModel
 */
class UpdateViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "UpdateViewModel"
    }
    
    private val updateRepository = UpdateRepository(context)
    
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    
    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()
    
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()
    
    private val _showNoUpdateMessage = MutableStateFlow(false)
    val showNoUpdateMessage: StateFlow<Boolean> = _showNoUpdateMessage.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * 关闭无更新消息
     */
    fun dismissNoUpdateMessage() {
        _showNoUpdateMessage.value = false
    }
    
    /**
     * 关闭错误消息
     */
    fun dismissErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * 检查应用更新
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            try {
                _isChecking.value = true
                _showNoUpdateMessage.value = false // 重置提示状态
                _errorMessage.value = null // 重置错误状态
                Logger.d(TAG, "开始检查应用更新...")
                
                val updateInfo = updateRepository.checkForUpdate()
                _updateInfo.value = updateInfo
                
                Logger.d(TAG, "UpdateInfo: hasUpdate=${updateInfo.hasUpdate}, latest=${updateInfo.latestVersion}, current=${updateInfo.currentVersion}")
                
                if (updateInfo.hasUpdate) {
                    Logger.i(TAG, "发现新版本: ${updateInfo.latestVersion}")
                    _showUpdateDialog.value = true
                } else {
                    Logger.d(TAG, "已是最新版本: ${updateInfo.currentVersion}")
                    // 手动检查时显示"已是最新版本"提示
                    _showNoUpdateMessage.value = true
                }
            } catch (e: Exception) {
                Logger.e(TAG, "检查更新时出错", e)
                _errorMessage.value = "检查更新失败: ${e.message ?: "网络错误"}"
            } finally {
                _isChecking.value = false
            }
        }
    }
    
    /**
     * 静默检查更新（启动时调用）
     */
    fun checkForUpdateSilently() {
        viewModelScope.launch {
            try {
                Logger.d(TAG, "静默检查应用更新...")
                val updateInfo = updateRepository.checkForUpdate()
                _updateInfo.value = updateInfo
                
                if (updateInfo.hasUpdate) {
                    Logger.i(TAG, "发现新版本: ${updateInfo.latestVersion}")
                    _showUpdateDialog.value = true
                }
            } catch (e: Exception) {
                Logger.e(TAG, "静默检查更新时出错", e)
            }
        }
    }
    
    /**
     * 前往更新页面
     */
    fun goToUpdate() {
        val updateInfo = _updateInfo.value
        if (updateInfo != null && updateInfo.hasUpdate) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Logger.d(TAG, "打开更新页面: ${updateInfo.downloadUrl}")
            } catch (e: Exception) {
                Logger.e(TAG, "打开更新页面失败", e)
            }
        }
        dismissUpdateDialog()
    }
    
    /**
     * 稍后提醒（关闭更新对话框）
     */
    fun remindLater() {
        dismissUpdateDialog()
        Logger.d(TAG, "用户选择稍后更新")
    }
    
    /**
     * 关闭更新对话框
     */
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }
}