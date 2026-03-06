package com.lhzkml.jasmine.core.rag

/**
 * 向量嵌入服务接口
 * 将文本转换为固定维度的 Float 向量，用于语义检索。
 */
interface EmbeddingService {
    /** 向量维度，必须与 KnowledgeIndex 中索引的 dimensions 一致 */
    val dimensions: Int

    /**
     * 将单条文本转换为向量
     */
    suspend fun embed(text: String): FloatArray?

    /**
     * 批量嵌入，可用于索引加速
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray?>
}
