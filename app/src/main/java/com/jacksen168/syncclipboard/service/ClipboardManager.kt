package com.jacksen168.syncclipboard.service

import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import com.jacksen168.syncclipboard.data.model.ClipboardItem
import com.jacksen168.syncclipboard.data.model.ClipboardType
import com.jacksen168.syncclipboard.data.model.ClipboardSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 剪贴板管理器
 * 负责监听和操作系统剪贴板
 */
class ClipboardManager(private val context: Context) {
    
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    
    // 剪贴板变化状态流
    private val _clipboardChangeFlow = MutableStateFlow<ClipboardItem?>(null)
    val clipboardChangeFlow: StateFlow<ClipboardItem?> = _clipboardChangeFlow.asStateFlow()
    
    // 监听器
    private var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    
    // 是否正在监听
    private var isListening = false
    
    // 上次处理的剪贴板内容哈希（避免重复处理）
    private var lastContentHash: String? = null
    
    // 最近设置到剪贴板的内容哈希（防止循环检测）
    private var recentlySetContentHash: String? = null
    
    // 防循环标记的过期时间
    private var preventLoopExpiry: Long = 0
    
    companion object {
        private const val TAG = "ClipboardManager"
        private const val CLIPBOARD_CACHE_DIR = "clipboard_cache"
    }
    
    /**
     * 开始监听剪贴板变化
     */
    fun startListening() {
        if (isListening) return
        
        clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange()
        }
        
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        isListening = true
        
        Log.d(TAG, "开始监听剪贴板变化")
        
