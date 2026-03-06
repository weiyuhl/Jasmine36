package com.lhzkml.jasmine.core.rag.embedding

/**
 * Embedding API 调用配置
 * 由 app 层从 ProviderManager 获取并传入。
 */
data class EmbeddingRequestConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String = "text-embedding-3-small",
    val dimensions: Int = 384
)
