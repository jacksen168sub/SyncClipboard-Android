package com.jacksen168.syncclipboard.data.model

/**
 * 服务器配置数据类
 */
data class ServerConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val trustUnsafeSSL: Boolean = false,
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
    val showNotifications: Boolean = true, // 常驻通知开关（前台服务通知）
    val showSyncStatusNotifications: Boolean = true, // 同步状态通知开关（同步成功/失败/文件下载等临时通知）
    val deviceName: String = "",
    val clipboardHistoryCount: Int = 10, // 剪贴板历史显示数量，默认10条
    val logDisplayCount: Int = -1, // 日志显示数量，默认-1(显示全部)
    val hideInRecents: Boolean = false, // 在多任务页面隐藏应用
    val rewriteAfterUnlock: Boolean = true, // 解锁后自动重新写入剪贴板同步过来的内容
    val downloadLocation: String = "", // 文件下载位置
    val autoSaveFiles: Boolean = false, // 文件自动保存开关
    val useRealtimeSync: Boolean = true // 使用实时同步（WebSocket/SignalR）
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