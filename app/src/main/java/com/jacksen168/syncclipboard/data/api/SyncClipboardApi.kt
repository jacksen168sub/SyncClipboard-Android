package com.jacksen168.syncclipboard.data.api

import com.google.gson.annotations.SerializedName
import com.jacksen168.syncclipboard.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * SyncClipboard API接口
 * 基于 https://github.com/Jeric-X/SyncClipboard 的API设计
 * 使用WebDAV协议
 */
interface SyncClipboardApi {
    
    /**
     * 获取剪贴板内容（文本/图片/文件）
     */
    @GET("SyncClipboard.json")
    suspend fun getClipboard(): Response<SyncClipboardResponse>
    
    /**
     * 上传剪贴板内容（文本/图片/文件）
     */
    @PUT("SyncClipboard.json")
    suspend fun uploadClipboard(
        @Body request: SyncClipboardRequest
    ): Response<SyncClipboardResponse>
    
    /**
     * 检查文件是否存在
     */
    @HEAD("file/{filename}")
    suspend fun checkFile(
        @Path("filename") filename: String
    ): Response<Void>
    
    /**
     * 下载文件
     */
    @GET("file/{filename}")
    suspend fun downloadFile(
        @Path("filename") filename: String
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 上传文件
     */
    @PUT("file/{filename}")
    suspend fun uploadFile(
        @Path("filename") filename: String,
        @Body file: okhttp3.RequestBody
    ): Response<Void>
    
    /**
     * 测试连接 - 尝试访问根路径
     */
    @GET(".")
    suspend fun ping(): Response<okhttp3.ResponseBody>
    
    // ==================== 历史记录相关接口（v3.1.1+）====================
    
    /**
     * 获取历史记录
     */
    @GET("api/history/{profileId}")
    suspend fun getHistory(
        @Path("profileId") profileId: String
    ): Response<HistoryRecordDto>
    
    /**
     * 获取历史记录数据
     */
    @GET("api/history/{profileId}/data")
    suspend fun getHistoryData(
        @Path("profileId") profileId: String
    ): Response<okhttp3.ResponseBody>
    
    /**
     * 查询历史记录列表
     */
    @POST("api/history/query")
    @Multipart
    suspend fun queryHistory(
        @Part page: MultipartBody.Part? = null,
        @Part before: MultipartBody.Part? = null,
        @Part after: MultipartBody.Part? = null,
        @Part types: MultipartBody.Part? = null,
        @Part searchText: MultipartBody.Part? = null,
        @Part starred: MultipartBody.Part? = null,
        @Part sortByLastAccessed: MultipartBody.Part? = null
    ): Response<List<HistoryRecordDto>>
    
    /**
     * 创建历史记录
     */
    @POST("api/history")
    @Multipart
    suspend fun createHistory(
        @Part("hash") hash: RequestBody,
        @Part("type") type: RequestBody,
        @Part("text") text: RequestBody? = null,
        @Part("size") size: RequestBody? = null,
        @Part data: MultipartBody.Part? = null
    ): Response<Unit>
    
    /**
     * 更新历史记录
     */
    @PATCH("api/history/{type}/{hash}")
    suspend fun updateHistory(
        @Path("type") type: String,
        @Path("hash") hash: String,
        @Body update: HistoryRecordUpdateDto
    ): Response<Unit>
    
    /**
     * 获取服务器时间
     */
    @GET("api/time")
    suspend fun getServerTime(): Response<String>
    
    /**
     * 获取服务器版本
     */
    @GET("api/version")
    suspend fun getServerVersion(): Response<String>
    
    /**
     * 获取历史记录统计信息
     */
    @GET("api/history/statistics")
    suspend fun getHistoryStatistics(): Response<HistoryStatisticsDto>
    
    /**
     * 清除历史记录
     */
    @DELETE("api/history/clear")
    suspend fun clearHistory(): Response<Unit>
    
    /**
     * 删除文件
     */
    @DELETE("file")
    suspend fun deleteFile(): Response<Void>
}

/**
 * 历史记录更新数据类
 */
data class HistoryRecordUpdateDto(
    @SerializedName("starred")
    val starred: Boolean? = null,
    
    @SerializedName("pinned")
    val pinned: Boolean? = null,
    
    @SerializedName("isDelete")
    val isDelete: Boolean? = null,
    
    @SerializedName("version")
    val version: Int? = null,
    
    @SerializedName("lastModified")
    val lastModified: String? = null, // ISO 8601 格式
    
    @SerializedName("lastAccessed")
    val lastAccessed: String? = null // ISO 8601 格式
)

/**
 * 历史记录统计信息数据类
 */
data class HistoryStatisticsDto(
    @SerializedName("activeCount")
    val activeCount: Int = 0,
    
    @SerializedName("starredCount")
    val starredCount: Int = 0,
    
    @SerializedName("deletedCount")
    val deletedCount: Int = 0,
    
    @SerializedName("totalCount")
    val totalCount: Int = 0,
    
    @SerializedName("totalFileSizeMB")
    val totalFileSizeMB: Double = 0.0
)