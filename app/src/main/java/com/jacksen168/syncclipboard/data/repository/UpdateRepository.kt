package com.jacksen168.syncclipboard.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.jacksen168.syncclipboard.data.api.ApiClient
import com.jacksen168.syncclipboard.data.api.GithubApiService
import com.jacksen168.syncclipboard.data.model.UpdateInfo
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
        ApiClient.createGithubService()
    }
    
    /**
     * 检查应用更新
     */
    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion()
            
            val response = githubApi.getLatestRelease()
            
            if (response.isSuccessful) {
                val release = response.body()
                if (release != null) {
                    val latestVersion = release.tagName.removePrefix("v")
                    
                    // 查找APK下载链接，确保是签名版本(-signed.apk)
                    val signedApkAsset = release.assets.find { asset ->
                        asset.name.endsWith("-signed.apk", ignoreCase = true)
                    }
                    
                    if (signedApkAsset != null) {
                        Logger.i(TAG, "找到签名APK: ${signedApkAsset.name}")
                    } else {
                        Logger.w(TAG, "未找到签名APK (-signed.apk)")
                    }
                    
                    val downloadUrl = signedApkAsset?.downloadUrl ?: release.htmlUrl
                    
                    // 只有找到签名APK时才标记为有更新
                    val hasUpdate = if (signedApkAsset != null) {
                        val isNewer = isNewerVersion(latestVersion, currentVersion)
                        isNewer
                    } else {
                        false // 没有签名APK，不提示更新
                    }
                    
                    // 格式化发布日期
                    val releaseDate = formatReleaseDate(release.publishedAt)

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
        
        return@withContext UpdateInfo.noUpdate(getCurrentVersion())
    }
    
    /**
     * 获取当前应用版本
     */
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = packageInfo.versionName ?: "1.0.0"
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
        return try {
            val latest = parseVersion(latestVersion)
            val current = parseVersion(currentVersion)
            
            for (i in 0 until maxOf(latest.size, current.size)) {
                val latestPart = latest.getOrNull(i) ?: 0
                val currentPart = current.getOrNull(i) ?: 0
                
                when {
                    latestPart > currentPart -> {
                        return true
                    }
                    latestPart < currentPart -> {
                        return false
                    }
                }
            }
            // 数字比较版本相同,判断当前已安装版本是否是非正式版(带"-"): true-需要更新,false-不需要更新
            return currentVersion.contains("-")
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