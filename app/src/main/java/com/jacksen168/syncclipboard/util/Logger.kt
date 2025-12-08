package com.jacksen168.syncclipboard.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 自定义日志记录器，支持保存日志到文件并保留最近3天的日志
 */
class Logger private constructor() {
    companion object {
        private const val TAG = "SyncClipboardLogger"
        private const val MAX_LOG_DAYS = 3L // 保存最近3天的日志
        
        @Volatile
        private var INSTANCE: Logger? = null
        private val LOCK = ReentrantLock()
        
        fun getInstance(context: Context): Logger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Logger().also { 
                    INSTANCE = it
                    it.initialize(context.applicationContext)
                }
            }
        }
        
        // 日志级别
        const val VERBOSE = Log.VERBOSE
        const val DEBUG = Log.DEBUG
        const val INFO = Log.INFO
        const val WARN = Log.WARN
        const val ERROR = Log.ERROR
        
        // 方便使用的静态方法
        fun v(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.log(VERBOSE, tag, message, throwable)
        }
        
        fun d(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.log(DEBUG, tag, message, throwable)
        }
        
        fun i(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.log(INFO, tag, message, throwable)
        }
        
        fun w(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.log(WARN, tag, message, throwable)
        }
        
        fun e(tag: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.log(ERROR, tag, message, throwable)
        }
    }
    
    private lateinit var logDir: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    private fun initialize(context: Context) {
        try {
            logDir = File(context.cacheDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            Log.d(TAG, "Logger initialized, log directory: ${logDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logger", e)
        }
    }
    
    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        try {
            // 使用系统Log输出
            when (priority) {
                VERBOSE -> Log.v(tag, message, throwable)
                DEBUG -> Log.d(tag, message, throwable)
                INFO -> Log.i(tag, message, throwable)
                WARN -> Log.w(tag, message, throwable)
                ERROR -> Log.e(tag, message, throwable)
                else -> Log.d(tag, message, throwable)
            }
            
            // 保存到文件
            saveToFile(priority, tag, message, throwable)
        } catch (e: Exception) {
            // 忽略日志记录错误，避免影响主流程
            Log.e(TAG, "Failed to log message", e)
        }
    }
    
    private fun saveToFile(priority: Int, tag: String, message: String, throwable: Throwable?) {
        LOCK.withLock {
            try {
                cleanOldLogs()
                
                val logFile = getCurrentLogFile()
                val logLevel = priorityToString(priority)
                val time = dateFormat.format(System.currentTimeMillis())
                val threadName = Thread.currentThread().name
                
                FileWriter(logFile, true).use { writer ->
                    writer.append("[$time] [$logLevel] [$threadName] [$tag] $message")
                    if (throwable != null) {
                        writer.append("\n")
                        throwable.printStackTrace(PrintWriter(writer))
                    }
                    writer.append("\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save log to file", e)
            }
        }
    }
    
    private fun getCurrentLogFile(): File {
        val fileName = "log_${fileNameFormat.format(System.currentTimeMillis())}.txt"
        return File(logDir, fileName)
    }
    
    private fun cleanOldLogs() {
        try {
            val files = logDir.listFiles() ?: return
            val cutoffTime = System.currentTimeMillis() - (MAX_LOG_DAYS * 24 * 60 * 60 * 1000)
            
            files.forEach { file ->
                if (file.name.startsWith("log_") && file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean old logs", e)
        }
    }
    
    fun getRecentLogs(maxLines: Int = 1000): List<String> {
        return LOCK.withLock {
            try {
                val files = logDir.listFiles() ?: return@withLock emptyList()
                
                // 按修改时间排序，最新的在前面
                val sortedFiles = files.filter { it.name.startsWith("log_") }
                    .sortedByDescending { it.lastModified() }
                
                val lines = mutableListOf<String>()
                
                for (file in sortedFiles) {
                    if (lines.size >= maxLines) break
                    
                    file.readLines().reversed().forEach { line ->
                        if (lines.size >= maxLines) return@forEach
                        lines.add(line)
                    }
                }
                
                lines.reversed() // 返回按时间顺序排列的日志
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read recent logs", e)
                emptyList()
            }
        }
    }
    
    fun exportLogsToFile(exportFile: File): Boolean {
        return LOCK.withLock {
            try {
                val files = logDir.listFiles() ?: return@withLock false
                
                // 按日期排序，最早的在前面
                val sortedFiles = files.filter { it.name.startsWith("log_") }
                    .sortedBy { it.name }
                
                PrintWriter(FileWriter(exportFile, false)).use { writer ->
                    sortedFiles.forEach { file ->
                        try {
                            file.forEachLine { line ->
                                writer.println(line)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read log file: ${file.name}", e)
                        }
                    }
                }
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs to file", e)
                false
            }
        }
    }
    
    /**
     * 将日志导出到输出流
     */
    fun exportLogsToStream(outputStream: java.io.OutputStream): Boolean {
        return LOCK.withLock {
            try {
                val files = logDir.listFiles() ?: return@withLock false
                
                // 按日期排序，最早的在前面
                val sortedFiles = files.filter { it.name.startsWith("log_") }
                    .sortedBy { it.name }
                
                PrintWriter(java.io.OutputStreamWriter(outputStream, Charsets.UTF_8), true).use { writer ->
                    sortedFiles.forEach { file ->
                        try {
                            file.forEachLine { line ->
                                writer.println(line)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read log file: ${file.name}", e)
                        }
                    }
                }
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export logs to stream", e)
                false
            }
        }
    }
    
    private fun priorityToString(priority: Int): String {
        return when (priority) {
            VERBOSE -> "V"
            DEBUG -> "D"
            INFO -> "I"
            WARN -> "W"
            ERROR -> "E"
            else -> "D"
        }
    }
}