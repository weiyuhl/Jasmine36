package com.lhzkml.jasmine.core.rag

import com.lhzkml.jasmine.core.prompt.llm.SystemContextProvider

/**
 * RAG 知识库上下文提供者
 *
 * 根据用户消息 (query) 做向量检索，将相关文档注入 system prompt。
 * 需在 SystemContextCollector 中注册；仅在 RAG 启用且 query 非空时返回内容。
 */
class RagContextProvider(
    private val embeddingService: EmbeddingService,
    private val knowledgeIndex: KnowledgeIndex,
    private val config: () -> RagConfig
) : SystemContextProvider {

    override val name = "rag_context"

    override suspend fun getContextSection(query: String?): String? {
        val cfg = config()
        if (!cfg.enabled || query.isNullOrBlank()) return null

        val vector = embeddingService.embed(query.trim()) ?: return null
        val topK = cfg.topK.coerceIn(1, 32)
        val libraryIds = cfg.activeLibraryIds.takeIf { it.isNotEmpty() }
        val chunks = knowledgeIndex.search(vector, topK, libraryIds)

        val filtered = cfg.minScoreThreshold?.let { threshold ->
            chunks.filter { it.score <= threshold }
        } ?: chunks

        if (filtered.isEmpty()) return null

        val formatted = filtered.joinToString("\n\n") { sc ->
            buildString {
                append("<chunk")
                if (sc.chunk.sourceId.isNotEmpty()) append(" source=\"${sc.chunk.sourceId.escapeXml()}\"")
                append(" score=\"${"%.4f".format(sc.score)}\">\n")
                append(sc.chunk.content.trim())
                append("\n</chunk>")
            }
        }

        return "<rag_context>\n" +
            "以下是与用户问题相关的知识库内容，供参考：\n\n" +
            formatted +
            "\n</rag_context>"
    }

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
