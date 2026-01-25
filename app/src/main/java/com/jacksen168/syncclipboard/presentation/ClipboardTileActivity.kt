package com.jacksen168.syncclipboard.presentation

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.SyncClipboardApplication
import com.jacksen168.syncclipboard.data.model.ClipboardItem
import com.jacksen168.syncclipboard.data.model.ClipboardSource
import com.jacksen168.syncclipboard.data.model.ClipboardType
import com.jacksen168.syncclipboard.util.ContentLimiter
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 透明 Activity，用于 Quick Settings Tile 触发剪贴板读取和同步
 * 
 * 关键技术点：
 * 1. 使用透明主题，用户无感知
 * 2. 在 onWindowFocusChanged 中读取剪贴板（Android 10+ 限制）
 * 3. 读取完成后自动 finish()
 */
class ClipboardTileActivity : androidx.activity.ComponentActivity() {

    companion object {
        private const val TAG = "ClipboardTileActivity"
        private const val NOTIFICATION_ID = 2001
    }

    // 标志位：是否需要读取剪贴板
    private var pendingClipboardRead = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.d(TAG, "onCreate - 透明 Activity 创建")

        // 设置标志位，等待窗口获得焦点后读取剪贴板
        pendingClipboardRead = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Logger.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus")

        // 关键：仅在获得焦点 + 标志位为 true 时执行一次
        if (hasFocus && pendingClipboardRead) {
            Logger.d(TAG, "窗口获得焦点，开始读取剪贴板")

            // 清除标志，防止重复执行
            pendingClipboardRead = false

            // 读取剪贴板并同步
            readClipboardAndSync()
        }
    }

    /**
     * 读取剪贴板并同步到服务器
     */
    private fun readClipboardAndSync() {
        lifecycleScope.launch {
            try {
                // 获取剪贴板内容
                val clipboardItem = getCurrentClipboardContent()

                if (clipboardItem != null) {
                    Logger.d(TAG, "剪贴板读取成功: ${clipboardItem.type}, 内容长度: ${clipboardItem.content.length}")

                    // 保存到数据库并上传到服务器
                    saveAndUploadClipboardItem(clipboardItem)
                } else {
                    Logger.w(TAG, "剪贴板为空或无法读取")
                    showToast("剪贴板为空")
                }

                // 显示同步结果通知
                showSyncNotification(clipboardItem != null)

            } catch (e: Exception) {
                Logger.e(TAG, "读取剪贴板时出错", e)
                showToast("读取剪贴板失败: ${e.message}")
                showSyncNotification(false)
            } finally {
                // 延迟关闭 Activity，确保通知显示
                kotlinx.coroutines.delay(500)
                finish()
            }
        }
    }

    /**
     * 获取当前剪贴板内容
     */
    private fun getCurrentClipboardContent(): ClipboardItem? {
        return try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // 检查是否有剪贴板内容
            if (!clipboardManager.hasPrimaryClip()) {
                Logger.d(TAG, "剪贴板为空 (hasPrimaryClip = false)")
                return null
            }

            val clip = clipboardManager.primaryClip ?: run {
                Logger.w(TAG, "primaryClip is null")
                return null
            }

            if (clip.itemCount == 0) {
                Logger.w(TAG, "itemCount = 0")
                return null
            }

            val item = clip.getItemAt(0) ?: run {
                Logger.w(TAG, "item is null")
                return null
            }

            // 尝试获取文本内容
            val text = item.text?.toString()

            if (!text.isNullOrBlank()) {
                // 检查内容大小，如果太大则裁剪
                val content = if (ContentLimiter.isContentTooLargeForDatabase(text)) {
                    Logger.w(TAG, "检测到过大的文本内容，正在进行裁剪: ${text.length} 字符")
                    ContentLimiter.truncateForDatabase(text)
                } else {
                    text
                }

                Logger.d(TAG, "成功读取文本内容，长度: ${content.length}")

                return ClipboardItem(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    type = ClipboardType.TEXT,
                    timestamp = System.currentTimeMillis(),
                    deviceName = getDeviceName(),
                    source = ClipboardSource.LOCAL,
                    contentHash = ClipboardItem.generateContentHash(content, ClipboardType.TEXT, null),
                    lastModified = System.currentTimeMillis()
                )
            }

            // 如果文本为空，尝试获取 URI（图片）
            val uri = item.uri
            if (uri != null) {
                Logger.d(TAG, "检测到图片 URI: $uri")
                // TODO: 处理图片内容（需要更复杂的逻辑）
                return null
            }

            // 尝试使用 coerceToText() 获取其他类型的内容
            val coerceText = item.coerceToText(this)?.toString()
            if (!coerceText.isNullOrBlank()) {
                Logger.d(TAG, "使用 coerceToText() 获取内容，长度: ${coerceText.length}")
                return ClipboardItem(
                    id = UUID.randomUUID().toString(),
                    content = coerceText,
                    type = ClipboardType.TEXT,
                    timestamp = System.currentTimeMillis(),
                    deviceName = getDeviceName(),
                    source = ClipboardSource.LOCAL,
                    contentHash = ClipboardItem.generateContentHash(coerceText, ClipboardType.TEXT, null),
                    lastModified = System.currentTimeMillis()
                )
            }

            Logger.w(TAG, "无法获取有效的剪贴板内容")
            return null

        } catch (e: SecurityException) {
            Logger.e(TAG, "无权限访问剪贴板 (SecurityException)", e)
            return null
        } catch (e: Exception) {
            Logger.e(TAG, "读取剪贴板失败", e)
            return null
        }
    }

    /**
     * 保存剪贴板项到数据库并上传到服务器
     */
    private suspend fun saveAndUploadClipboardItem(item: ClipboardItem) {
        try {
            val application = application as SyncClipboardApplication
            val clipboardRepository = application.clipboardRepository

            Logger.d(TAG, "开始保存剪贴板项到数据库")

            // 保存到数据库（传递正确的参数）
            val savedItem = clipboardRepository.saveClipboardItem(
                content = item.content,
                type = item.type,
                fileName = item.fileName,
                mimeType = item.mimeType,
                localPath = item.localPath,
                source = item.source
            )

            // 上传到服务器
            Logger.d(TAG, "开始上传到服务器")
            val result = clipboardRepository.uploadToServer(savedItem)

            result.onSuccess {
                Logger.i(TAG, "上传成功")
                showToast("同步成功")
            }.onFailure { error ->
                Logger.e(TAG, "上传失败", error)
                showToast("同步失败: ${error.message}")
            }

        } catch (e: Exception) {
            Logger.e(TAG, "保存和上传剪贴板项时出错", e)
            showToast("同步失败: ${e.message}")
        }
    }

    /**
     * 显示同步结果通知
     */
    private suspend fun showSyncNotification(success: Boolean) {
        try {
            // 检查是否启用同步状态通知
            val application = application as SyncClipboardApplication
            val settingsRepository = application.settingsRepository
            val appSettings = settingsRepository.appSettingsFlow.first()

            if (!appSettings.showSyncStatusNotifications) {
                Logger.d(TAG, "同步状态通知已禁用，跳过显示")
                return
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val message = if (success) "剪贴板同步成功" else "剪贴板同步失败"

            val notification = NotificationCompat.Builder(this, SyncClipboardApplication.CHANNEL_ID_SYNC_STATUS)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Logger.d(TAG, "显示同步通知: $message")

        } catch (e: Exception) {
            Logger.e(TAG, "显示通知时出错", e)
        }
    }

    /**
     * 获取设备名称
     */
    private fun getDeviceName(): String {
        return android.os.Build.MODEL ?: "Unknown Device"
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy - 透明 Activity 销毁")
    }
}