package com.lhzkml.jasmine.mnn

import android.util.Log

/**
 * MNN LLM 会话类 - 管理本地 LLM 推理
 */
class MnnLlmSession(
    private val modelPath: String,
    private val config: MnnConfig = MnnConfig()
) {
    private var nativePtr: Long = 0
    private var isInitialized = false
    
    companion object {
        private const val TAG = "MnnLlmSession"
    }
    
    /**
     * 初始化 LLM 会话
     */
    fun init(): Boolean {
        if (!MnnBridge.isAvailable()) {
            Log.e(TAG, "MNN not available")
            return false
        }
        
        try {
            val configJson = config.toJson()
            Log.d(TAG, "Initializing with config: $configJson")
            nativePtr = nativeInit(modelPath, configJson)
            isInitialized = nativePtr != 0L
            Log.d(TAG, "LLM session initialized: $isInitialized, ptr=$nativePtr")
            return isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            return false
        }
    }
    
    /**
     * 生成文本 - 流式输出
     */
    fun generate(prompt: String, onToken: (String) -> Boolean): String {
        if (!isInitialized) {
            throw IllegalStateException("Session not initialized")
        }
        
        return nativeGenerate(nativePtr, prompt, object : GenerateCallback {
            override fun onToken(token: String): Boolean {
                return onToken(token)
            }
        })
    }
    
    /**
     * 生成文本 - 简单版本
     */
    fun generate(prompt: String): String {
        return generate(prompt) { false }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInitialized && nativePtr != 0L) {
            nativeRelease(nativePtr)
            nativePtr = 0
            isInitialized = false
            Log.d(TAG, "LLM session released")
        }
    }
    
    // Native 方法
    private external fun nativeInit(modelPath: String, configJson: String): Long
    private external fun nativeRelease(sessionPtr: Long)
    private external fun nativeGenerate(sessionPtr: Long, prompt: String, callback: GenerateCallback): String
    
    /**
     * 生成回调接口
     */
    interface GenerateCallback {
        /**
         * 每个 token 生成时回调
         * @return true 停止生成，false 继续
         */
        fun onToken(token: String): Boolean
    }
}

/**
 * MNN 配置
 */
data class MnnConfig(
    val maxNewTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val systemPrompt: String = "You are a helpful assistant."
) {
    fun toJson(): String {
        return """
            {
                "max_new_tokens": $maxNewTokens,
                "temperature": $temperature,
                "top_p": $topP,
                "top_k": $topK,
                "system_prompt": "$systemPrompt"
            }
        """.trimIndent()
    }
}
