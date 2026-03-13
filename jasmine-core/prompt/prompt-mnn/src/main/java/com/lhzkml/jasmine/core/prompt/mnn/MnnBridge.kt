package com.lhzkml.jasmine.core.prompt.mnn

import android.util.Log

/**
 * MNN 桥接类 - 负责加载 MNN 库和提供基础功能
 */
object MnnBridge {
    private const val TAG = "MnnBridge"
    
    private var isLoaded = false
    
    init {
        try {
            System.loadLibrary("jasmine_mnn")
            System.loadLibrary("MNN")
            isLoaded = true
            Log.d(TAG, "✅ MNN libraries loaded successfully")
            Log.d(TAG, "MNN Version: ${getMnnVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "❌ Failed to load MNN libraries", e)
            isLoaded = false
        }
    }
    
    fun isAvailable(): Boolean = isLoaded
    
    /**
     * 获取 MNN 版本
     */
    external fun getMnnVersion(): String
    
    /**
     * 测试 MNN 初始化
     */
    external fun testMnnInit(): Boolean
}
