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
    val embeddingModel: String = "text-embedding-3-small",
    /** 是否使用本地 MNN Embedding */
    val useLocalEmbedding: Boolean = false,
    /** 本地 MNN Embedding 模型路径（当 useLocalEmbedding 为 true 时使用） */
    val embeddingModelPath: String = "",
    /** 参与检索的知识库 ID 集合，空表示检索全部 */
    val activeLibraryIds: Set<String> = emptySet()
)
