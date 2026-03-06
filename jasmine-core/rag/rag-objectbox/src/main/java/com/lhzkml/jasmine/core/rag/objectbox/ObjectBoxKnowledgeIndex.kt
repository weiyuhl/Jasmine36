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

    override suspend fun deleteByLibraryId(libraryId: String) = withContext(Dispatchers.IO) {
        val query = box.query(KnowledgeChunkEntity_.libraryId.equal(libraryId)).build()
        val toRemove = query.findIds().toList()
        query.close()
        box.removeByIds(toRemove)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        box.removeAll()
    }

    override suspend fun search(queryVector: FloatArray, topK: Int, libraryIds: Set<String>?): List<ScoredChunk> =
        withContext(Dispatchers.IO) {
            val baseCondition = KnowledgeChunkEntity_.embedding.nearestNeighbors(queryVector, topK)
            val condition = when {
                libraryIds.isNullOrEmpty() -> baseCondition
                else -> baseCondition.and(KnowledgeChunkEntity_.libraryId.oneOf(libraryIds!!.toTypedArray()))
            }
            val query = box.query(condition).build()
            query.findWithScores().map { scored ->
                ScoredChunk(scored.get().toChunk(), scored.score.toDouble())
            }
        }

    override suspend fun count(libraryId: String?): Long = withContext(Dispatchers.IO) {
        if (libraryId == null) box.count()
        else box.query(KnowledgeChunkEntity_.libraryId.equal(libraryId)).build().use { it.count() }
    }
}
