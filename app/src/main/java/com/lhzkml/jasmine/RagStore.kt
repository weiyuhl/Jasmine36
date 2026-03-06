package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.core.rag.EmbeddingService
import com.lhzkml.jasmine.core.rag.IndexingService
import com.lhzkml.jasmine.core.rag.KnowledgeIndex
import com.lhzkml.jasmine.core.rag.RagConfig
import com.lhzkml.jasmine.core.rag.RagContextProvider
import com.lhzkml.jasmine.core.rag.embedding.ApiEmbeddingService
import com.lhzkml.jasmine.core.rag.embedding.EmbeddingRequestConfig
import com.lhzkml.jasmine.core.rag.objectbox.MyObjectBox
import com.lhzkml.jasmine.core.rag.objectbox.ObjectBoxKnowledgeIndex
import io.objectbox.BoxStore

/**
 * RAG 知识库组件入口
 *
 * 持有 ObjectBox BoxStore、KnowledgeIndex、EmbeddingService，
 * 在 RAG 启用时提供 RagContextProvider。
 */
object RagStore {

    private var boxStore: BoxStore? = null
    private var knowledgeIndex: KnowledgeIndex? = null

    fun initialize(context: Context) {
        if (boxStore != null) return
        boxStore = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
        knowledgeIndex = boxStore?.let { ObjectBoxKnowledgeIndex(it) }
    }

    /** 获取知识索引（用于知识库内容管理：查看、删除、编辑） */
    fun getKnowledgeIndex(): KnowledgeIndex? = knowledgeIndex

    /**
     * 构建 RagContextProvider，若 RAG 未启用或配置不完整则返回 null
     */
    fun buildRagContextProvider(configProvider: () -> RagConfig): RagContextProvider? {
        val config = configProvider()
        if (!config.enabled) return null
        if (config.embeddingBaseUrl.isBlank() || config.embeddingApiKey.isBlank()) return null

        val index = knowledgeIndex ?: return null
        val embeddingConfig = EmbeddingRequestConfig(
            baseUrl = config.embeddingBaseUrl,
            apiKey = config.embeddingApiKey,
            model = config.embeddingModel,
            dimensions = 384
        )
        val embeddingService: EmbeddingService = ApiEmbeddingService(embeddingConfig)

        return RagContextProvider(embeddingService, index, configProvider)
    }

    /**
     * 构建 IndexingService，用于工作区文件索引。
     * 若 RAG 未启用或配置不完整则返回 null。
     */
    fun buildIndexingService(configProvider: () -> RagConfig): IndexingService? {
        val config = configProvider()
        if (!config.enabled) return null
        if (config.embeddingBaseUrl.isBlank() || config.embeddingApiKey.isBlank()) return null

        val index = knowledgeIndex ?: return null
        val embeddingConfig = EmbeddingRequestConfig(
            baseUrl = config.embeddingBaseUrl,
            apiKey = config.embeddingApiKey,
            model = config.embeddingModel,
            dimensions = 384
        )
        val embeddingService: EmbeddingService = ApiEmbeddingService(embeddingConfig)
        return IndexingService(embeddingService, index)
    }
}
