package com.jacksen168.syncclipboard.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jacksen168.syncclipboard.R
import com.jacksen168.syncclipboard.data.model.AppSettings
import com.jacksen168.syncclipboard.data.model.ServerConfig
import com.jacksen168.syncclipboard.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

// 扩展属性创建DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置存储仓库
 */
class SettingsRepository(private val context: Context) {
    
    private val dataStore = context.dataStore
    
    companion object {
        // 服务器配置键
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_USERNAME_KEY = stringPreferencesKey("server_username")
        private val SERVER_PASSWORD_KEY = stringPreferencesKey("server_password")
        private val TRUST_UNSAFE_SSL_KEY = booleanPreferencesKey("trust_unsafe_ssl")
        private val IS_CONNECTED_KEY = booleanPreferencesKey("is_connected")
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
        
        // 应用设置键
        private val AUTO_SYNC_KEY = booleanPreferencesKey("auto_sync")
        private val SYNC_INTERVAL_KEY = longPreferencesKey("sync_interval")
        private val SYNC_ON_BOOT_KEY = booleanPreferencesKey("sync_on_boot")
        private val SHOW_NOTIFICATIONS_KEY = booleanPreferencesKey("show_notifications")
        private val DEVICE_NAME_KEY = stringPreferencesKey("device_name")
        private val CLIPBOARD_HISTORY_COUNT_KEY = intPreferencesKey("clipboard_history_count")
        private val LOG_DISPLAY_COUNT_KEY = intPreferencesKey("log_display_count")
        private val HIDE_IN_RECENTS_KEY = booleanPreferencesKey("hide_in_recents")
        private val REWRITE_AFTER_UNLOCK_KEY = booleanPreferencesKey("rewrite_after_unlock")
        private val FOREGROUND_SERVICE_KEEPALIVE_KEY = booleanPreferencesKey("foreground_service_keepalive")
        private val DOWNLOAD_LOCATION_KEY = stringPreferencesKey("download_location")
        private val AUTO_SAVE_FILES_KEY = booleanPreferencesKey("auto_save_files")
        
        // 密码加密密钥（简单的对称加密）
        private const val ENCRYPTION_KEY = "jascksen168_SyncClipboard"
    }
    
    /**
     * 获取服务器配置Flow
     */
    val serverConfigFlow: Flow<ServerConfig> = dataStore.data.map { preferences ->
        ServerConfig(
            url = preferences[SERVER_URL_KEY] ?: "",
            username = preferences[SERVER_USERNAME_KEY] ?: "",
            password = decryptPassword(preferences[SERVER_PASSWORD_KEY] ?: ""),
            trustUnsafeSSL = preferences[TRUST_UNSAFE_SSL_KEY] ?: false,
            isConnected = preferences[IS_CONNECTED_KEY] ?: false,
            lastSyncTime = preferences[LAST_SYNC_TIME_KEY] ?: 0L
        )
    }
    
