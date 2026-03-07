package com.lhzkml.jasmine

import android.content.Context
import android.util.Log
import com.lhzkml.jasmine.core.rag.EmbeddingService
import com.lhzkml.jasmine.core.rag.IndexingService
import com.lhzkml.jasmine.core.rag.KnowledgeIndex
import com.lhzkml.jasmine.core.rag.RagConfig
import com.lhzkml.jasmine.core.rag.RagContextProvider
import com.lhzkml.jasmine.core.rag.embedding.ApiEmbeddingService
import com.lhzkml.jasmine.core.rag.embedding.EmbeddingRequestConfig
import com.lhzkml.jasmine.core.rag.embedding.PaddingEmbeddingService
import com.lhzkml.jasmine.core.rag.objectbox.MyObjectBox
import com.lhzkml.jasmine.core.rag.objectbox.ObjectBoxKnowledgeIndex
import com.lhzkml.jasmine.mnn.MnnEmbeddingService
import io.objectbox.BoxStore
import io.objectbox.BoxStoreBuilder

/**
 * RAG 知识库组件入口
 *
 * 持有 ObjectBox BoxStore、KnowledgeIndex、EmbeddingService，
 * 在 RAG 启用时提供 RagContextProvider。
 * 支持远程 API 与本地 MNN 两种 Embedding 模式。
 */
object RagStore {

    private const val TAG = "RagStore"
    private const val INDEX_DIMENSIONS = 1024

    private var boxStore: BoxStore? = null
    private var knowledgeIndex: KnowledgeIndex? = null

    @Volatile
    private var cachedMnnEmbedding: MnnEmbeddingService? = null
    @Volatile
    private var cachedMnnModelPath: String? = null

    /** 上次 Embedding 初始化失败的原因，用于向用户展示更具体的错误提示 */
    @Volatile
    var lastEmbeddingFailureReason: String? = null
        private set

    /** Embedding 向量维度从 384 升级到 1024 时的 schema 版本 */
    private const val RAG_EMBEDDING_SCHEMA_VERSION = 2
    private const val PREFS_RAG = "rag_store"
    private const val KEY_SCHEMA_VERSION = "embedding_schema_version"

    fun initialize(context: Context) {
        if (boxStore != null) return
        val appContext = context.applicationContext
        // 384->1024 维升级：旧数据不兼容，需清空 ObjectBox 后重建
        val prefs = appContext.getSharedPreferences(PREFS_RAG, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SCHEMA_VERSION, 1) < RAG_EMBEDDING_SCHEMA_VERSION) {
            try {
                BoxStore.deleteAllFiles(appContext, BoxStoreBuilder.DEFAULT_NAME)
                Log.i(TAG, "Cleared ObjectBox for schema migration (384->1024 dims)")
            } catch (e: Exception) {
                Log.e(TAG, "Schema migration cleanup failed", e)
            }
            prefs.edit().putInt(KEY_SCHEMA_VERSION, RAG_EMBEDDING_SCHEMA_VERSION).apply()
        }
        boxStore = MyObjectBox.builder()
            .androidContext(appContext)
            .build()
        knowledgeIndex = boxStore?.let { ObjectBoxKnowledgeIndex(it) }
    }

    /** 获取知识索引（用于知识库内容管理：查看、删除、编辑） */
    fun getKnowledgeIndex(): KnowledgeIndex? = knowledgeIndex

    private fun getOrCreateEmbeddingService(config: RagConfig): EmbeddingService? {
        lastEmbeddingFailureReason = null
        return when {
            config.useLocalEmbedding && config.embeddingModelPath.isNotBlank() -> {
                val path = config.embeddingModelPath.trim()
                if (cachedMnnModelPath != path) {
                    cachedMnnEmbedding?.release()
                    cachedMnnEmbedding = null
                    cachedMnnModelPath = null
                }
                try {
                    var service = cachedMnnEmbedding
                    if (service == null) {
                        service = MnnEmbeddingService(path)
                        cachedMnnEmbedding = service
                        cachedMnnModelPath = path
                    }
                    PaddingEmbeddingService(service, INDEX_DIMENSIONS)
                } catch (e: Exception) {
                    val msg = "本地 Embedding 模型加载失败: ${e.message}"
                    Log.e(TAG, msg, e)
                    lastEmbeddingFailureReason = msg
                    cachedMnnEmbedding = null
                    cachedMnnModelPath = null
                    null
                }
            }
            config.embeddingBaseUrl.isNotBlank() && config.embeddingApiKey.isNotBlank() -> {
                PaddingEmbeddingService(
                    ApiEmbeddingService(
                        EmbeddingRequestConfig(
                            baseUrl = config.embeddingBaseUrl,
                            apiKey = config.embeddingApiKey,
                            model = config.embeddingModel,
                            dimensions = INDEX_DIMENSIONS
                        )
                    ),
                    INDEX_DIMENSIONS
                )
            }
            else -> {
                lastEmbeddingFailureReason = when {
                    config.useLocalEmbedding && config.embeddingModelPath.isBlank() ->
                        "已选择本地模式但未选择模型，请前往「Embedding 服务」选择模型"
                    !config.useLocalEmbedding && (config.embeddingBaseUrl.isBlank() || config.embeddingApiKey.isBlank()) ->
                        "已选择远程 API 但未配置完整，请填写 API 地址和 Key"
                    else ->
                        "请前往「设置」-「模型供应商」-「Embedding 服务」配置"
                }
                null
            }
        }
    }

    /**
     * 构建 RagContextProvider，若 RAG 未启用或配置不完整则返回 null
     */
    fun buildRagContextProvider(configProvider: () -> RagConfig): RagContextProvider? {
        val config = configProvider()
        if (!config.enabled) return null

        val index = knowledgeIndex ?: return null
        val embeddingService = getOrCreateEmbeddingService(config) ?: return null

        return RagContextProvider(embeddingService, index, configProvider)
    }

    /**
     * 构建 IndexingService，用于工作区文件索引。
     * 若 RAG 未启用或配置不完整则返回 null。
     * 失败时可通过 lastEmbeddingFailureReason 获取具体原因。
     */
    fun buildIndexingService(configProvider: () -> RagConfig): IndexingService? {
        lastEmbeddingFailureReason = null
        val config = configProvider()
        if (!config.enabled) {
            lastEmbeddingFailureReason = "请先在「RAG 知识库」中启用 RAG"
            return null
        }
        val index = knowledgeIndex
        if (index == null) {
            lastEmbeddingFailureReason = "索引未初始化，请重启应用"
            return null
        }
        val embeddingService = getOrCreateEmbeddingService(config) ?: return null

        return IndexingService(embeddingService, index)
    }

    /** 释放缓存的 MNN Embedding 服务（配置切换时调用） */
    fun releaseCachedMnnEmbedding() {
        cachedMnnEmbedding?.release()
        cachedMnnEmbedding = null
        cachedMnnModelPath = null
    }
}
