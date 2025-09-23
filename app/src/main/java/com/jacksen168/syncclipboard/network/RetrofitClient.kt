package com.jacksen168.syncclipboard.network

import com.google.gson.GsonBuilder
import com.jacksen168.syncclipboard.data.api.GithubApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客户端配置
 */
object RetrofitClient {
    
    private const val GITHUB_BASE_URL = "https://api.github.com"
    
    /**
     * OkHttp 客户端配置
     */
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }
    
    /**
     * Gson 配置
     */
    private val gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }
    
    /**
     * Github API Retrofit 实例
     */
    private val githubRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * 创建 Github API 服务
     */
    fun createGithubService(): GithubApiService {
        return githubRetrofit.create(GithubApiService::class.java)
    }
}