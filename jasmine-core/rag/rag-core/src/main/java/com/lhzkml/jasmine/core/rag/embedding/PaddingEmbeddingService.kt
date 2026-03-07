package com.lhzkml.jasmine.core.rag.embedding

import com.lhzkml.jasmine.core.rag.EmbeddingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将任意维度的 Embedding 输出适配到目标维度。
 * 不足则补零，超出则截断。支持 384、512、768、1024 等常见模型。
 */
class PaddingEmbeddingService(
    private val delegate: EmbeddingService,
    private val targetDimensions: Int
) : EmbeddingService {

    override val dimensions: Int get() = targetDimensions

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        delegate.embed(text)?.let { adapt(it) }
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.Default) {
        delegate.embedBatch(texts).map { it?.let { vec -> adapt(vec) } }
    }

    private fun adapt(vec: FloatArray): FloatArray {
        return when {
            vec.size == targetDimensions -> vec
            vec.size < targetDimensions -> FloatArray(targetDimensions) { if (it < vec.size) vec[it] else 0f }
            else -> vec.copyOf(targetDimensions)
        }
    }
}
