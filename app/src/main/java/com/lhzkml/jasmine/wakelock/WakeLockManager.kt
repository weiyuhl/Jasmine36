package com.lhzkml.jasmine.wakelock

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

/**
 * WakeLock 管理器
 * 
 * 负责管理 PowerManager.WakeLock 和 WifiManager.WifiLock
 * 防止设备休眠导致长时间任务中断
 */
class WakeLockManager(private val context: Context) {

    private var powerWakeLock: PowerManager.WakeLock? = null
    private var wifiWakeLock: WifiManager.WifiLock? = null
    
    private val listeners = mutableListOf<WakeLockStateListener>()

    /**
     * 检查是否持有 WakeLock
     */
    val isHeld: Boolean
        get() = powerWakeLock != null

    /**
     * 获取 WakeLock（Power + WiFi）
     */
    @SuppressLint("WakelockTimeout")
    fun acquire() {
        if (powerWakeLock != null) {
            Log.d(TAG, "WakeLock already held, ignoring acquire request")
            return
        }

        Log.d(TAG, "Acquiring WakeLocks")

        try {
            // 获取 PowerManager.WakeLock
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "jasmine:wakelock"
            )
            powerWakeLock?.acquire()

            // 获取 WifiManager.WifiLock
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiWakeLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "jasmine"
            )
            wifiWakeLock?.acquire()

            Log.i(TAG, "WakeLocks acquired successfully")
            notifyStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLocks: ${e.message}")
            release()
        }
    }

    /**
     * 释放 WakeLock（Power + WiFi）
     */
    fun release() {
        if (powerWakeLock == null && wifiWakeLock == null) {
            Log.d(TAG, "No WakeLocks held, ignoring release request")
            return
        }

        Log.d(TAG, "Releasing WakeLocks")

        try {
            powerWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
                powerWakeLock = null
            }

            wifiWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
                wifiWakeLock = null
            }

            Log.i(TAG, "WakeLocks released successfully")
            notifyStateChanged(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLocks: ${e.message}")
        }
    }

    /**
     * 切换 WakeLock 状态
     */
    fun toggle() {
        if (isHeld) {
            release()
        } else {
            acquire()
        }
    }

    /**
     * 添加状态监听器
     */
    fun addListener(listener: WakeLockStateListener) {
        listeners.add(listener)
    }

    /**
     * 移除状态监听器
     */
    fun removeListener(listener: WakeLockStateListener) {
        listeners.remove(listener)
    }

    /**
     * 通知状态变化
     */
    private fun notifyStateChanged(isHeld: Boolean) {
        listeners.forEach { it.onWakeLockStateChanged(isHeld) }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        release()
        listeners.clear()
    }

    companion object {
        private const val TAG = "WakeLockManager"
    }
}

/**
 * WakeLock 状态监听器
 */
interface WakeLockStateListener {
    fun onWakeLockStateChanged(isHeld: Boolean)
}
