package com.lhzkml.jasmine.core.rag.objectbox

import com.lhzkml.jasmine.core.rag.KnowledgeChunk
import com.lhzkml.jasmine.core.rag.KnowledgeIndex
import com.lhzkml.jasmine.core.rag.ScoredChunk
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 ObjectBox 的知识向量索引实现
 */
class ObjectBoxKnowledgeIndex(private val store: BoxStore) : KnowledgeIndex {

    private val box = store.boxFor<KnowledgeChunkEntity>()

    override suspend fun insert(chunk: KnowledgeChunk) = withContext(Dispatchers.IO) {
        box.put(chunk.toEntity())
        Unit
    }

    override suspend fun insertAll(chunks: List<KnowledgeChunk>) = withContext(Dispatchers.IO) {
        box.put(chunks.map { it.toEntity() })
        Unit
    }

    override suspend fun deleteBySourceId(sourceId: String) = withContext(Dispatchers.IO) {
        val query = box.query(KnowledgeChunkEntity_.sourceId.equal(sourceId)).build()
        val toRemove = query.findIds().toList()
        query.close()
        box.removeByIds(toRemove)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        box.removeAll()
    }

    override suspend fun search(queryVector: FloatArray, topK: Int): List<ScoredChunk> =
        withContext(Dispatchers.IO) {
            val query = box.query(
                KnowledgeChunkEntity_.embedding.nearestNeighbors(queryVector, topK)
            ).build()
            query.findWithScores().map { scored ->
                ScoredChunk(scored.get().toChunk(), scored.score.toDouble())
            }
        }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        box.count()
    }
}
