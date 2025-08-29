package com.jacksen168.syncclipboard.data.model

/**
 * 服务器配置数据类
 */
data class ServerConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val isConnected: Boolean = false,
    val lastSyncTime: Long = 0L
)

/**
 * 应用设置数据类
 */
data class AppSettings(
    val autoSync: Boolean = true,
    val syncInterval: Long = 300L, // 3秒
    val syncOnBoot: Boolean = true,
    val showNotifications: Boolean = true,
    val deviceName: String = "",
    val clipboardHistoryCount: Int = 10 // 剪贴板历史显示数量，默认10条
)

/**
 * 同步状态枚举
 */
enum class SyncStatus {
    IDLE,           // 空闲
    SYNCING,        // 同步中
    CONNECTED,      // 已连接
    DISCONNECTED,   // 已断开
    ERROR           // 错误
}

/**
 * 网络状态
 */
enum class NetworkStatus {
    AVAILABLE,
    UNAVAILABLE,
    METERED
}