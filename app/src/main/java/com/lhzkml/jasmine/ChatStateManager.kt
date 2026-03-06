package com.lhzkml.jasmine

import android.os.Handler
import android.os.Looper

/**
 * Centralized chat state manager.
 * Operates on a MutableList<ChatItem> (backed by SnapshotStateList in Compose).
 * All public methods must be called on the Main thread.
 */
class ChatStateManager(
    private val items: MutableList<ChatItem>,
    private val onScrollNeeded: () -> Unit
) {
    companion object {
        private const val DEBOUNCE_MS = 50L
    }

    private var streamProcessor: StreamProcessor? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStreamRender: Runnable? = null

    fun addUserMessage(content: String, time: String) {
        items.add(ChatItem.UserMessage(content, time))
        onScrollNeeded()
    }

    fun startStreaming() {
        streamProcessor = StreamProcessor()
        items.add(ChatItem.TypingIndicator)
        onScrollNeeded()
    }

    fun handleChunk(chunk: String) {
        val proc = streamProcessor ?: return
        val update = proc.onChunk(chunk)
        scheduleStreamUpdate(update)
    }

    fun handleThinking(text: String) {
        val proc = streamProcessor ?: return
        val update = proc.onThinking(text)
        scheduleStreamUpdate(update)
    }

    fun handleToolCall(toolName: String, arguments: String) {
        val proc = streamProcessor ?: return
        val update = proc.onToolCallStart(toolName, arguments)
        scheduleStreamUpdate(update)
    }

    fun handleToolResult(toolName: String, result: String) {
        val proc = streamProcessor ?: return
        val update = proc.onToolCallResult(toolName, result)
        scheduleStreamUpdate(update)
    }

    fun handlePlan(goal: String, steps: List<String>) {
        val proc = streamProcessor ?: return
        val update = proc.onPlan(goal, steps)
        scheduleStreamUpdate(update)
    }

    fun handleGraphLog(content: String) {
        val proc = streamProcessor ?: return
        val update = proc.onGraphLog(content)
        scheduleStreamUpdate(update)
    }

    fun handleSystemLog(content: String) {
        val proc = streamProcessor
        if (proc != null) {
            val update = proc.onSystemLog(content)
            scheduleStreamUpdate(update)
        } else {
            addHistoryLogBlocks(listOf(ContentBlock.SystemLog(content)))
            onScrollNeeded()
        }
    }

    fun handleSubAgentStart(purpose: String, type: String) {
        val proc = streamProcessor ?: return
        val update = proc.onSubAgentStart(purpose, type)
        scheduleStreamUpdate(update)
    }

    fun handleSubAgentResult(purpose: String, result: String) {
        val proc = streamProcessor ?: return
        val update = proc.onSubAgentResult(purpose, result)
        scheduleStreamUpdate(update)
    }

    fun handleError(message: String) {
        val proc = streamProcessor
        if (proc != null) {
            val update = proc.onError(message)
            scheduleStreamUpdate(update)
        } else {
            addHistoryLogBlocks(listOf(ContentBlock.Error(message)))
            onScrollNeeded()
        }
    }

    fun finalizeStream(usageLine: String, time: String) {
        val proc = streamProcessor ?: return
        proc.finalize()
        flushPendingRender()
        removeTypingIndicator()
        val idx = findStreamingAiIndex()
        if (idx != null) {
            val item = items[idx] as ChatItem.AiMessage
            items[idx] = item.copy(isStreaming = false, usageLine = usageLine, time = time)
        }
        streamProcessor = null
        onScrollNeeded()
    }

    fun cancelStream() {
        flushPendingRender()
        removeTypingIndicator()
        val idx = findStreamingAiIndex()
        if (idx != null) {
            val item = items[idx] as ChatItem.AiMessage
            items[idx] = item.copy(isStreaming = false)
        }
        streamProcessor = null
    }

    /**
     * 获取当前流式回复中已生成的文本内容（用于停止时保存部分回复）。
     * 仅提取 Text 和 Thinking 块的内容。
     */
    fun getPartialContent(): String {
        val proc = streamProcessor ?: return ""
        val blocks = proc.currentBlocks()
        return blocks.mapNotNull { block ->
            when (block) {
                is ContentBlock.Text -> block.content
                is ContentBlock.Thinking -> block.content
                else -> null
            }
        }.joinToString("\n").trim()
    }

    fun getLogContent(): String = streamProcessor?.getLogContent() ?: ""

    fun getBufferedText(): String = streamProcessor?.getBufferedText() ?: ""

    fun addHistoryAiMessage(blocks: List<ContentBlock>, usageLine: String, time: String) {
        items.add(ChatItem.AiMessage(blocks = blocks, usageLine = usageLine, time = time))
    }

    fun addHistoryLogBlocks(blocks: List<ContentBlock>) {
        items.add(ChatItem.AiMessage(blocks = blocks))
    }

    fun clearAll() {
        flushPendingRender()
        streamProcessor = null
        items.clear()
    }

    private fun scheduleStreamUpdate(update: StreamUpdate) {
        pendingStreamRender?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingStreamRender = null
            applyStreamUpdate(update)
        }
        pendingStreamRender = runnable
        handler.postDelayed(runnable, DEBOUNCE_MS)
    }

    private fun applyStreamUpdate(update: StreamUpdate) {
        removeTypingIndicator()
        val idx = findStreamingAiIndex()
        if (idx == null) {
            items.add(ChatItem.AiMessage(blocks = update.blocks, isStreaming = true))
        } else {
            val item = items[idx] as ChatItem.AiMessage
            items[idx] = item.copy(blocks = update.blocks)
        }
        onScrollNeeded()
    }

    private fun findStreamingAiIndex(): Int? {
        for (i in items.lastIndex downTo 0) {
            val item = items[i]
            if (item is ChatItem.AiMessage && item.isStreaming) return i
        }
        return null
    }

    private fun removeTypingIndicator() {
        val idx = items.indexOfFirst { it is ChatItem.TypingIndicator }
        if (idx >= 0) items.removeAt(idx)
    }

    private fun flushPendingRender() {
        pendingStreamRender?.let {
            handler.removeCallbacks(it)
            it.run()
            pendingStreamRender = null
        }
    }
}
