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
        Logger.d("PermissionManager", "检查通知权限")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Logger.d("PermissionManager", "通知权限状态: $hasPermission")
            hasPermission
        } else {
            Logger.d("PermissionManager", "Android 13以下默认有通知权限")
            true // Android 13以下默认有通知权限
        }
    }
    
    /**
     * 请求通知权限
     */
    fun requestNotificationPermission(activity: Activity) {
        Logger.d("PermissionManager", "请求通知权限")
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
        Logger.d("PermissionManager", "检查电池优化设置")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            Logger.d("PermissionManager", "电池优化忽略状态: $isIgnoring")
            isIgnoring
        } else {
            Logger.d("PermissionManager", "Android 6.0以下没有电池优化")
            true // Android 6.0以下没有电池优化
        }
    }
    
    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        Logger.d("PermissionManager", "请求忽略电池优化")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATION)
                Logger.d("PermissionManager", "启动电池优化请求页面")
            } catch (e: Exception) {
                Logger.w("PermissionManager", "直接跳转电池优化请求页面失败", e)
                // 如果直接跳转失败，则跳转到电池优化设置页面
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivity(intent)
                    Logger.d("PermissionManager", "启动电池优化设置页面")
                } catch (e2: Exception) {
                    Logger.w("PermissionManager", "跳转电池优化设置页面失败", e2)
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
        Logger.d("PermissionManager", "打开应用设置页面")
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Logger.d("PermissionManager", "成功打开应用设置页面")
        } catch (e: Exception) {
            Logger.w("PermissionManager", "打开应用设置页面失败", e)
            // 如果无法打开应用设置，则打开系统设置
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Logger.d("PermissionManager", "打开系统设置页面")
            } catch (e2: Exception) {
                Logger.e("PermissionManager", "打开系统设置页面失败", e2)
                // 忽略异常
            }
        }
    }
    
    /**
     * 检查自启动权限（针对不同厂商）
     */
    fun hasAutoStartPermission(context: Context): Boolean {
        Logger.d("PermissionManager", "检查自启动权限")
        // 这个权限通常无法通过代码检查，需要用户手动设置
        // 这里返回true，实际应用中可以提示用户手动设置
        Logger.d("PermissionManager", "自启动权限无法通过代码检查，默认返回true")
        return true
    }
    
    /**
     * 打开自启动设置页面（针对不同厂商）
     */
    fun openAutoStartSettings(context: Context) {
        Logger.d("PermissionManager", "打开自启动设置页面")
        val manufacturer = Build.MANUFACTURER.lowercase()
        Logger.d("PermissionManager", "设备制造商: $manufacturer")
        
        try {
            val intent = when {
                manufacturer.contains("xiaomi") -> {
                    Logger.d("PermissionManager", "小米设备，打开自启动管理")
                    // 小米自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                manufacturer.contains("oppo") -> {
                    Logger.d("PermissionManager", "OPPO设备，打开自启动管理")
                    // OPPO自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.FakeActivity"
                        )
                    }
                }
                manufacturer.contains("vivo") -> {
                    Logger.d("PermissionManager", "VIVO设备，打开自启动管理")
                    // VIVO自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    Logger.d("PermissionManager", "华为/荣耀设备，打开自启动管理")
                    // 华为/荣耀自启动管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                    }
                }
                manufacturer.contains("samsung") -> {
                    Logger.d("PermissionManager", "三星设备，打开设备管理")
                    // 三星设备管理
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    }
                }
                else -> {
                    Logger.d("PermissionManager", "其他设备，打开应用设置")
                    // 默认打开应用设置
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Logger.d("PermissionManager", "成功启动设置页面")
            
        } catch (e: Exception) {
            Logger.w("PermissionManager", "打开特定设置页面失败", e)
            // 如果无法打开特定设置页面，则打开应用设置
            openAppSettings(context)
        }
    }
    
    /**
     * 检查所有必要权限
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        Logger.d("PermissionManager", "检查所有必要权限")
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
        Logger.d("PermissionManager", "获取缺失的权限列表")
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
        
        Logger.d("PermissionManager", "缺失权限列表: $missing")
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