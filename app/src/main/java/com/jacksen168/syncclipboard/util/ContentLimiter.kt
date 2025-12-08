package com.jacksen168.syncclipboard.util

/**
 * 内容限制工具类
 * 用于限制剪贴板内容和数据库内容的大小，避免TransactionTooLargeException和数据库行过大问题
 */
object ContentLimiter {
    // 1MB限制（约100万字符）
    private const val MAX_CONTENT_LENGTH = 1024 * 1024
    
    // 剪贴板内容限制（略小于1MB，留出一些空间给系统包装）
    private const val MAX_CLIPBOARD_CONTENT_LENGTH = 900 * 1024
    
    // UI显示内容限制（避免UI渲染性能问题）
    private const val MAX_UI_CONTENT_LENGTH = 10 * 1024 // 10KB
    
    /**
     * 检查内容是否超过数据库限制
     */
    fun isContentTooLargeForDatabase(content: String): Boolean {
        val isTooLarge = content.toByteArray(Charsets.UTF_8).size > MAX_CONTENT_LENGTH
        if (isTooLarge) {
            Logger.w("ContentLimiter", "内容超过数据库限制: ${content.length} 字符, ${content.toByteArray(Charsets.UTF_8).size} 字节 > $MAX_CONTENT_LENGTH 字节")
        }
        return isTooLarge
    }
    
    /**
     * 检查内容是否超过剪贴板限制
     */
    fun isContentTooLargeForClipboard(content: String): Boolean {
        val isTooLarge = content.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_CONTENT_LENGTH
        if (isTooLarge) {
            Logger.w("ContentLimiter", "内容超过剪贴板限制: ${content.length} 字符, ${content.toByteArray(Charsets.UTF_8).size} 字节 > $MAX_CLIPBOARD_CONTENT_LENGTH 字节")
        }
        return isTooLarge
    }
    
    /**
     * 检查内容是否超过UI显示限制
     */
    fun isContentTooLargeForUI(content: String): Boolean {
        val isTooLarge = content.toByteArray(Charsets.UTF_8).size > MAX_UI_CONTENT_LENGTH
        if (isTooLarge) {
            Logger.d("ContentLimiter", "内容超过UI显示限制: ${content.length} 字符, ${content.toByteArray(Charsets.UTF_8).size} 字节 > $MAX_UI_CONTENT_LENGTH 字节")
        }
        return isTooLarge
    }
    
    /**
     * 裁剪内容以适应数据库存储
     */
    fun truncateForDatabase(content: String): String {
        Logger.d("ContentLimiter", "开始裁剪内容以适应数据库存储")
        val result = truncateContent(content, MAX_CONTENT_LENGTH)
        Logger.d("ContentLimiter", "数据库内容裁剪完成: 原始长度=${content.length}, 结果长度=${result.length}")
        return result
    }
    
    /**
     * 裁剪内容以适应剪贴板操作
     */
    fun truncateForClipboard(content: String): String {
        Logger.d("ContentLimiter", "开始裁剪内容以适应剪贴板操作")
        val result = truncateContent(content, MAX_CLIPBOARD_CONTENT_LENGTH)
        Logger.d("ContentLimiter", "剪贴板内容裁剪完成: 原始长度=${content.length}, 结果长度=${result.length}")
        return result
    }
    
    /**
     * 裁剪内容以适应UI显示
     */
    fun truncateForUI(content: String): String {
        Logger.d("ContentLimiter", "开始裁剪内容以适应UI显示")
        val result = truncateContent(content, MAX_UI_CONTENT_LENGTH, " [内容已被截断以提高性能]")
        Logger.d("ContentLimiter", "UI内容裁剪完成: 原始长度=${content.length}, 结果长度=${result.length}")
        return result
    }
    
    /**
     * 通用内容裁剪方法
     */
    private fun truncateContent(content: String, maxLength: Int, suffix: String = " [内容已被自动裁剪]"): String {
        Logger.d("ContentLimiter", "开始裁剪内容: 原始长度=${content.length}, 最大长度=$maxLength")
        
        // 先检查字节数
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        if (contentBytes.size <= maxLength) {
            Logger.d("ContentLimiter", "内容未超限，无需裁剪")
            return content
        }
        
        // 如果字节数超限，需要裁剪
        // 使用二分查找找到合适的字符位置
        var left = 0
        var right = content.length
        var result = content
        
        while (left <= right) {
            val mid = (left + right) / 2
            val truncated = content.substring(0, mid)
            val bytes = truncated.toByteArray(Charsets.UTF_8)
            
            if (bytes.size <= maxLength) {
                result = truncated
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        
        // 添加提示信息表示内容已被裁剪
        val finalResult = result + suffix
        Logger.d("ContentLimiter", "内容裁剪完成: 原始长度=${content.length}, 结果长度=${finalResult.length}")
        return finalResult
    }
    
    /**
     * 获取内容的字节大小
     */
    fun getContentByteSize(content: String): Int {
        val size = content.toByteArray(Charsets.UTF_8).size
        Logger.d("ContentLimiter", "内容字节大小: ${content.length} 字符 = $size 字节")
        return size
    }
}