        // 初始检查当前剪贴板内容
        handleClipboardChange()
    }
    
    /**
     * 停止监听剪贴板变化
     */
    fun stopListening() {
        if (!isListening || clipboardListener == null) return
        
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardListener = null
        isListening = false
        
        Log.d(TAG, "停止监听剪贴板变化")
    }
    
    /**
     * 处理剪贴板变化
     */
    private fun handleClipboardChange() {
        try {
            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount == 0) {
                return
            }
            
            val item = clip.getItemAt(0)
            val clipboardItem = when {
                // 文本内容
                item.text != null -> {
                    val content = item.text.toString()
                    val contentHash = content.hashCode().toString()
                    
                    // 检查是否是刚刚设置的内容（防循环）
                    if (isRecentlySetContent(contentHash)) {
                        Log.d(TAG, "检测到刚刚设置的内容，跳过处理: 文本")
                        return
                    }
                    
                    // 避免重复处理相同内容
                    if (contentHash == lastContentHash) return
                    lastContentHash = contentHash
                    
                    ClipboardItem(
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
                
                // 图片内容
                item.uri != null -> {
                    val uri = item.uri
                    val uriHash = uri.toString().hashCode().toString()
                    
                    // 检查是否是刚刚设置的内容（防循环）
                    if (isRecentlySetContent(uriHash)) {
                        Log.d(TAG, "检测到刚刚设置的内容，跳过处理: 图片")
                        return
                    }
                    
                    if (uriHash == lastContentHash) return
                    lastContentHash = uriHash
                    
                    handleImageUri(uri)
                }
                
                else -> null
            }
            
            clipboardItem?.let {
                _clipboardChangeFlow.value = it
                Log.d(TAG, "检测到剪贴板变化: ${it.type}, 内容长度: ${it.content.length}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理剪贴板变化时出错", e)
        }
    }
    
    /**
     * 处理图片URI
     */
    private fun handleImageUri(uri: Uri): ClipboardItem? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                // 创建缓存目录
                val cacheDir = File(context.cacheDir, CLIPBOARD_CACHE_DIR)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                // 生成文件名
                val fileName = "clipboard_image_${System.currentTimeMillis()}.jpg"
                val cacheFile = File(cacheDir, fileName)
                
                // 保存图片到缓存
                val outputStream = FileOutputStream(cacheFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                // 计算文件哈希值作为content
                val fileHash = calculateFileHash(cacheFile)
                
                // 获取MIME类型
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                
                ClipboardItem(
                    id = UUID.randomUUID().toString(),
                    content = fileHash, // 使用哈希值作为content
                    type = ClipboardType.IMAGE,
                    timestamp = System.currentTimeMillis(),
                    deviceName = getDeviceName(),
                    fileName = fileName,
                    fileSize = cacheFile.length(),
                    mimeType = mimeType,
                    localPath = cacheFile.absolutePath,
                    source = ClipboardSource.LOCAL,
                    contentHash = ClipboardItem.generateContentHash(fileHash, ClipboardType.IMAGE, fileName),
                    lastModified = System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图片URI时出错", e)
            null
        }
    }
    
    /**
     * 设置文本到剪贴板
     */
    fun setClipboardText(text: String, label: String = "SyncClipboard") {
        try {
            // 记录即将设置的内容哈希，避免循环检测
            val contentHash = text.hashCode().toString()
            markRecentlySetContent(contentHash)
            
            val clip = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "设置文本到剪贴板: ${text.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "设置文本到剪贴板时出错", e)
        }
    }
    
    /**
     * 设置图片到剪贴板
     */
    fun setClipboardImage(uri: Uri, label: String = "SyncClipboard") {
        try {
            // 记录即将设置的内容哈希，避免循环检测
            val uriHash = uri.toString().hashCode().toString()
            markRecentlySetContent(uriHash)
            
            val clip = ClipData.newUri(context.contentResolver, label, uri)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "设置图片到剪贴板: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "设置图片到剪贴板时出错", e)
        }
    }
    
    /**
     * 获取当前剪贴板文本内容
     */
    fun getCurrentClipboardText(): String? {
        return try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                item.text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取剪贴板文本内容时出错", e)
            null
        }
    }
    
    /**
     * 获取当前剪贴板内容
     */
    fun getCurrentClipboardContent(): ClipboardItem? {
        return try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                when {
                    item.text != null -> ClipboardItem(
                        id = UUID.randomUUID().toString(),
                        content = item.text.toString(),
                        type = ClipboardType.TEXT,
                        timestamp = System.currentTimeMillis(),
                        deviceName = getDeviceName(),
                        source = ClipboardSource.LOCAL,
                        contentHash = ClipboardItem.generateContentHash(item.text.toString(), ClipboardType.TEXT, null),
                        lastModified = System.currentTimeMillis()
                    )
                    item.uri != null -> handleImageUri(item.uri)
                    else -> null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取剪贴板内容时出错", e)
            null
        }
    }
    
    /**
     * 计算文件哈希值
     */
    private fun calculateFileHash(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val inputStream = file.inputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            inputStream.use {
                while (it.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "计算文件哈希时出错", e)
            "" // 返回空字符串，让调用方处理
        }
    }
    
    /**
     * 标记刚刚设置的内容哈希（防循环检测）
     */
    private fun markRecentlySetContent(contentHash: String) {
        recentlySetContentHash = contentHash
        // 设置5秒后过期
        preventLoopExpiry = System.currentTimeMillis() + 5000
        Log.d(TAG, "标记防循环内容: $contentHash")
    }
    
    /**
     * 检查是否是刚刚设置的内容
     */
    private fun isRecentlySetContent(contentHash: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return recentlySetContentHash == contentHash && currentTime < preventLoopExpiry
    }
    
    /**
     * 检查是否有剪贴板访问权限
     */
    fun hasClipboardAccess(): Boolean {
        return try {
            clipboardManager.primaryClip
            true
        } catch (e: Exception) {
            Log.w(TAG, "无剪贴板访问权限", e)
            false
        }
    }
    
    /**
     * 清空剪贴板
     */
    fun clearClipboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                // 在较旧版本中设置空文本
                val clip = ClipData.newPlainText("", "")
                clipboardManager.setPrimaryClip(clip)
            }
            Log.d(TAG, "清空剪贴板")
        } catch (e: Exception) {
            Log.e(TAG, "清空剪贴板时出错", e)
        }
    }
    
    /**
     * 获取设备名称
     */
    private fun getDeviceName(): String {
        return try {
            Build.MODEL ?: "Android Device"
        } catch (e: Exception) {
            "Android Device"
        }
    }
    
    /**
     * 清理缓存文件
     */
    fun cleanupCache() {
        try {
            val cacheDir = File(context.cacheDir, CLIPBOARD_CACHE_DIR)
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                files?.forEach { file ->
                    // 删除超过24小时的缓存文件
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存时出错", e)
        }
    }
}