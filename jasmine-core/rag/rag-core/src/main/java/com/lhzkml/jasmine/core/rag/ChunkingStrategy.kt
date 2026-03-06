package com.lhzkml.jasmine.core.rag

/**
 * 分块策略接口
 * 将长文本拆分为适合 embedding 的块。
 */
interface ChunkingStrategy {
    /**
     * 将文本分块
     * @param sourceId 来源标识（如文件路径）
     * @param content 原始文本
     * @return 分块列表，每个块包含 content 和 metadata（JSON，含 startLine、endLine、chunkIndex 等）
     */
    fun chunk(sourceId: String, content: String): List<ChunkResult>
}

/**
 * 分块结果
 */
data class ChunkResult(
    val content: String,
    val metadata: String
)

/**
 * 按固定行数分块
 * @param maxLinesPerChunk 每块最大行数
 * @param overlapLines 块间重叠行数（保持上下文连贯）
 */
class LineChunkingStrategy(
    private val maxLinesPerChunk: Int = 50,
    private val overlapLines: Int = 5
) : ChunkingStrategy {

    override fun chunk(sourceId: String, content: String): List<ChunkResult> {
        val lines = content.lines()
        if (lines.isEmpty()) return emptyList()

        val step = (maxLinesPerChunk - overlapLines).coerceAtLeast(1)
        val result = mutableListOf<ChunkResult>()
        var start = 0
        var chunkIndex = 0

        while (start < lines.size) {
            val end = minOf(start + maxLinesPerChunk, lines.size)
            val chunkLines = lines.subList(start, end)
            val chunkContent = chunkLines.joinToString("\n")
            val escapedId = sourceId.replace("\\", "\\\\").replace("\"", "\\\"")
            val metadata = """{"sourceId":"$escapedId","startLine":$start,"endLine":$end,"chunkIndex":$chunkIndex}"""
            result.add(ChunkResult(chunkContent, metadata))
            start += step
            chunkIndex++
        }

        return result
    }
}
