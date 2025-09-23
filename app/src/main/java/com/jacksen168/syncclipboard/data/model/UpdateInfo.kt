package com.jacksen168.syncclipboard.data.model

import com.google.gson.annotations.SerializedName

/**
 * Github Release 响应数据模型
 */
data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("body")
    val body: String,
    @SerializedName("published_at")
    val publishedAt: String,
    @SerializedName("html_url")
    val htmlUrl: String,
    @SerializedName("assets")
    val assets: List<GithubAsset>,
    @SerializedName("prerelease")
    val prerelease: Boolean
)

/**
 * Github Release Asset 数据模型
 */
data class GithubAsset(
    @SerializedName("name")
    val name: String,
    @SerializedName("browser_download_url")
    val downloadUrl: String,
    @SerializedName("size")
    val size: Long
)

/**
 * 应用更新信息
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseDate: String,
    val isPrerelease: Boolean = false
) {
    companion object {
        fun noUpdate(currentVersion: String) = UpdateInfo(
            hasUpdate = false,
            latestVersion = currentVersion,
            currentVersion = currentVersion,
            releaseNotes = "",
            downloadUrl = "",
            releaseDate = ""
        )
    }
}