package com.lhzkml.jasmine.core.rag

/**
 * 知识向量索引接口
 * 支持插入、删除、相似度检索。
 */
interface KnowledgeIndex {
    /**
     * 插入或更新知识块
     */
    suspend fun insert(chunk: KnowledgeChunk)

    /**
     * 批量插入
     */
    suspend fun insertAll(chunks: List<KnowledgeChunk>)

    /**
     * 按 sourceId 删除（如文件路径变更时）
     */
    suspend fun deleteBySourceId(sourceId: String)

    /**
     * 清空全部
     */
    suspend fun clear()

    /**
     * 向量相似度检索
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param libraryIds 限定检索的知识库 ID 集合，null 或空表示检索全部
     * @return 按相似度排序的 chunks
     */
    suspend fun search(queryVector: FloatArray, topK: Int, libraryIds: Set<String>? = null): List<ScoredChunk>

    /**
     * 按 libraryId 删除（删除整库）
     */
    suspend fun deleteByLibraryId(libraryId: String)

    /** 当前索引的 chunk 数量，可选按 libraryId 统计 */
    suspend fun count(libraryId: String? = null): Long
}

/**
 * 知识块（接口层，不含 ObjectBox 注解）
 */
data class KnowledgeChunk(
    val id: Long = 0,
    val libraryId: String = "default",
    val sourceId: String,
    val content: String,
    val metadata: String = "{}",
    val embedding: FloatArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KnowledgeChunk
        return id == other.id && libraryId == other.libraryId && sourceId == other.sourceId && content == other.content &&
            metadata == other.metadata && (embedding?.contentEquals(other.embedding ?: return false) ?: (other.embedding == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + libraryId.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

data class ScoredChunk(
    val chunk: KnowledgeChunk,
    /** 距离分数，越小越相似（取决于 distanceType） */
    val score: Double
)
