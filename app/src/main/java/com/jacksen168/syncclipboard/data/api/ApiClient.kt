package com.jacksen168.syncclipboard.data.api

import com.google.gson.GsonBuilder
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络客户端工厂类
 */
object ApiClient {
    
    /**
     * 验证URL是否有效
     */
    fun validateUrl(url: String): Result<String> {
        return try {
            val validUrl = validateAndNormalizeUrl(url)
            Result.success(validUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建Retrofit实例
     */
    fun createApiService(
        baseUrl: String,
        username: String = "",
        password: String = ""
    ): SyncClipboardApi {
        val gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .setLenient()
            .create()
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                
                // 添加Basic Auth认证
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    val credentials = Credentials.basic(username, password)
                    requestBuilder.header("Authorization", credentials)
                }
                
                chain.proceed(requestBuilder.build())
            }
            .authenticator(BasicAuthenticator(username, password))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(validateAndNormalizeUrl(baseUrl)))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        return retrofit.create(SyncClipboardApi::class.java)
    }
    
    /**
     * 创建日志拦截器
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (com.jacksen168.syncclipboard.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }
    
    /**
     * 确保URL以斜杠结尾
     */
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
    
    /**
     * 验证并规范化 URL
     */
    private fun validateAndNormalizeUrl(url: String): String {
        val trimmedUrl = url.trim()
        
        // 如果为空，返回空
        if (trimmedUrl.isEmpty()) {
            throw IllegalArgumentException("URL 不能为空")
        }
        
        // 如果没有协议，添加 http://
        val normalizedUrl = if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            "http://$trimmedUrl"
        } else {
            trimmedUrl
        }
        
        // 验证 URL 格式
        try {
            val httpUrl = normalizedUrl.toHttpUrl()
            return httpUrl.toString()
        } catch (e: Exception) {
            throw IllegalArgumentException("无效的 URL 格式: $trimmedUrl", e)
        }
    }
}

/**
 * Basic认证器
 */
class BasicAuthenticator(
    private val username: String,
    private val password: String
) : Authenticator {
    
    override fun authenticate(route: Route?, response: Response): Request? {
        // 如果已经尝试过认证，则不再重试
        if (response.request.header("Authorization") != null) {
            return null
        }
        
        // 添加Basic Auth认证
        if (username.isNotEmpty() && password.isNotEmpty()) {
            val credentials = Credentials.basic(username, password)
            return response.request.newBuilder()
                .header("Authorization", credentials)
                .build()
        }
        
        return null
    }
}