    /**
     * 获取应用设置Flow
     */
    val appSettingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            autoSync = preferences[AUTO_SYNC_KEY] ?: true,
            syncInterval = preferences[SYNC_INTERVAL_KEY] ?: 3000L,
            syncOnBoot = preferences[SYNC_ON_BOOT_KEY] ?: false,
            showNotifications = preferences[SHOW_NOTIFICATIONS_KEY] ?: false,
            deviceName = preferences[DEVICE_NAME_KEY] ?: getDefaultDeviceName(),
            clipboardHistoryCount = preferences[CLIPBOARD_HISTORY_COUNT_KEY] ?: 10,
            logDisplayCount = preferences[LOG_DISPLAY_COUNT_KEY] ?: -1,
            hideInRecents = preferences[HIDE_IN_RECENTS_KEY] ?: false,
            rewriteAfterUnlock = preferences[REWRITE_AFTER_UNLOCK_KEY] ?: true,
            foregroundServiceKeepalive = preferences[FOREGROUND_SERVICE_KEEPALIVE_KEY] ?: false,
            downloadLocation = preferences[DOWNLOAD_LOCATION_KEY] ?: "",
            autoSaveFiles = preferences[AUTO_SAVE_FILES_KEY] ?: false
        )
    }
    
    /**
     * 保存服务器配置
     */
    suspend fun saveServerConfig(config: ServerConfig) {
        Logger.d("SettingsRepository", "开始保存服务器配置: url=${config.url}, username=${config.username}")
        dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = config.url
            preferences[SERVER_USERNAME_KEY] = config.username
            preferences[SERVER_PASSWORD_KEY] = encryptPassword(config.password)
            preferences[TRUST_UNSAFE_SSL_KEY] = config.trustUnsafeSSL
            preferences[IS_CONNECTED_KEY] = config.isConnected
            preferences[LAST_SYNC_TIME_KEY] = config.lastSyncTime
        }
        Logger.d("SettingsRepository", "服务器配置保存完成")
    }
    
    /**
     * 保存应用设置
     */
    suspend fun saveAppSettings(settings: AppSettings) {
        Logger.d("SettingsRepository", "开始保存应用设置")
        dataStore.edit { preferences ->
            preferences[AUTO_SYNC_KEY] = settings.autoSync
            preferences[SYNC_INTERVAL_KEY] = settings.syncInterval
            preferences[SYNC_ON_BOOT_KEY] = settings.syncOnBoot
            preferences[SHOW_NOTIFICATIONS_KEY] = settings.showNotifications
            preferences[DEVICE_NAME_KEY] = settings.deviceName
            preferences[CLIPBOARD_HISTORY_COUNT_KEY] = settings.clipboardHistoryCount
            preferences[LOG_DISPLAY_COUNT_KEY] = settings.logDisplayCount
            preferences[HIDE_IN_RECENTS_KEY] = settings.hideInRecents
            preferences[REWRITE_AFTER_UNLOCK_KEY] = settings.rewriteAfterUnlock
            preferences[FOREGROUND_SERVICE_KEEPALIVE_KEY] = settings.foregroundServiceKeepalive
            preferences[DOWNLOAD_LOCATION_KEY] = settings.downloadLocation
            preferences[AUTO_SAVE_FILES_KEY] = settings.autoSaveFiles
        }
        Logger.d("SettingsRepository", "应用设置保存完成")
    }
    
    /**
     * 更新连接状态
     */
    suspend fun updateConnectionStatus(isConnected: Boolean) {
        Logger.d("SettingsRepository", "更新连接状态: $isConnected")
        dataStore.edit { preferences ->
            preferences[IS_CONNECTED_KEY] = isConnected
        }
        Logger.d("SettingsRepository", "连接状态更新完成")
    }
    
    /**
     * 更新最后同步时间
     */
    suspend fun updateLastSyncTime(timestamp: Long) {
        Logger.d("SettingsRepository", "更新最后同步时间: $timestamp")
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = timestamp
        }
        Logger.d("SettingsRepository", "最后同步时间更新完成")
    }
    
    /**
     * 获取默认设备名称
     */
    private fun getDefaultDeviceName(): String {
        return try {
            android.os.Build.MODEL ?: "Android Device"
        } catch (e: Exception) {
            "Android Device"
        }
    }
    
    /**
     * 加密密码（简单的AES加密）
     */
    private fun encryptPassword(password: String): String {
        if (password.isEmpty()) return ""
        
        return try {
            val key = MessageDigest.getInstance("MD5").digest(ENCRYPTION_KEY.toByteArray())
            val cipher = Cipher.getInstance("AES/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val encrypted = cipher.doFinal(password.toByteArray())
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            password // 如果加密失败，返回原密码
        }
    }
    
    /**
     * 解密密码
     */
    private fun decryptPassword(encryptedPassword: String): String {
        if (encryptedPassword.isEmpty()) return ""
        
        return try {
            val key = MessageDigest.getInstance("MD5").digest(ENCRYPTION_KEY.toByteArray())
            val cipher = Cipher.getInstance("AES/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
            val decrypted = cipher.doFinal(Base64.decode(encryptedPassword, Base64.DEFAULT))
            String(decrypted)
        } catch (e: Exception) {
            encryptedPassword // 如果解密失败，返回原文
        }
    }

    /**
     * 重置数据库
     */
    suspend fun resetDatabase(context: Context) {
        try {
            // 删除数据库文件
            context.deleteDatabase("clipboard_database")
            Logger.d("SettingsRepository", context.getString(R.string.database_reset_log))
        } catch (e: Exception) {
            Logger.e("SettingsRepository", context.getString(R.string.database_reset_error_log), e)
        }
    }
}