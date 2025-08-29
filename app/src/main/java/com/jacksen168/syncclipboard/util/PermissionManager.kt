package com.jacksen168.syncclipboard.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限管理工具类
 */
object PermissionManager {
    
    const val REQUEST_CODE_NOTIFICATION = 1001
    const val REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATION = 1002
    
    /**
     * 检查通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13以下默认有通知权限
        }
    }
    
    /**
     * 请求通知权限
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION
            )
        }
    }
    
    /**
     * 检查是否忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Android 6.0以下没有电池优化
        }
    }
    
    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATION)
            } catch (e: Exception) {
                // 如果直接跳转失败，则跳转到电池优化设置页面
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivity(intent)
                } catch (e2: Exception) {
                    // 最后尝试跳转到应用设置页面
                    openAppSettings(activity)
                }
            }
        }
    }
    
    /**
     * 打开应用设置页面
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果无法打开应用设置，则打开系统设置
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // 忽略异常
            }
        }
    }
    
    /**
     * 检查自启动权限（针对不同厂商）
     */
    fun hasAutoStartPermission(context: Context): Boolean {
        // 这个权限通常无法通过代码检查，需要用户手动设置
        // 这里返回true，实际应用中可以提示用户手动设置
        return true
    }
    
    /**
     * 打开自启动设置页面（针对不同厂商）
     */
    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        try {
            val intent = when {
                manufacturer.contains("xiaomi") -> {
                    // 小米自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                manufacturer.contains("oppo") -> {
                    // OPPO自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.FakeActivity"
                        )
                    }
                }
                manufacturer.contains("vivo") -> {
                    // VIVO自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    // 华为/荣耀自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                    }
                }
                manufacturer.contains("samsung") -> {
                    // 三星设备管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    }
                }
                else -> {
                    // 默认打开应用设置
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            
        } catch (e: Exception) {
            // 如果无法打开特定设置页面，则打开应用设置
            openAppSettings(context)
        }
    }
    
    /**
     * 检查所有必要权限
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        return PermissionStatus(
            hasNotification = hasNotificationPermission(context),
            hasIgnoreBatteryOptimization = isIgnoringBatteryOptimizations(context),
            hasAutoStart = hasAutoStartPermission(context)
        )
    }
    
    /**
     * 获取缺失的权限列表
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        val status = checkAllPermissions(context)
        
        if (!status.hasNotification) {
            missing.add("通知权限")
        }
        
        if (!status.hasIgnoreBatteryOptimization) {
            missing.add("电池优化白名单")
        }
        
        if (!status.hasAutoStart) {
            missing.add("自启动权限")
        }
        
        return missing
    }
}

/**
 * 权限状态数据类
 */
data class PermissionStatus(
    val hasNotification: Boolean = false,
    val hasIgnoreBatteryOptimization: Boolean = false,
    val hasAutoStart: Boolean = false
) {
    val allGranted: Boolean
        get() = hasNotification && hasIgnoreBatteryOptimization && hasAutoStart
}