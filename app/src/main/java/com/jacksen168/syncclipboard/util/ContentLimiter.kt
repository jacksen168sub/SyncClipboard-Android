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
        return content.toByteArray(Charsets.UTF_8).size > MAX_CONTENT_LENGTH
    }
    
    /**
     * 检查内容是否超过剪贴板限制
     */
    fun isContentTooLargeForClipboard(content: String): Boolean {
        return content.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_CONTENT_LENGTH
    }
    
    /**
     * 检查内容是否超过UI显示限制
     */
    fun isContentTooLargeForUI(content: String): Boolean {
        return content.toByteArray(Charsets.UTF_8).size > MAX_UI_CONTENT_LENGTH
    }
    
    /**
     * 裁剪内容以适应数据库存储
     */
    fun truncateForDatabase(content: String): String {
        return truncateContent(content, MAX_CONTENT_LENGTH)
    }
    
    /**
     * 裁剪内容以适应剪贴板操作
     */
    fun truncateForClipboard(content: String): String {
        return truncateContent(content, MAX_CLIPBOARD_CONTENT_LENGTH)
    }
    
    /**
     * 裁剪内容以适应UI显示
     */
    fun truncateForUI(content: String): String {
        return truncateContent(content, MAX_UI_CONTENT_LENGTH, " [内容已被截断以提高性能]")
    }
    
    /**
     * 通用内容裁剪方法
     */
    private fun truncateContent(content: String, maxLength: Int, suffix: String = " [内容已被自动裁剪]"): String {
        // 先检查字节数
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        if (contentBytes.size <= maxLength) {
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
        return result + suffix
    }
    
    /**
     * 获取内容的字节大小
     */
    fun getContentByteSize(content: String): Int {
        return content.toByteArray(Charsets.UTF_8).size
    }
}