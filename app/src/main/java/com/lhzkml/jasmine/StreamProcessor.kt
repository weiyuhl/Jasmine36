package com.lhzkml.jasmine

/**
 * Incremental stream processor, inspired by Claude App's of.g (StreamEventProcessor).
 * Manages ContentBlock list and buffers for text/thinking accumulation.
 */
class StreamProcessor {

    private val blocks = mutableListOf<ContentBlock>()
    private val textBuffer = StringBuilder()
    private val thinkingBuffer = StringBuilder()
    private val logBuilder = StringBuilder()
    private var inThinking = false

    fun onChunk(chunk: String): StreamUpdate {
        if (inThinking) {
            flushThinking()
        }
        textBuffer.append(chunk)
        return snapshot()
    }

    fun onThinking(text: String): StreamUpdate {
        if (!inThinking) {
            flushText()
            inThinking = true
        }
        thinkingBuffer.append(text)
        logBuilder.append(if (thinkingBuffer.length == text.length) "[Think] $text" else text)
        return snapshot()
    }

    fun onToolCallStart(toolName: String, arguments: String): StreamUpdate {
        flushAll()
        val argsPreview = if (arguments.length > 80) arguments.take(80) + "…" else arguments
        blocks.add(ContentBlock.ToolCall(toolName, argsPreview))
        logBuilder.append("\n[Tool] 调用工具: $toolName($argsPreview)\n")
        return snapshot()
    }

    fun onToolCallResult(toolName: String, result: String): StreamUpdate {
        flushAll()
        val preview = if (result.length > 200) result.take(200) + "…" else result
        blocks.add(ContentBlock.ToolResult(toolName, preview))
        logBuilder.append("[Result] $toolName 结果: $preview\n\n")
        return snapshot()
    }

    fun onPlan(goal: String, steps: List<String>): StreamUpdate {
        flushAll()
        blocks.add(ContentBlock.Plan(goal, steps))
        logBuilder.append("[Plan] 任务规划:\n[Goal] 目标: $goal\n")
        steps.forEachIndexed { i, step -> logBuilder.append("  ${i + 1}. $step\n") }
        logBuilder.append("\n")
        return snapshot()
    }

    fun onGraphLog(content: String): StreamUpdate {
        flushAll()
        val lastBlock = blocks.lastOrNull()
        if (lastBlock is ContentBlock.GraphLog) {
            blocks[blocks.lastIndex] = ContentBlock.GraphLog(lastBlock.content + content)
        } else {
            blocks.add(ContentBlock.GraphLog(content))
        }
        logBuilder.append(content)
        return snapshot()
    }

    fun onSystemLog(content: String): StreamUpdate {
        flushAll()
        val lastBlock = blocks.lastOrNull()
        if (lastBlock is ContentBlock.SystemLog) {
            blocks[blocks.lastIndex] = ContentBlock.SystemLog(lastBlock.content + content)
        } else {
            blocks.add(ContentBlock.SystemLog(content))
        }
        logBuilder.append(content)
        return snapshot()
    }

    fun onError(message: String): StreamUpdate {
        flushAll()
        blocks.add(ContentBlock.Error(message))
        return snapshot()
    }

    fun onSubAgentStart(purpose: String, type: String): StreamUpdate {
        flushAll()
        blocks.add(ContentBlock.SubAgentStart(purpose, type))
        logBuilder.append("\n[SubAgent] $purpose (type=$type)\n")
        return snapshot()
    }

    fun onSubAgentResult(purpose: String, result: String): StreamUpdate {
        flushAll()
        val preview = if (result.length > 500) result.take(500) + "…" else result
        blocks.add(ContentBlock.SubAgentResult(purpose, preview))
        logBuilder.append("[SubAgent Result] $purpose: $preview\n\n")
        return snapshot()
    }

    fun finalize(): StreamUpdate {
        flushAll()
        return StreamUpdate(blocks.toList(), isComplete = true)
    }

    fun currentBlocks(): List<ContentBlock> = buildCurrentBlocks()

    fun getLogContent(): String = logBuilder.toString()

    fun reset() {
        blocks.clear()
        textBuffer.setLength(0)
        thinkingBuffer.setLength(0)
        logBuilder.setLength(0)
        inThinking = false
    }

    fun getBufferedText(): String {
        val sb = StringBuilder()
        if (thinkingBuffer.isNotEmpty()) sb.append(thinkingBuffer)
        if (textBuffer.isNotEmpty()) sb.append(textBuffer)
        return sb.toString()
    }

    private fun flushText() {
        if (textBuffer.isNotEmpty()) {
            val text = textBuffer.toString()
            val lastBlock = blocks.lastOrNull()
            if (lastBlock is ContentBlock.Text) {
                blocks[blocks.lastIndex] = ContentBlock.Text(lastBlock.content + text)
            } else {
                blocks.add(ContentBlock.Text(text))
            }
            logBuilder.append("[Text] ").append(text).append("\n")
            textBuffer.setLength(0)
        }
    }

    private fun flushThinking() {
        if (thinkingBuffer.isNotEmpty()) {
            val lastBlock = blocks.lastOrNull()
            if (lastBlock is ContentBlock.Thinking) {
                blocks[blocks.lastIndex] = ContentBlock.Thinking(lastBlock.content + thinkingBuffer)
            } else {
                blocks.add(ContentBlock.Thinking(thinkingBuffer.toString()))
            }
            thinkingBuffer.setLength(0)
            inThinking = false
        }
    }

    private fun flushAll() {
        flushThinking()
        flushText()
    }

    private fun snapshot(): StreamUpdate = StreamUpdate(buildCurrentBlocks(), isComplete = false)

    private fun buildCurrentBlocks(): List<ContentBlock> {
        val result = blocks.toMutableList()
        if (thinkingBuffer.isNotEmpty()) {
            val last = result.lastOrNull()
            if (last is ContentBlock.Thinking) {
                result[result.lastIndex] = ContentBlock.Thinking(last.content + thinkingBuffer)
            } else {
                result.add(ContentBlock.Thinking(thinkingBuffer.toString()))
            }
        }
        if (textBuffer.isNotEmpty()) {
            val last = result.lastOrNull()
            if (last is ContentBlock.Text) {
                result[result.lastIndex] = ContentBlock.Text(last.content + textBuffer)
            } else {
                result.add(ContentBlock.Text(textBuffer.toString()))
            }
        }
        return result
    }
}

data class StreamUpdate(
    val blocks: List<ContentBlock>,
    val isComplete: Boolean = false,
    /** 流式结束时用于 finalizeStream，仅当 isComplete=true 时有效 */
    val usageLine: String? = null,
    val time: String? = null
)
