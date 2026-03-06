package com.lhzkml.jasmine.core.rag.objectbox

import com.lhzkml.jasmine.core.rag.KnowledgeChunk
import com.lhzkml.jasmine.core.rag.ScoredChunk
import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

/**
 * ObjectBox 知识块实体
 * 使用 HNSW 向量索引，维度需与 EmbeddingService.dimensions 一致。
 */
@Entity
data class KnowledgeChunkEntity(
    @Id var id: Long = 0,
    var sourceId: String = "",
    var content: String = "",
    var metadata: String = "{}",
    @HnswIndex(dimensions = 384) var embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KnowledgeChunkEntity
        return id == other.id && sourceId == other.sourceId && content == other.content &&
            metadata == other.metadata && (embedding?.contentEquals(other.embedding ?: return false) ?: (other.embedding == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

internal fun KnowledgeChunkEntity.toChunk() = KnowledgeChunk(
    id = id,
    sourceId = sourceId,
    content = content,
    metadata = metadata,
    embedding = embedding
)

internal fun KnowledgeChunk.toEntity() = KnowledgeChunkEntity(
    id = id,
    sourceId = sourceId,
    content = content,
    metadata = metadata,
    embedding = embedding
)
