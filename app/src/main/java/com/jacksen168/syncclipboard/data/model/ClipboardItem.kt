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
         * 使用与服务端一致的哈希计算方式：type|text|dataName|size
         * UTF-8编码后使用SHA256计算，结果转为大写十六进制字符串
         */
        fun generateContentHash(content: String, type: ClipboardType, fileName: String?): String {
            val typeString = when (type) {
                ClipboardType.TEXT -> "Text"
                ClipboardType.IMAGE -> "Image"
                ClipboardType.FILE -> "File"
            }
            
            // 按照服务端格式构造输入字符串：type|text|dataName|size
            // 注意：dataName 和 size 对于文本类型可能为空
            val input = "$typeString|$content|${fileName ?: ""}"
            
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            
            // 转换为大写十六进制字符串（与服务端保持一致）
            return hashBytes.joinToString("") { "%02X".format(it) }
        }
        
        /**
         * 计算图片类型的哈希（与服务端格式一致）
         * 格式：fileName|文件内容的SHA256
         * 步骤：
         * 1. 计算文件内容的 SHA256 哈希值并转换为大写十六进制字符串
         * 2. 构造字符串：fileName|文件内容SHA256字符串
         * 3. 对该字符串进行 UTF-8 编码后再次计算 SHA256
         */
        fun generateImageHash(fileName: String, fileContentHash: String): String {
            // 构造输入字符串：fileName|文件内容SHA256字符串
            val input = "$fileName|$fileContentHash"
            
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            
            // 转换为大写十六进制字符串
            return hashBytes.joinToString("") { "%02X".format(it) }
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
 * 对应服务端 v3.1.1+ 新版 API 格式
 */
data class SyncClipboardRequest(
    @SerializedName("type")
    val type: String, // "Text", "Image", "File"
    
    @SerializedName("hash")
    val hash: String, // 内容哈希值（SHA256）
    
    @SerializedName("text")
    val text: String? = null, // 文本内容
    
    @SerializedName("hasData")
    val hasData: Boolean = false, // 是否有附加数据（图片/文件）
    
    @SerializedName("dataName")
    val dataName: String? = null, // 数据文件名
    
    @SerializedName("size")
    val size: Long = 0 // 内容大小（字节）
)

/**
 * SyncClipboard API的响应数据类
 * 对应服务端 v3.1.1+ 新版 API 格式
 */
data class SyncClipboardResponse(
    @SerializedName("type")
    val type: String, // "Text", "Image", "File"
    
    @SerializedName("hash")
    val hash: String, // 内容哈希值（SHA256）
    
    @SerializedName("text")
    val text: String?, // 文本内容
    
    @SerializedName("hasData")
    val hasData: Boolean = false, // 是否有附加数据（图片/文件）
    
    @SerializedName("dataName")
    val dataName: String? = null, // 数据文件名
    
    @SerializedName("size")
    val size: Long = 0 // 内容大小（字节）
)

/**
 * Profile 类型枚举（用于历史记录 API）
 */
enum class ProfileType {
    @SerializedName("Text")
    TEXT,
    
    @SerializedName("File")
    FILE,
    
    @SerializedName("Image")
    IMAGE,
    
    @SerializedName("Group")
    GROUP,
    
    @SerializedName("Unknown")
    UNKNOWN,
    
    @SerializedName("None")
    NONE
}

/**
 * 历史记录数据类（用于 /api/history 接口）
 */
data class HistoryRecordDto(
    @SerializedName("hash")
    val hash: String?,
    
    @SerializedName("text")
    val text: String?,
    
    @SerializedName("type")
    val type: ProfileType,
    
    @SerializedName("createTime")
    val createTime: String?, // ISO 8601 格式
    
    @SerializedName("lastModified")
    val lastModified: String?, // ISO 8601 格式
    
    @SerializedName("lastAccessed")
    val lastAccessed: String?, // ISO 8601 格式
    
    @SerializedName("starred")
    val starred: Boolean = false,
    
    @SerializedName("pinned")
    val pinned: Boolean = false,
    
    @SerializedName("size")
    val size: Long = 0,
    
    @SerializedName("hasData")
    val hasData: Boolean = false,
    
    @SerializedName("version")
    val version: Int = 0,
    
    @SerializedName("isDeleted")
    val isDeleted: Boolean = false
)