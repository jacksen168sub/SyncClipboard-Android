package com.jacksen168.syncclipboard.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.presentation.ClipboardTileActivity
import com.jacksen168.syncclipboard.util.Logger

/**
 * Quick Settings Tile 服务
 * 
 * 功能：用户点击磁贴后，启动透明 Activity 读取剪贴板并同步
 * 
 * 关键技术点：
 * 1. Android 7.0+ (API 24) 支持
 * 2. Android 14+ (API 34) 需要使用 PendingIntent 启动 Activity
 * 3. 使用 startActivityAndCollapse() 收起通知栏
 */
@Suppress("DEPRECATION")
class ClipboardSyncTileService : TileService() {

    companion object {
        private const val TAG = "ClipboardSyncTileService"
    }

    override fun onClick() {
        super.onClick()
        Logger.d(TAG, "磁贴被点击")

        // 更新磁贴状态为正在同步
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }

        // 创建 Intent 并添加标识符
        val intent = Intent(this, ClipboardTileActivity::class.java).apply {
            // 从 Service 启动 Activity 必须添加此 Flag
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        // 根据 Android 版本选择启动方式
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34)：使用新 API
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // Android 7-13：使用旧 API
                startActivityAndCollapse(intent)
            }
            Logger.d(TAG, "成功启动透明 Activity")
        } catch (e: Exception) {
            Logger.e(TAG, "启动 Activity 失败", e)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Logger.d(TAG, "磁贴开始监听")

        // 更新磁贴状态
        qsTile?.apply {
            state = Tile.STATE_ACTIVE    // 可点击状态
            label = "发送剪贴板"         // 磁贴文字
            icon = Icon.createWithResource(this@ClipboardSyncTileService, android.R.drawable.ic_menu_upload)
            updateTile()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        Logger.d(TAG, "磁贴停止监听")
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Logger.d(TAG, "磁贴已添加到快速设置")
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Logger.d(TAG, "磁贴已从快速设置移除")
    }
}