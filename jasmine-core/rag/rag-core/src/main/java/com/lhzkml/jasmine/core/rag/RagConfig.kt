package com.lhzkml.jasmine.core.rag

/**
 * RAG 运行时配置
 * 由 app 层从 ConfigRepository 构建并注入。
 */
data class RagConfig(
    val enabled: Boolean = false,
    val topK: Int = 5,
    val minScoreThreshold: Double? = null,
    val knowledgeSourcePath: String = "",
    val embeddingBaseUrl: String = "",
    val embeddingApiKey: String = "",
    val embeddingModel: String = "text-embedding-3-small"
)
