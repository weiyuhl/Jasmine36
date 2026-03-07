package com.lhzkml.jasmine.mnn

import com.lhzkml.jasmine.core.rag.EmbeddingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 MNN 本地 Embedding 模型的 EmbeddingService 实现
 * 供 RAG 知识库使用，支持纯离线向量化
 */
class MnnEmbeddingService(
    private val modelPath: String
) : EmbeddingService {

    private val session = MnnEmbeddingSession(modelPath)

    init {
        if (!session.init()) {
            throw IllegalStateException("MNN Embedding 模型初始化失败: $modelPath")
        }
    }

    override val dimensions: Int
        get() = session.dimensions

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        session.embed(text)
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        texts.map { session.embed(it) }
    }

    fun release() {
        session.release()
    }
}
