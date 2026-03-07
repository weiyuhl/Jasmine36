package com.lhzkml.jasmine.mnn

import android.util.Log

/**
 * MNN Embedding 会话 - 用于 RAG 知识库的本地向量化
 */
class MnnEmbeddingSession(private val modelPath: String) {

    private var nativePtr: Long = 0
    private var isInitialized = false

    companion object {
        private const val TAG = "MnnEmbeddingSession"
    }

    fun init(): Boolean {
        if (!MnnBridge.isAvailable()) {
            Log.e(TAG, "MNN not available")
            return false
        }
        try {
            nativePtr = nativeInit(modelPath)
            isInitialized = nativePtr != 0L
            if (isInitialized) {
                Log.d(TAG, "Embedding session initialized, dim=$dimensions")
            }
            return isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init embedding", e)
            return false
        }
    }

    val dimensions: Int
        get() = if (isInitialized) nativeGetDimensions(nativePtr) else 0

    fun embed(text: String): FloatArray? {
        if (!isInitialized) return null
        return try {
            nativeEmbedText(nativePtr, text)
        } catch (e: Exception) {
            Log.e(TAG, "embed failed", e)
            null
        }
    }

    fun release() {
        if (nativePtr != 0L) {
            nativeRelease(nativePtr)
            nativePtr = 0
            isInitialized = false
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeEmbedText(ptr: Long, text: String): FloatArray?
    private external fun nativeGetDimensions(ptr: Long): Int
    private external fun nativeRelease(ptr: Long)
}
