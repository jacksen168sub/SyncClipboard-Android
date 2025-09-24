package com.jacksen168.syncclipboard.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.jacksen168.syncclipboard.data.api.ApiClient
import com.jacksen168.syncclipboard.data.api.SyncClipboardApi
import com.jacksen168.syncclipboard.data.database.ClipboardDatabase
import com.jacksen168.syncclipboard.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 剪贴板数据仓库
 */
class ClipboardRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val TAG = "ClipboardRepository"
    }

    // 数据库实例
    private val database = Room.databaseBuilder(
        context.applicationContext,
        ClipboardDatabase::class.java,
        ClipboardDatabase.DATABASE_NAME
    )
    .addMigrations(ClipboardDatabase.MIGRATION_1_2)
    .build()

    private val clipboardDao = database.clipboardItemDao()

    // API客户端
    private var apiService: SyncClipboardApi? = null

    // 同步锁：防止上传和下载同时进行
    private val syncMutex = Mutex()

    // 最后同步的内容哈希,用于防止循环
    private var lastSyncedContentHash: String? = null

    init {
        // 监听服务器配置变化,更新API客户端
        settingsRepository.serverConfigFlow
            .onEach { config ->
                if (config.url.isNotEmpty()) {
                    try {
                        apiService = ApiClient.createApiService(
                            baseUrl = config.url,
                            username = config.username,
                            password = config.password,
                            trustUnsafeSSL = config.trustUnsafeSSL
                        )
                        Log.d("ClipboardRepository", "成功初始化API服务: ${config.url}, trustUnsafeSSL: ${config.trustUnsafeSSL}")
                    } catch (e: Exception) {
                        Log.e("ClipboardRepository", "初始化API服务失败: ${config.url}", e)
                        apiService = null
                        // 可以在这里通知UI显示URL错误
                    }
                }
            }
            .launchIn(kotlinx.coroutines.GlobalScope)
    }

    /**
     * 获取本地剪贴板项目列表（带去重逻辑）
     */
    fun getLocalClipboardItems(): Flow<List<ClipboardItem>> = flow {
        val allItems = clipboardDao.getAllItems()
        val deduplicatedItems = smartDeduplication(allItems)
        emit(deduplicatedItems)
    }.flowOn(Dispatchers.IO)

    /**
     * 智能去重逻辑：基于内容哈希合并重复项目
     */
    private suspend fun smartDeduplication(items: List<ClipboardItem>): List<ClipboardItem> {
        val hashGroups = items.groupBy { it.contentHash }
        val deduplicatedItems = mutableListOf<ClipboardItem>()

        hashGroups.forEach { (hash, group) ->
            if (group.size == 1) {
                // 没有重复,直接添加
                deduplicatedItems.add(group.first())
            } else {
                // 有重复,需要合并
                val localItems = group.filter { it.source == ClipboardSource.LOCAL }
                val remoteItems = group.filter { it.source == ClipboardSource.REMOTE }
                val mergedItems = group.filter { it.source == ClipboardSource.MERGED }

                when {
                    // 如果有已合并的项,优先使用
                    mergedItems.isNotEmpty() -> {
                        val latest = mergedItems.maxByOrNull { it.lastModified }!!
                        deduplicatedItems.add(latest)
                        // 删除其他重复项
                        group.filter { it.id != latest.id }.forEach { clipboardDao.deleteItem(it) }
                    }
                    // 如果同时有本地和远程项,合并为一个项
                    localItems.isNotEmpty() && remoteItems.isNotEmpty() -> {
                        val localLatest = localItems.maxByOrNull { it.lastModified }!!
                        val remoteLatest = remoteItems.maxByOrNull { it.lastModified }!!

                        // 选择最新的作为基础,但标记为已合并
                        val base = if (localLatest.lastModified >= remoteLatest.lastModified) localLatest else remoteLatest
                        val merged = base.copy(
                            source = ClipboardSource.MERGED,
                            isSynced = true, // 已合并的项视为已同步
                            lastModified = maxOf(localLatest.lastModified, remoteLatest.lastModified)
                        )

                        // 更新数据库
                        clipboardDao.insertItem(merged)
                        deduplicatedItems.add(merged)

                        // 删除原始重复项
                        group.forEach { if (it.id != merged.id) clipboardDao.deleteItem(it) }
                    }
                    // 只有本地项或只有远程项,保留最新的
                    else -> {
                        val latest = group.maxByOrNull { it.lastModified }!!
                        deduplicatedItems.add(latest)
                        // 删除旧的重复项
                        group.filter { it.id != latest.id }.forEach { clipboardDao.deleteItem(it) }
                    }
                }
            }
        }

        return deduplicatedItems.sortedByDescending { it.lastModified }
    }

    /**
     * 获取最近的剪贴板项目（带去重逻辑）
     */
    suspend fun getRecentItems(limit: Int = 50): List<ClipboardItem> = withContext(Dispatchers.IO) {
        val allItems = clipboardDao.getAllItems()
        val deduplicatedItems = smartDeduplication(allItems)
        deduplicatedItems.take(limit)
    }

    /**
     * 保存剪贴板项目到本地
     */
    suspend fun saveClipboardItem(
        content: String,
        type: ClipboardType,
        fileName: String? = null,
        mimeType: String? = null,
        localPath: String? = null,
        source: ClipboardSource = ClipboardSource.LOCAL
    ): ClipboardItem = withContext(Dispatchers.IO) {
        val settings = settingsRepository.appSettingsFlow.first()
        val contentHash = ClipboardItem.generateContentHash(content, type, fileName)
        val currentTime = System.currentTimeMillis()

        // 检查是否已存在相同内容哈希的项目
        val existingItems = clipboardDao.getItemsByContentHash(contentHash)

        val item = if (existingItems.isNotEmpty()) {
            // 如果已存在,更新现有项目
            val existing = existingItems.first()
            val updatedItem = existing.copy(
                timestamp = currentTime,
                lastModified = currentTime,
                source = when {
                    existing.source == ClipboardSource.REMOTE && source == ClipboardSource.LOCAL -> ClipboardSource.MERGED
                    existing.source == ClipboardSource.LOCAL && source == ClipboardSource.REMOTE -> ClipboardSource.MERGED
                    else -> source
                },
                isSynced = source == ClipboardSource.REMOTE || existing.isSynced,
                deviceName = if (source == ClipboardSource.LOCAL) settings.deviceName else existing.deviceName
            )
            clipboardDao.updateItem(updatedItem)
            updatedItem
        } else {
            // 创建新项目
            val newItem = ClipboardItem(
                id = UUID.randomUUID().toString(),
                content = content,
                type = type,
                timestamp = currentTime,
                deviceName = if (source == ClipboardSource.LOCAL) settings.deviceName else null,
                fileName = fileName,
                mimeType = mimeType,
                localPath = localPath,
                isSynced = source == ClipboardSource.REMOTE,
                source = source,
                contentHash = contentHash,
                lastModified = currentTime
            )
            clipboardDao.insertItem(newItem)
            newItem
        }

        // 检查并清理超过限制的数据
        cleanupExcessItems()

        item
    }

    /**
     * 清理超过限制的剪贴板数据（根据设置保留指定数量）
     */
    private suspend fun cleanupExcessItems() {
        val settings = settingsRepository.appSettingsFlow.first()
        val maxItems = settings.clipboardHistoryCount
        val currentCount = clipboardDao.getItemCount()

        if (currentCount > maxItems) {
            Log.d(TAG, "当前记录数: $currentCount, 最大允许: $maxItems, 需要删除: ${currentCount - maxItems}")

            // 获取需要删除的项目（最旧的项目）
            val excessCount = currentCount - maxItems
            val itemsToDelete = clipboardDao.getOldestItems(excessCount)

            // 先清理图片缓存文件
            for (item in itemsToDelete) {
                if (item.type == ClipboardType.IMAGE && !item.localPath.isNullOrEmpty()) {
                    cleanupImageFileCache(item.localPath)
                }
            }

            // 然后删除数据库记录
            clipboardDao.deleteOldestItems(excessCount)

            Log.d(TAG, "已清理 $excessCount 条超出限制的记录及其缓存文件")
        }
    }

    /**
     * 从服务器获取剪贴板内容
     */
    suspend fun fetchFromServer(): Result<ClipboardItem?> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val api = apiService ?: return@withContext Result.failure(
                    Exception("API服务未初始化")
                )

                val response = api.getClipboard()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.clipboard.isNotEmpty()) {
                        // 转换SyncClipboard格式到本地ClipboardItem格式
                        val type = when (body.type) {
                            "Text" -> ClipboardType.TEXT
                            "Image" -> ClipboardType.IMAGE
                            "File" -> ClipboardType.FILE
                            else -> ClipboardType.TEXT
                        }

                        val fileName = if (body.file.isNotEmpty()) body.file else null

                        // 对于图片类型,需要下载文件并保存到本地
                        val content = if (type == ClipboardType.IMAGE && fileName != null) {
                            downloadImageFile(fileName, body.clipboard)
                        } else {
                            body.clipboard
                        }

                        val contentHash = ClipboardItem.generateContentHash(content, type, fileName)

                        // 检查是否与最后同步的内容相同（防止循环）
                        if (contentHash == lastSyncedContentHash) {
                            return@withContext Result.success(null)
                        }

                        // 检查是否已存在相同内容的项目
                        val existingItems = clipboardDao.getItemsByContentHash(contentHash)

                        val item = if (existingItems.isNotEmpty()) {
                            // 如果已存在,更新为合并项目,但不改变时间戳
                            val existing = existingItems.first()
                            val updatedItem = existing.copy(
                                source = if (existing.source == ClipboardSource.LOCAL) ClipboardSource.MERGED else ClipboardSource.REMOTE,
                                isSynced = true
                            )
                            clipboardDao.updateItem(updatedItem)
                            lastSyncedContentHash = contentHash
                            updatedItem
                        } else {
                            // 创建新的远程项目
                            val newItem = ClipboardItem(
                                id = UUID.randomUUID().toString(),
                                content = content,
                                type = type,
                                timestamp = System.currentTimeMillis(),
                                fileName = fileName,
                                isSynced = true,
                                source = ClipboardSource.REMOTE,
                                contentHash = contentHash,
                                lastModified = System.currentTimeMillis(),
                                localPath = if (type == ClipboardType.IMAGE) content else null
                            )

                            // 保存到本地数据库
                            clipboardDao.insertItem(newItem)
                            lastSyncedContentHash = contentHash
                            newItem
                        }

                        // 清理超过限制的数据
                        cleanupExcessItems()

                        Result.success(item)
                    } else {
                        // 服务器返回空内容或无内容,认为成功但无新内容
                        Result.success(null)
                    }
                } else {
                    Result.failure(Exception("获取失败: HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 上传剪贴板内容到服务器
     */
    suspend fun uploadToServer(item: ClipboardItem): Result<ClipboardItem> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                // 记录详细的item信息,用于调试
                Log.d(TAG, "=== 开始上传剪贴板内容 ===")
                Log.d(TAG, "Item ID: ${item.id}")
                Log.d(TAG, "Item Type: ${item.type}")
                Log.d(TAG, "Item Content: ${item.content.take(50)}...")
                Log.d(TAG, "Item FileName: ${item.fileName}")
                Log.d(TAG, "Item LocalPath: ${item.localPath}")
                Log.d(TAG, "Item FileSize: ${item.fileSize}")
                Log.d(TAG, "Item Source: ${item.source}")

                val api = apiService ?: return@withContext Result.failure(
                    Exception("API服务未初始化")
                )

                // 转换为SyncClipboard格式
                val typeString = when (item.type) {
                    ClipboardType.TEXT -> "Text"
                    ClipboardType.IMAGE -> "Image"
                    ClipboardType.FILE -> "File"
                }

                // 对于图片类型,需要先上传文件,然后上传元数据
                Log.d(TAG, "检查图片上传条件:")
                Log.d(TAG, "  - item.type == ClipboardType.IMAGE: ${item.type == ClipboardType.IMAGE}")
                Log.d(TAG, "  - item.localPath != null: ${item.localPath != null}")
                Log.d(TAG, "  - item.fileName != null: ${item.fileName != null}")

                if (item.type == ClipboardType.IMAGE && item.localPath != null && item.fileName != null) {
                    Log.d(TAG, "开始处理图片上传: localPath=${item.localPath}, fileName=${item.fileName}")

                    // 首先计算文件哈希值
                    val file = File(item.localPath)
                    if (!file.exists()) {
                        Log.e(TAG, "图片文件不存在: ${item.localPath}")
                        return@withContext Result.failure(Exception("图片文件不存在: ${item.localPath}"))
                    }

                    Log.d(TAG, "文件存在,开始计算哈希值: ${file.absolutePath}, 大小: ${file.length()} bytes")

                    val fileHash = calculateFileHash(file)
                    if (fileHash.isEmpty()) {
                        Log.e(TAG, "无法计算文件哈希值")
                        return@withContext Result.failure(Exception("无法计算文件哈希值"))
                    }

                    Log.d(TAG, "文件哈希值计算完成: $fileHash")

                    // 上传文件到服务器
                    Log.d(TAG, "开始调用uploadImageFile方法")
                    val uploadSuccess = uploadImageFile(item.localPath, item.fileName)
                    Log.d(TAG, "uploadImageFile方法调用完成,结果: $uploadSuccess")

                    if (!uploadSuccess) {
                        Log.w(TAG, "图片文件上传失败,但继续尝试上传元数据")
                        // 不直接返回失败,而是继续尝试上传元数据
                    } else {
                        Log.d(TAG, "图片文件上传成功: ${item.fileName}")
                    }

                    // 发送元数据,使用文件哈希作为Clipboard内容
                    val request = SyncClipboardRequest(
                        type = typeString,
                        clipboard = fileHash,
                        file = item.fileName
                    )

                    Log.d(TAG, "上传图片元数据: file=$fileHash, filename=${item.fileName}")

                    val response = api.uploadClipboard(request)
                    if (response.isSuccessful) {
                        // 标记为已同步
                        val updatedItem = item.copy(
                            isSynced = true,
                            lastModified = System.currentTimeMillis()
                        )
                        clipboardDao.updateItem(updatedItem)
                        settingsRepository.updateLastSyncTime(System.currentTimeMillis())

                        // 记录最后同步的内容哈希
                        lastSyncedContentHash = item.contentHash

                        Log.d(TAG, "图片元数据上传成功")
                        Result.success(updatedItem)
                    } else {
                        Log.e(TAG, "图片元数据上传失败: HTTP ${response.code()}")
                        Result.failure(Exception("元数据上传失败: HTTP ${response.code()}"))
                    }
                } else {
                    // 文本类型的处理或图片上传条件不满足
                    Log.d(TAG, "进入非图片上传逆辑,直接上传元数据")
                    val request = SyncClipboardRequest(
                        type = typeString,
                        clipboard = item.content,
                        file = item.fileName ?: ""
                    )

                    val response = api.uploadClipboard(request)
                    if (response.isSuccessful) {
                        // 标记为已同步
                        val updatedItem = item.copy(
                            isSynced = true,
                            lastModified = System.currentTimeMillis()
                        )
                        clipboardDao.updateItem(updatedItem)
                        settingsRepository.updateLastSyncTime(System.currentTimeMillis())

                        // 记录最后同步的内容哈希
                        lastSyncedContentHash = item.contentHash

                        Result.success(updatedItem)
                    } else {
                        Result.failure(Exception("上传失败: HTTP ${response.code()}"))
                    }
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                // 处理JSON解析错误（服务器返回空响应）
                // 这种情况下认为上传成功
                val updatedItem = item.copy(
                    isSynced = true,
                    lastModified = System.currentTimeMillis()
                )
                clipboardDao.updateItem(updatedItem)
                settingsRepository.updateLastSyncTime(System.currentTimeMillis())
                lastSyncedContentHash = item.contentHash

                Result.success(updatedItem)
            } catch (e: java.io.EOFException) {
                // 处理EOF异常（服务器返回0字节响应）
                // 这种情况下认为上传成功
                val updatedItem = item.copy(
                    isSynced = true,
                    lastModified = System.currentTimeMillis()
                )
                clipboardDao.updateItem(updatedItem)
                settingsRepository.updateLastSyncTime(System.currentTimeMillis())
                lastSyncedContentHash = item.contentHash

                Result.success(updatedItem)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取未同步的项目
     */
    suspend fun getUnsyncedItems(): List<ClipboardItem> = withContext(Dispatchers.IO) {
        clipboardDao.getUnsyncedItems()
    }

    /**
     * 删除剪贴板项目
     */
    suspend fun deleteItem(item: ClipboardItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 如果是图片类型,先清理本地缓存文件
            if (item.type == ClipboardType.IMAGE && !item.localPath.isNullOrEmpty()) {
                cleanupImageFileCache(item.localPath)
            }

            // 从数据库中删除记录
            clipboardDao.deleteItem(item)

            Log.d(TAG, "已删除剪贴板项目: ${item.id}, 类型: ${item.type}")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "删除剪贴板项目时出错", e)
            Result.failure(e)
        }
    }

    /**
     * 测试服务器连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val api = apiService ?: return@withContext Result.failure(
                Exception("API服务未初始化")
            )

            // 尝试获取 SyncClipboard.json 特征文件
            Log.d(TAG, "测试服务器连接")
            val response = api.getClipboard()

            if (response.isSuccessful) {
                // 检查响应内容是否为有效的SyncClipboard格式
                try {
                    val body = response.body()
                    if (body != null) {
                        // 如果能成功解析为SyncClipboardResponse对象，说明服务端返回了正确的API格式
                        Log.d(TAG, "服务器连接成功，返回有效的SyncClipboard格式数据")
                        settingsRepository.updateConnectionStatus(true)
                        Result.success(true)
                    } else {
                        // 响应成功但内容为空，可能不是SyncClipboard服务器
                        settingsRepository.updateConnectionStatus(false)
                        Result.failure(Exception("仅支持 SyncClipboard 服务端API,该地址的 SyncClipboard.json 文件格式不正确"))
                    }
                } catch (e: com.google.gson.JsonSyntaxException) {
                    // JSON解析失败，说明返回的不是有效的SyncClipboard格式
                    settingsRepository.updateConnectionStatus(false)
                    Log.e(TAG, "服务器返回的内容不是有效的JSON格式", e)
                    Result.failure(Exception("仅支持 SyncClipboard 服务端API,该地址返回的内容不是有效的JSON格式"))
                }
            } else {
                settingsRepository.updateConnectionStatus(false)
                when (response.code()) {
                    401 -> Result.failure(Exception("认证失败,请检查用户名和密码"))
                    403 -> Result.failure(Exception("访问被拒绝,请检查权限配置"))
                    404 -> Result.failure(Exception("仅支持 SyncClipboard 服务端API,未在该地址找到 SyncClipboard.json 特征文件"))
                    500, 502, 503, 504 -> Result.failure(Exception("服务端错误: HTTP ${response.code()}"))
                    else -> Result.failure(Exception("连接测试失败: HTTP ${response.code()}"))
                }
            }
        } catch (e: com.google.gson.JsonSyntaxException) {
            // 处理JSON解析异常 - 这通常意味着服务器返回的不是JSON格式
            settingsRepository.updateConnectionStatus(false)
            Log.e(TAG, "连接测试时JSON解析失败", e)
            Result.failure(Exception("仅支持 SyncClipboard 服务端API,该地址返回的内容不是有效的JSON格式"))
        } catch (e: java.net.ConnectException) {
            settingsRepository.updateConnectionStatus(false)
            Result.failure(Exception("无法连接到服务端,请检查服务器地址和网络连接"))
        } catch (e: java.net.SocketTimeoutException) {
            settingsRepository.updateConnectionStatus(false)
            Result.failure(Exception("连接超时,请检查网络状况或服务端响应速度"))
        } catch (e: javax.net.ssl.SSLException) {
            settingsRepository.updateConnectionStatus(false)
            Result.failure(Exception("SSL 证书验证失败,如使用自签名证书请启用\"信任不安全的SSL\"选项:\n$e"))
        } catch (e: java.net.UnknownHostException) {
            settingsRepository.updateConnectionStatus(false)
            Result.failure(Exception("无法解析服务器地址,请检查地址是否正确"))
        } catch (e: Exception) {
            settingsRepository.updateConnectionStatus(false)
            Log.e(TAG, "连接测试时出错", e)
            Result.failure(e)
        }
    }

    /**
     * 主动清理超出限制的数据（当设置更改时调用）
     */
    suspend fun forceCleanupExcessData() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始主动清理超出限制的数据...")
            cleanupExcessItems()

            // 同时清理图片缓存
            cleanupImageCache()

            Log.d(TAG, "主动清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "主动清理时出错", e)
        }
    }

    /**
     * 清理旧数据
     */
    suspend fun cleanupOldData(beforeTimestamp: Long) = withContext(Dispatchers.IO) {
        clipboardDao.deleteOldItems(beforeTimestamp)
    }

    /**
     * 智能清理图片缓存
     * 删除不在历史记录中的图片文件
     */
    suspend fun cleanupImageCache() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始清理图片缓存...")

            // 获取所有存在的剪贴板图片记录的本地路径
            val existingImageItems = clipboardDao.getImageItems()
            val validImagePaths = existingImageItems
                .filter { it.type == ClipboardType.IMAGE && !it.localPath.isNullOrEmpty() }
                .mapNotNull { it.localPath }
                .toSet()

            Log.d(TAG, "有效图片路径数量: ${validImagePaths.size}")
            Log.d(TAG, "有效路径样例: ${validImagePaths.take(3)}")

            // 清理主要的图片缓存目录
            val imageCacheDir = File(context.cacheDir, "images")
            if (imageCacheDir.exists()) {
                cleanupCacheDirectory(imageCacheDir, validImagePaths, "images")
            }

            // 清理剪贴板缓存目录 - 使用更严格的策略
            val clipboardCacheDir = File(context.cacheDir, "clipboard_cache")
            if (clipboardCacheDir.exists()) {
                cleanupClipboardCacheDirectory(clipboardCacheDir, validImagePaths)
            }

            Log.d(TAG, "图片缓存清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理图片缓存时出错", e)
        }
    }

    /**
     * 清理指定缓存目录
     */
    private suspend fun cleanupCacheDirectory(
        cacheDir: File,
        validPaths: Set<String>,
        dirName: String
    ) = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: return@withContext
            var deletedCount = 0
            var totalSize = 0L

            for (file in files) {
                if (file.isFile) {
                    val filePath = file.absolutePath
                    val fileSize = file.length()

                    // 检查文件是否在有效路径列表中
                    if (!validPaths.contains(filePath)) {
                        // 额外检查：如果是超过7天的文件,即使在记录中也删除（防止僵尸文件）
                        val isOldFile = System.currentTimeMillis() - file.lastModified() > 7 * 24 * 60 * 60 * 1000L

                        if (file.delete()) {
                            deletedCount++
                            totalSize += fileSize
                            Log.d(TAG, "删除无效缓存文件: $filePath (${formatFileSize(fileSize)})")
                        } else {
                            Log.w(TAG, "无法删除文件: $filePath")
                        }
                    } else if (System.currentTimeMillis() - file.lastModified() > 7 * 24 * 60 * 60 * 1000L) {
                        // 删除超过7天的文件（即使在记录中,可能是僵尸记录）
                        if (file.delete()) {
                            deletedCount++
                            totalSize += fileSize
                            Log.d(TAG, "删除过期缓存文件: $filePath (${formatFileSize(fileSize)})")
                        }
                    }
                }
            }

            Log.d(TAG, "缓存目录 $dirName 清理完成: 删除 $deletedCount 个文件,释放 ${formatFileSize(totalSize)}")
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存目录 $dirName 时出错", e)
        }
    }

    /**
     * 清理剪贴板缓存目录 - 使用更严格的策略
     * clipboard_cache目录中的文件是临时性的,应该更积极地清理
     */
    private suspend fun cleanupClipboardCacheDirectory(
        cacheDir: File,
        validPaths: Set<String>
    ) = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: return@withContext
            var deletedCount = 0
            var totalSize = 0L
            val currentTime = System.currentTimeMillis()

            Log.d(TAG, "clipboard_cache目录中共有 ${files.size} 个文件")

            for (file in files) {
                if (file.isFile) {
                    val filePath = file.absolutePath
                    val fileSize = file.length()
                    val fileAge = currentTime - file.lastModified()
                    val isOldFile = fileAge > 24 * 60 * 60 * 1000L // 超过24小时
                    val isInValidPaths = validPaths.contains(filePath)

                    // 对clipboard_cache清理：
                    // 1. 删除不在数据库记录中的文件
                    // 2. 删除超过24小时的文件
                    if (!isInValidPaths || isOldFile) {
                        if (file.delete()) {
                            deletedCount++
                            totalSize += fileSize
                            val reason = if (!isInValidPaths) "无记录" else "过期(${fileAge / (60 * 60 * 1000)}小时)"
                            Log.d(TAG, "删除clipboard_cache文件: ${file.name} (${formatFileSize(fileSize)}) - 原因: $reason")
                        } else {
                            Log.w(TAG, "无法删除clipboard_cache文件: $filePath")
                        }
                    } else {
                        Log.d(TAG, "保留clipboard_cache文件: ${file.name} (有记录且未过期)")
                    }
                }
            }

            Log.d(TAG, "clipboard_cache目录清理完成: 删除 $deletedCount 个文件,释放 ${formatFileSize(totalSize)}")
        } catch (e: Exception) {
            Log.e(TAG, "清理clipboard_cache目录时出错", e)
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)}MB"
            else -> "${size / (1024 * 1024 * 1024)}GB"
        }
    }

    /**
     * 清理指定图片文件的缓存
     * 当删除历史记录时调用
     */
    suspend fun cleanupImageFileCache(localPath: String?) = withContext(Dispatchers.IO) {
        if (localPath.isNullOrEmpty()) return@withContext

        try {
            val file = File(localPath)
            if (file.exists()) {
                val fileSize = file.length()
                if (file.delete()) {
                    Log.d(TAG, "删除图片缓存文件: $localPath (${formatFileSize(fileSize)})")
                } else {
                    Log.w(TAG, "无法删除图片缓存文件: $localPath")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除图片缓存文件时出错: $localPath", e)
        }
    }

    /**
     * 获取项目数量
     */
    suspend fun getItemCount(): Int = withContext(Dispatchers.IO) {
        clipboardDao.getItemCount()
    }

    /**
     * 下载图片文件并保存到本地
     */
    private suspend fun downloadImageFile(fileName: String, expectedHash: String): String {
        return try {
            val api = apiService ?: throw Exception("网络服务未初始化")

            // 检查本地是否已存在该文件
            val cacheDir = File(context.cacheDir, "images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val localFile = File(cacheDir, fileName)

            // 如果本地文件存在且哈希值匹配,直接返回本地路径
            if (localFile.exists()) {
                val localHash = calculateFileHash(localFile)
                if (localHash == expectedHash) {
                    return localFile.absolutePath
                }
            }

            // 下载文件
            val response = api.downloadFile(fileName)
            if (response.isSuccessful) {
                response.body()?.let { responseBody ->
                    val inputStream = responseBody.byteStream()
                    val outputStream = localFile.outputStream()

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 验证下载的文件哈希值
                    val downloadedHash = calculateFileHash(localFile)
                    if (downloadedHash == expectedHash) {
                        localFile.absolutePath
                    } else {
                        Log.w(TAG, "下载的图片哈希值不匹配: 预期=$expectedHash, 实际=$downloadedHash")
                        localFile.absolutePath // 仍然返回路径,但记录警告
                    }
                } ?: expectedHash // 如果下载失败,返回哈希值
            } else {
                Log.e(TAG, "下载图片失败: HTTP ${response.code()}")
                expectedHash // 下载失败时返回哈希值
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载图片时出错", e)
            expectedHash // 发生异常时返回哈希值
        }
    }

    /**
     * 上传图片文件到服务器
     */
    private suspend fun uploadImageFile(localPath: String, fileName: String): Boolean {
        Log.d(TAG, "=== 开始上传图片文件 ===")
        Log.d(TAG, "localPath: $localPath")
        Log.d(TAG, "fileName: $fileName")

        return try {
            val api = apiService
            if (api == null) {
                Log.e(TAG, "API服务未初始化")
                return false
            }

            val file = File(localPath)
            if (!file.exists()) {
                Log.e(TAG, "要上传的图片文件不存在: $localPath")
                return false
            }

            Log.d(TAG, "文件存在,大小: ${file.length()} bytes")

            Log.d(TAG, "检查服务器上是否已存在文件: $fileName")

            // 检查服务器上是否已存在该文件
            val checkResponse = api.checkFile(fileName)
            Log.d(TAG, "文件检查响应: HTTP ${checkResponse.code()}")

            if (checkResponse.isSuccessful) {
                Log.d(TAG, "图片文件已存在于服务器: $fileName")
                return true
            } else if (checkResponse.code() != 404) {
                // 如果不是404错误,说明可能有其他问题
                Log.w(TAG, "检查文件存在性时出错: HTTP ${checkResponse.code()}")
            } else {
                Log.d(TAG, "文件不存在,需要上传: $fileName")
            }

            // 上传文件
            Log.d(TAG, "开始上传图片文件: $fileName, 大小: ${file.length()} bytes")

            val requestBody = okhttp3.RequestBody.create(
                "image/*".toMediaType(),
                file
            )

            Log.d(TAG, "请求体创建完成,开始发送PUT请求")

            val uploadResponse = api.uploadFile(fileName, requestBody)
            val success = uploadResponse.isSuccessful

            Log.d(TAG, "文件上传响应: HTTP ${uploadResponse.code()}")

            if (success) {
                Log.d(TAG, "图片文件上传成功: $fileName")
            } else {
                Log.e(TAG, "图片文件上传失败: HTTP ${uploadResponse.code()}")
                // 记录响应体信息以便调试
                try {
                    val errorBody = uploadResponse.errorBody()?.string()
                    if (!errorBody.isNullOrEmpty()) {
                        Log.e(TAG, "上传错误响应: $errorBody")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取错误响应时出错", e)
                }
            }

            Log.d(TAG, "=== 图片文件上传结束,结果: $success ===")
            success
        } catch (e: Exception) {
            Log.e(TAG, "上传图片文件时出错", e)
            false
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
            ""
        }
    }
}

