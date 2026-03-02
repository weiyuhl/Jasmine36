package com.lhzkml.jasmine

/**
 * Centralized chat state manager, inspired by Claude App's e2 (ChatViewModel).
 * Coordinates StreamProcessor, ChatAdapter, and scroll behavior.
 * All public methods must be called on the Main thread.
 */
class ChatStateManager(
    private val adapter: ChatAdapter,
    private val onScrollNeeded: () -> Unit
) {
    private var streamProcessor: StreamProcessor? = null

    fun addUserMessage(content: String, time: String) {
        adapter.addItem(ChatItem.UserMessage(content, time))
        onScrollNeeded()
    }

    fun startStreaming() {
        streamProcessor = StreamProcessor()
        adapter.addTypingIndicator()
        onScrollNeeded()
    }

    fun handleChunk(chunk: String) {
        val proc = streamProcessor ?: return
        val update = proc.onChunk(chunk)
        updateStreamingAi(update)
    }

    fun handleThinking(text: String) {
        val proc = streamProcessor ?: return
        val update = proc.onThinking(text)
        updateStreamingAi(update)
    }

    fun handleToolCall(toolName: String, arguments: String) {
        val proc = streamProcessor ?: return
        val update = proc.onToolCallStart(toolName, arguments)
        updateStreamingAi(update)
    }

    fun handleToolResult(toolName: String, result: String) {
        val proc = streamProcessor ?: return
        val update = proc.onToolCallResult(toolName, result)
        updateStreamingAi(update)
    }

    fun handlePlan(goal: String, steps: List<String>) {
        val proc = streamProcessor ?: return
        val update = proc.onPlan(goal, steps)
        updateStreamingAi(update)
    }

    fun handleGraphLog(content: String) {
        val proc = streamProcessor ?: return
        val update = proc.onGraphLog(content)
        updateStreamingAi(update)
    }

    fun handleSystemLog(content: String) {
        val proc = streamProcessor
        if (proc != null) {
            val update = proc.onSystemLog(content)
            updateStreamingAi(update)
        } else {
            addHistoryLogBlocks(listOf(ContentBlock.SystemLog(content)))
            onScrollNeeded()
        }
    }

    fun handleError(message: String) {
        val proc = streamProcessor
        if (proc != null) {
            val update = proc.onError(message)
            updateStreamingAi(update)
        } else {
            addHistoryLogBlocks(listOf(ContentBlock.Error(message)))
            onScrollNeeded()
        }
    }

    fun finalizeStream(usageLine: String, time: String) {
        val proc = streamProcessor ?: return
        proc.finalize()
        adapter.finalizeStreamingAi(usageLine, time)
        streamProcessor = null
        onScrollNeeded()
    }

    fun cancelStream() {
        adapter.removeTypingIndicator()
        adapter.finalizeStreamingAi("", "")
        streamProcessor = null
    }

    fun getLogContent(): String = streamProcessor?.getLogContent() ?: ""

    fun addHistoryAiMessage(blocks: List<ContentBlock>, usageLine: String, time: String) {
        adapter.addItem(ChatItem.AiMessage(
            blocks = blocks,
            usageLine = usageLine,
            time = time
        ))
    }

    fun addHistoryLogBlocks(blocks: List<ContentBlock>) {
        adapter.addItem(ChatItem.AiMessage(blocks = blocks))
    }

    fun clearAll() {
        streamProcessor = null
        adapter.clearAll()
    }

    private fun updateStreamingAi(update: StreamUpdate) {
        adapter.updateStreamingAi(update.blocks)
        onScrollNeeded()
    }
}
