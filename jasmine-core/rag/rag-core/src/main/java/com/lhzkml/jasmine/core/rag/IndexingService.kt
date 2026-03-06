package com.lhzkml.jasmine.core.rag

/**
 * 待索引文档
 */
data class IndexDocument(
    val sourceId: String,
    val content: String
)

/**
 * 索引结果
 */
data class IndexingResult(
    val sourceId: String,
    val chunksIndexed: Int,
    val success: Boolean,
    val error: String? = null
)

/**
 * 索引服务
 *
 * 将文档分块、embed、写入 KnowledgeIndex。
 * 调用方负责获取文档内容（如从工作区扫描文件）。
 */
class IndexingService(
    private val embeddingService: EmbeddingService,
    private val knowledgeIndex: KnowledgeIndex,
    private val chunkingStrategy: ChunkingStrategy = LineChunkingStrategy()
) {

    /**
     * 索引单份文档
     */
    suspend fun indexDocument(doc: IndexDocument): IndexingResult {
        return try {
            val chunks = chunkingStrategy.chunk(doc.sourceId, doc.content)
            if (chunks.isEmpty()) return IndexingResult(doc.sourceId, 0, true)

            val texts = chunks.map { it.content }
            val vectors = embeddingService.embedBatch(texts)

            val toInsert = chunks.zip(vectors).mapNotNull { (chunk, vec) ->
                if (vec != null) KnowledgeChunk(
                    sourceId = doc.sourceId,
                    content = chunk.content,
                    metadata = chunk.metadata,
                    embedding = vec
                ) else null
            }
            knowledgeIndex.insertAll(toInsert)
            IndexingResult(doc.sourceId, toInsert.size, true)
        } catch (e: Exception) {
            IndexingResult(doc.sourceId, 0, false, e.message)
        }
    }

    /**
     * 索引多份文档，先删除旧数据再写入
     */
    suspend fun indexDocuments(
        documents: List<IndexDocument>,
        replaceSourceIds: Boolean = true
    ): List<IndexingResult> {
        val sourceIds = documents.map { it.sourceId }.toSet()
        if (replaceSourceIds) {
            sourceIds.forEach { knowledgeIndex.deleteBySourceId(it) }
        }
        return documents.map { indexDocument(it) }
    }

    /**
     * 删除指定来源的索引
     */
    suspend fun removeBySourceId(sourceId: String) {
        knowledgeIndex.deleteBySourceId(sourceId)
    }

    /**
     * 清空全部索引
     */
    suspend fun clear() {
        knowledgeIndex.clear()
    }
}
