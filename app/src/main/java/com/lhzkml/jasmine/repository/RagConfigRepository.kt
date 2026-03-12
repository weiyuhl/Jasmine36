package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.RagLibraryConfig

/**
 * RAG 配置 Repository
 *
 * 负责：
 * - RAG 开关
 * - TopK
 * - Embedding BaseUrl / ApiKey / Model / UseLocal / ModelPath
 * - Libraries 元数据
 * - Active LibraryIds
 * - IndexableExtensions
 *
 * 对应页面：
 * - RagConfigActivity
 * - EmbeddingConfigActivity
 * - SettingsActivity 中 RAG 摘要
 * - ChatViewModel 中 RAG 上下文启用配置
 *
 * 说明：
 * - 配置属于这个 Repository
 * - 具体知识库内容增删查、索引任务放到 RagLibraryRepository
 */
interface RagConfigRepository {
    
    // RAG 开关
    fun isRagEnabled(): Boolean
    fun setRagEnabled(enabled: Boolean)
    
    // TopK
    fun getRagTopK(): Int
    fun setRagTopK(topK: Int)
    
    // Embedding 配置
    fun getRagEmbeddingBaseUrl(): String
    fun setRagEmbeddingBaseUrl(url: String)
    fun getRagEmbeddingApiKey(): String
    fun setRagEmbeddingApiKey(key: String)
    fun getRagEmbeddingModel(): String
    fun setRagEmbeddingModel(model: String)
    fun getRagEmbeddingUseLocal(): Boolean
    fun setRagEmbeddingUseLocal(useLocal: Boolean)
    fun getRagEmbeddingModelPath(): String
    fun setRagEmbeddingModelPath(path: String)
    
    // Libraries 元数据
    fun getRagLibraries(): List<RagLibraryConfig>
    fun setRagLibraries(libraries: List<RagLibraryConfig>)
    
    // Active LibraryIds
    fun getRagActiveLibraryIds(): Set<String>
    fun setRagActiveLibraryIds(ids: Set<String>)
    
    // Indexable Extensions
    fun getRagIndexableExtensions(): Set<String>
    fun setRagIndexableExtensions(extensions: Set<String>)
}

class DefaultRagConfigRepository(
    private val configRepo: ConfigRepository
) : RagConfigRepository {
    
    override fun isRagEnabled(): Boolean = configRepo.isRagEnabled()
    
    override fun setRagEnabled(enabled: Boolean) {
        configRepo.setRagEnabled(enabled)
    }
    
    override fun getRagTopK(): Int = configRepo.getRagTopK()
    
    override fun setRagTopK(topK: Int) {
        configRepo.setRagTopK(topK)
    }
    
    override fun getRagEmbeddingBaseUrl(): String = configRepo.getRagEmbeddingBaseUrl()
    
    override fun setRagEmbeddingBaseUrl(url: String) {
        configRepo.setRagEmbeddingBaseUrl(url)
    }
    
    override fun getRagEmbeddingApiKey(): String = configRepo.getRagEmbeddingApiKey()
    
    override fun setRagEmbeddingApiKey(key: String) {
        configRepo.setRagEmbeddingApiKey(key)
    }
    
    override fun getRagEmbeddingModel(): String = configRepo.getRagEmbeddingModel()
    
    override fun setRagEmbeddingModel(model: String) {
        configRepo.setRagEmbeddingModel(model)
    }
    
    override fun getRagEmbeddingUseLocal(): Boolean = configRepo.getRagEmbeddingUseLocal()
    
    override fun setRagEmbeddingUseLocal(useLocal: Boolean) {
        configRepo.setRagEmbeddingUseLocal(useLocal)
    }
    
    override fun getRagEmbeddingModelPath(): String = configRepo.getRagEmbeddingModelPath()
    
    override fun setRagEmbeddingModelPath(path: String) {
        configRepo.setRagEmbeddingModelPath(path)
    }
    
    override fun getRagLibraries(): List<RagLibraryConfig> = configRepo.getRagLibraries()
    
    override fun setRagLibraries(libraries: List<RagLibraryConfig>) {
        configRepo.setRagLibraries(libraries)
    }
    
    override fun getRagActiveLibraryIds(): Set<String> = configRepo.getRagActiveLibraryIds()
    
    override fun setRagActiveLibraryIds(ids: Set<String>) {
        configRepo.setRagActiveLibraryIds(ids)
    }
    
    override fun getRagIndexableExtensions(): Set<String> = configRepo.getRagIndexableExtensions()
    
    override fun setRagIndexableExtensions(extensions: Set<String>) {
        configRepo.setRagIndexableExtensions(extensions)
    }
}
