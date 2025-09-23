package com.jacksen168.syncclipboard.data.api

import com.jacksen168.syncclipboard.data.model.GithubRelease
import retrofit2.Response
import retrofit2.http.GET

/**
 * Github API 接口
 */
interface GithubApiService {
    
    /**
     * 获取最新的 Release 信息
     */
    @GET("/repos/jacksen168sub/SyncClipboard-Android/releases/latest")
    suspend fun getLatestRelease(): Response<GithubRelease>
}