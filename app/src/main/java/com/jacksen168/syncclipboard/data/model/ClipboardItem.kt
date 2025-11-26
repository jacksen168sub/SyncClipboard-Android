package com.jacksen168.syncclipboard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.jacksen168.syncclipboard.util.ContentLimiter
import java.security.MessageDigest
import java.util.Date

/**
 * 剪贴板项目数据类
 */
@Entity(tableName = "clipboard_items")
data class ClipboardItem(
    @PrimaryKey
    @SerializedName("id")
    val id: String,
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("type")
    val type: ClipboardType,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("device_name")
    val deviceName: String? = null,
    
    @SerializedName("file_name")
    val fileName: String? = null,
    
    @SerializedName("file_size")
    val fileSize: Long? = null,
    
    @SerializedName("mime_type")
    val mimeType: String? = null,
    
    // 本地字段，不参与网络同步
    val isSynced: Boolean = false,
    val localPath: String? = null,
    val createdAt: Date = Date(timestamp),
    
    // 新增字段：内容来源跟踪
    val source: ClipboardSource = ClipboardSource.LOCAL,
    
    // 新增字段：内容哈希（用于智能去重）
    val contentHash: String = generateContentHash(content, type, fileName),
    
    // 新增字段：最后修改时间戳（区分创建和修改）
    val lastModified: Long = timestamp,
    
    // 新增字段：是否正在同步中
    val isSyncing: Boolean = false,
    
    // 新增字段：用于UI显示的裁剪内容（避免UI渲染性能问题）
    val uiContent: String = if (type == ClipboardType.TEXT && ContentLimiter.isContentTooLargeForUI(content)) {
        ContentLimiter.truncateForUI(content)
    } else {
        content
    }
) {
    companion object {
        /**
         * 生成内容哈希
         */
        fun generateContentHash(content: String, type: ClipboardType, fileName: String?): String {
            val input = "${content}_${type}_${fileName ?: ""}"
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * 剪贴板内容类型
 */
enum class ClipboardType {
    @SerializedName("text")
    TEXT,
    
    @SerializedName("image")
    IMAGE,
    
    @SerializedName("file")
    FILE
}

/**
 * 剪贴板内容来源
 */
enum class ClipboardSource {
    LOCAL,      // 本地创建的内容
    REMOTE,     // 从服务器同步的内容
    MERGED      // 合并后的内容
}

/**
 * SyncClipboard API的请求数据类
 * 对应SyncClipboard.json格式
 */
data class SyncClipboardRequest(
    @SerializedName("Type")
    val type: String, // "Text", "Image", "File", "Group"
    
    @SerializedName("Clipboard")
    val clipboard: String, // 内容或hash
    
    @SerializedName("File")
    val file: String // 文件名，文本时为空
)

/**
 * SyncClipboard API的响应数据类
 */
data class SyncClipboardResponse(
    @SerializedName("Type")
    val type: String,
    
    @SerializedName("Clipboard")
    val clipboard: String,
    
    @SerializedName("File")
    val file: String
)