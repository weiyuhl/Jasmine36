package com.lhzkml.jasmine.wakelock

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 电池优化豁免辅助类
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimization"

    /**
     * 检查是否已豁免电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // Android 6.0 以下无需检查
        }

        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check battery optimization status: ${e.message}")
            false
        }
    }

    /**
     * 请求豁免电池优化
     * 
     * 注意：需要在 AndroidManifest.xml 中声明权限：
     * <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "Already ignoring battery optimizations")
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Requested battery optimization exemption")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization exemption: ${e.message}")
            // 降级到设置页面
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * 打开电池优化设置页面
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Opened battery optimization settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings: ${e.message}")
        }
    }
}
