package com.jacksen168.syncclipboard.work

import android.content.Context
import androidx.work.*
import com.jacksen168.syncclipboard.data.repository.ClipboardRepository
import com.jacksen168.syncclipboard.data.repository.SettingsRepository
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager工厂类
 */
class SyncWorkManagerFactory(
    private val settingsRepository: SettingsRepository
) : WorkerFactory() {
    
    override fun createWorker(
        appContext: Context, 
        workerClassName: String, 
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> 
                SyncWorker(appContext, workerParameters, settingsRepository)
            else -> null
        }
    }
}

/**
 * 同步工作者
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {
    
    private val clipboardRepository = ClipboardRepository(context, settingsRepository)
    
    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.appSettingsFlow.first()
            
            // 检查是否启用自动同步
            if (!settings.autoSync) {
                return Result.success()
            }
            
            // 优先级策略：先上传本地未同步的项目，再从服务器获取
            
            // 1. 上传未同步的项目（优先级更高）
            val unsyncedItems = clipboardRepository.getUnsyncedItems()
            var uploadCount = 0
            for (item in unsyncedItems) {
                val result = clipboardRepository.uploadToServer(item)
                if (result.isSuccess) {
                    uploadCount++
                }
            }
            
            // 2. 从服务器获取最新内容
            clipboardRepository.fetchFromServer()
            
            // 3. 记录成功的操作
            if (uploadCount > 0) {
                Logger.d("SyncWorker", "已上传 $uploadCount 个未同步项目")
            }
            
            Result.success()
        } catch (e: Exception) {
            Logger.e("SyncWorker", "同步失败", e)
            if (runAttemptCount < 3) {
                return Result.retry()
            } else {
                return Result.failure()
            }
        }
    }
    
    companion object {
        const val WORK_NAME = "clipboard_sync_work"
        
        /**
         * 调度周期性同步工作
         */
        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("periodic_sync")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                syncRequest
            )
        }
        
        /**
         * 调度一次性同步工作
         */
        fun scheduleOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag("one_time_sync")
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "one_time_sync",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
        
        /**
         * 取消所有同步工作
         */
        fun cancelAllWork(context: Context) {
            WorkManager.getInstance(context).cancelAllWork()
        }
    }
}