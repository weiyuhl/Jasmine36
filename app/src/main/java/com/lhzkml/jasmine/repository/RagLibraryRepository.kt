package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.RagStore
import com.lhzkml.jasmine.core.rag.KnowledgeIndex
import com.lhzkml.jasmine.core.rag.SourceSummary

/**
 * RAG 知识库内容 Repository
 *
 * 负责：
 * - 获取知识索引
 * - 浏览库内容
 * - 删除条目
 * - 触发索引任务
 * - 返回索引错误原因
 *
 * 对应页面：
 * - RagLibraryContentActivity
 * - RagConfigActivity 中手动索引/工作区索引
 */
interface RagLibraryRepository {
    
    /**
     * 获取知识索引实例
     */
    fun getKnowledgeIndex(): KnowledgeIndex?
    
    /**
     * 获取指定库的所有来源摘要
     */
    suspend fun listSources(libraryId: String): List<SourceSummary>
    
    /**
     * 删除指定来源的所有文档
     */
    suspend fun deleteBySourceId(sourceId: String)
    
    /**
     * 删除指定库的所有文档
     */
    suspend fun deleteByLibraryId(libraryId: String)
    
    /**
     * 获取上次 Embedding 初始化失败的原因
     */
    fun getLastEmbeddingFailureReason(): String?
}

class DefaultRagLibraryRepository : RagLibraryRepository {
    
    override fun getKnowledgeIndex(): KnowledgeIndex? {
        return RagStore.getKnowledgeIndex()
    }
    
    override suspend fun listSources(libraryId: String): List<SourceSummary> {
        val index = getKnowledgeIndex() ?: return emptyList()
        return index.listSources(libraryId)
    }
    
    override suspend fun deleteBySourceId(sourceId: String) {
        val index = getKnowledgeIndex() ?: return
        index.deleteBySourceId(sourceId)
    }
    
    override suspend fun deleteByLibraryId(libraryId: String) {
        val index = getKnowledgeIndex() ?: return
        index.deleteByLibraryId(libraryId)
    }
    
    override fun getLastEmbeddingFailureReason(): String? {
        return RagStore.lastEmbeddingFailureReason
    }
}
