package com.jacksen168.syncclipboard.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.jacksen168.syncclipboard.data.api.GithubApiService
import com.jacksen168.syncclipboard.data.model.UpdateInfo
import com.jacksen168.syncclipboard.network.RetrofitClient
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 应用更新检查Repository
 */
class UpdateRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateRepository"
    }
    
    private val githubApi: GithubApiService by lazy {
        RetrofitClient.createGithubService()
    }
    
    /**
     * 检查应用更新
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        // Logger.d(TAG, "开始检查应用更新")
        try {
            val currentVersion = getCurrentVersion()
            // Logger.i(TAG, "当前版本: $currentVersion")
            
            // Logger.i(TAG, "开始请求Github API...")
            val response = githubApi.getLatestRelease()
            // Logger.i(TAG, "Github API响应: 状态码=${response.code()}, 成功=${response.isSuccessful}")
            
            if (response.isSuccessful) {
                val release = response.body()
                if (release != null) {
                    // Logger.i(TAG, "Github Release: tagName=${release.tagName}, name=${release.name}")
                    val latestVersion = release.tagName.removePrefix("v")
                    
                    // 查找APK下载链接，确保是签名版本(-signed.apk)
                    val signedApkAsset = release.assets.find { asset ->
                        asset.name.endsWith("-signed.apk", ignoreCase = true)
                    }
                    
//                    // 记录找到的资产信息
//                    release.assets.forEach { asset ->
//                        Logger.i(TAG, "找到资产: ${asset.name}, URL: ${asset.downloadUrl}")
//                    }
                    
                    if (signedApkAsset != null) {
                        // Logger.i(TAG, "找到签名APK: ${signedApkAsset.name}")
                    } else {
                        Logger.w(TAG, "未找到签名APK (-signed.apk)")
                    }
                    
                    val downloadUrl = signedApkAsset?.downloadUrl ?: release.htmlUrl
                    
                    // 只有找到签名APK时才标记为有更新
                    val hasUpdate = if (signedApkAsset != null) {
                        val isNewer = isNewerVersion(latestVersion, currentVersion)
                        // Logger.i(TAG, "版本比较结果: 最新版本=$latestVersion, 当前版本=$currentVersion, 是否更新=$isNewer")
                        isNewer
                    } else {
                        // Logger.i(TAG, "由于未找到签名APK，不提示更新")
                        false // 没有签名APK，不提示更新
                    }
                    
                    // 格式化发布日期
                    val releaseDate = formatReleaseDate(release.publishedAt)
                    
                    // Logger.i(TAG, "最新版本: $latestVersion, 有更新: $hasUpdate, 下载地址: $downloadUrl")

                    // isPreRelease 是否是预发布版的判断依据: 标签是否带-
                    val isPreRelease = release.tagName.contains("-")
                    
                    val updateInfo = UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = latestVersion,
                        currentVersion = currentVersion,
                        releaseNotes = release.body,
                        downloadUrl = downloadUrl,
                        releaseDate = releaseDate,
                        isPrerelease = isPreRelease
                    )
                    
                    // Logger.d(TAG, "更新检查完成: hasUpdate=${updateInfo.hasUpdate}")
                    return@withContext updateInfo
                } else {
                    Logger.w(TAG, "Github API返回空数据")
                }
            } else {
                Logger.w(TAG, "检查更新失败: ${response.code()} ${response.message()}")
                
                // 尝试获取错误响应体
                val errorBody = response.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    Logger.w(TAG, "错误响应: $errorBody")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "检查更新时出错", e)
            // 抛出异常，让ViewModel处理
            throw e
        }
        
        // Logger.d(TAG, "更新检查完成: 无更新")
        return@withContext UpdateInfo.noUpdate(getCurrentVersion())
    }
    
    /**
     * 获取当前应用版本
     */
    private fun getCurrentVersion(): String {
        // Logger.d(TAG, "获取当前应用版本")
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = packageInfo.versionName ?: "1.0.0"
            // Logger.d(TAG, "当前版本: $version")
            version
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.e(TAG, "获取应用版本失败", e)
            "1.0.0"
        }
    }
    
    /**
     * 比较版本号，判断是否有新版本
     */
    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
         // Logger.d(TAG, "比较版本号: latest=$latestVersion, current=$currentVersion")
        return try {
            val latest = parseVersion(latestVersion)
            val current = parseVersion(currentVersion)
            // Logger.d(TAG, "比较版本号(parsed): latest=$latest, current=$current")
            
            for (i in 0 until maxOf(latest.size, current.size)) {
                val latestPart = latest.getOrNull(i) ?: 0
                val currentPart = current.getOrNull(i) ?: 0
                
                when {
                    latestPart > currentPart -> {
                          // Logger.d(TAG, "发现新版本: $latestPart > $currentPart")
                        return true
                    }
                    latestPart < currentPart -> {
                         // Logger.d(TAG, "当前版本较新: $latestPart < $currentPart")
                        return false
                    }
                }
            }
            // 数字比较版本相同,判断当前已安装版本是否是非正式版(带"-"): true-需要更新,false-不需要更新
            return currentVersion.contains("-")

            // Logger.d(TAG, "版本相同")
            false
        } catch (e: Exception) {
            Logger.w(TAG, "版本比较失败: $latestVersion vs $currentVersion", e)
            false
        }
    }

    /**
     * 解析版本号为数字列表
     */
    private fun parseVersion(version: String): List<Int> {
        return version.split(".").mapNotNull { part ->
            try {
                // 移除非数字字符
                part.replace(Regex("[^0-9]"), "").toIntOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 格式化发布日期
     */
    private fun formatReleaseDate(publishedAt: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
            val date = inputFormat.parse(publishedAt)
            date?.let { outputFormat.format(it) } ?: publishedAt
        } catch (e: Exception) {
            publishedAt
        }
    }
}