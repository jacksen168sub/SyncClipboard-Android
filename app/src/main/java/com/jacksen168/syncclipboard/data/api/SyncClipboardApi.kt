package com.jacksen168.syncclipboard.data.api

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
}