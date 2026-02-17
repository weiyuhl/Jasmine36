package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.prompt

/**
 * 压缩过程事件监听器
 * 用于在 UI 层实时显示压缩的详细过程
 */
interface CompressionEventListener {
    /** 压缩开始，显示策略信息 */
    suspend fun onCompressionStart(strategyName: String, originalMessageCount: Int) {}
    /** LLM 正在生成摘要（流式输出） */
    suspend fun onSummaryChunk(chunk: String) {}
    /** 单个块压缩完成 */
    suspend fun onBlockCompressed(blockIndex: Int, totalBlocks: Int) {}
    /** 压缩完成 */
    suspend fun onCompressionDone(compressedMessageCount: Int) {}
}

/**
 * 历史压缩策略抽象基类
 * 完整移植 koog 的 HistoryCompressionStrategy，定义不同的上下文压缩方式。
 *
 * 核心流程：
 * 1. 把当前对话历史发给 LLM，让它生成 TLDR 摘要
 * 2. 用 [system + 第一条 user + memory + TLDR] 替换原始历史
 * 3. 不同策略决定"哪些消息参与摘要生成"
 *
 * 可用策略：
 * - [WholeHistory] — 整个历史生成一个 TLDR
 * - [WholeHistoryMultipleSystemMessages] — 按 system 消息分块，每块独立 TLDR
 * - [FromLastNMessages] — 只保留最后 N 条消息生成 TLDR
 * - [FromTimestamp] — 从指定时间戳开始的消息生成 TLDR
 * - [Chunked] — 按固定大小分块，每块独立生成 TLDR
 * - [TokenBudget] — 基于 token 预算自动触发压缩
 */
abstract class HistoryCompressionStrategy {

    /**
     * 执行压缩
     * @param session 当前 LLM 会话
     * @param memoryMessages 需要保留的记忆消息（如之前的摘要）
     * @param listener 压缩过程事件监听器（可选）
     */
    abstract suspend fun compress(
        session: LLMSession,
        memoryMessages: List<ChatMessage> = emptyList(),
        listener: CompressionEventListener? = null
    )

    // ========== TLDR 摘要生成 ==========

    /**
     * 让 LLM 对当前 prompt 生成 TLDR 摘要
     * 流程：去掉末尾工具调用 → 追加"请总结"的用户消息 → 流式请求 LLM（不带工具）→ 返回摘要
     */
    protected suspend fun compressPromptIntoTLDR(
        session: LLMSession,
        listener: CompressionEventListener? = null
    ): List<ChatMessage> {
        // 去掉末尾未完成的工具调用
        session.dropTrailingToolCalls()

        // 追加摘要请求
        session.appendPrompt {
            user(SUMMARIZE_PROMPT)
        }

        // 流式请求 LLM 生成摘要（不带工具，避免 LLM 调用工具而非生成摘要）
        if (listener != null) {
            val result = session.requestLLMStreamWithoutTools(
                onChunk = { chunk -> listener.onSummaryChunk(chunk) }
            )
            return listOf(ChatMessage.assistant(result.content))
        } else {
            val result = session.requestLLMWithoutTools()
            return listOf(ChatMessage.assistant(result.content))
        }
    }

    // ========== 消息重组 ==========

    /**
     * 重组消息历史：保留 system + 第一条 user + memory + TLDR
     * 参考 koog 的 composeMessageHistory
     *
     * 带时间戳的消息会按时间戳排序（system + first user + memory 部分）。
     */
    protected fun composeMessageHistory(
        originalMessages: List<ChatMessage>,
        tldrMessages: List<ChatMessage>,
        memoryMessages: List<ChatMessage>
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // 保留所有 system 消息
        messages.addAll(originalMessages.filter { it.role == "system" })

        // 保留第一条 user 消息
        originalMessages.firstOrNull { it.role == "user" }?.let { messages.add(it) }

        // 添加记忆消息
        messages.addAll(memoryMessages)

        // 按时间戳排序（如果有时间戳的话）
        messages.sortWith(compareBy { it.timestamp ?: 0L })

        // 添加 TLDR 摘要
        messages.addAll(tldrMessages)

        // 保留末尾的工具调用（如果有未完成的）
        val trailingToolCalls = originalMessages.takeLastWhile {
            it.role == "assistant" && it.toolCalls != null
        }
        messages.addAll(trailingToolCalls)

        return messages
    }

    // ========== 辅助方法 ==========

    /**
     * 按 system 消息边界拆分历史
     * 参考 koog 的 splitHistoryBySystemMessages
     *
     * [User, System1, User, Assistant, ToolCall, ToolResult, System2, User, Assistant]
     * → [[User, System1, User, Assistant, ToolCall, ToolResult], [System2, User, Assistant]]
     */
    protected fun splitHistoryBySystemMessages(messages: List<ChatMessage>): List<List<ChatMessage>> {
        val result = mutableListOf<MutableList<ChatMessage>>()
        var currentBlock = mutableListOf<ChatMessage>()
        var beforeSystemMessage = true

        for (message in messages) {
            if (message.role == "system") {
                if (beforeSystemMessage) {
                    beforeSystemMessage = false
                } else {
                    result.add(currentBlock)
                    currentBlock = mutableListOf()
                }
            }
            currentBlock.add(message)
        }

        if (currentBlock.isNotEmpty()) {
            result.add(currentBlock)
        }

        return result
    }

    // ========== 具体策略 ==========

    /**
     * 整个历史生成一个 TLDR
     *
     * [System, User1, Assistant, ToolCall, ToolResult, User2, Assistant]
     * → [System, User1, Memory, TLDR(全部历史)]
     */
    object WholeHistory : HistoryCompressionStrategy() {
        override suspend fun compress(
            session: LLMSession,
            memoryMessages: List<ChatMessage>,
            listener: CompressionEventListener?
        ) {
            val originalMessages = session.prompt.messages
            listener?.onCompressionStart("WholeHistory", originalMessages.size)
            val tldrMessages = compressPromptIntoTLDR(session, listener)
            val compressed = composeMessageHistory(originalMessages, tldrMessages, memoryMessages)
            session.rewritePrompt { it.withMessages { compressed } }
            listener?.onCompressionDone(compressed.size)
        }
    }

    /**
     * 多 System 消息场景的压缩策略
     * 参考 koog 的 WholeHistoryMultipleSystemMessages
     *
     * 按 system 消息边界拆分历史，每块独立生成 TLDR，记忆消息只加到第一块。
     *
     * [System1, User1, Assistant, System2, User2, Assistant]
     * → [System1, User1, Memory, TLDR(block1), System2, User2, TLDR(block2)]
     */
    object WholeHistoryMultipleSystemMessages : HistoryCompressionStrategy() {
        override suspend fun compress(
            session: LLMSession,
            memoryMessages: List<ChatMessage>,
            listener: CompressionEventListener?
        ) {
            val compressedMessages = mutableListOf<ChatMessage>()

            val messageBlocks = splitHistoryBySystemMessages(session.prompt.messages)
            listener?.onCompressionStart("WholeHistoryMultipleSystemMessages", session.prompt.messages.size)

            messageBlocks.forEachIndexed { index, messageBlock ->
                session.rewritePrompt { it.withMessages { messageBlock } }

                val tldrMessageBlock = compressPromptIntoTLDR(session, listener)
                listener?.onBlockCompressed(index + 1, messageBlocks.size)

                val compressedMessageBlock = composeMessageHistory(
                    originalMessages = messageBlock,
                    tldrMessages = tldrMessageBlock,
                    memoryMessages = if (index == 0) memoryMessages else emptyList()
                )
                compressedMessages.addAll(compressedMessageBlock)
            }
            session.rewritePrompt { it.withMessages { compressedMessages } }
            listener?.onCompressionDone(compressedMessages.size)
        }
    }

    /**
     * 只保留最后 N 条消息生成 TLDR
     *
     * @param n 保留的最近消息数
     */
    data class FromLastNMessages(val n: Int) : HistoryCompressionStrategy() {
        override suspend fun compress(
            session: LLMSession,
            memoryMessages: List<ChatMessage>,
            listener: CompressionEventListener?
        ) {
            val originalMessages = session.prompt.messages
            listener?.onCompressionStart("FromLastNMessages(n=$n)", originalMessages.size)
            session.leaveLastNMessages(n)
            val tldrMessages = compressPromptIntoTLDR(session, listener)
            val compressed = composeMessageHistory(originalMessages, tldrMessages, memoryMessages)
            session.rewritePrompt { it.withMessages { compressed } }
            listener?.onCompressionDone(compressed.size)
        }
    }

    /**
     * 从指定时间戳开始的消息生成 TLDR
     * 参考 koog 的 FromTimestamp
     *
     * 保留 system 消息和第一条 user 消息，只对指定时间戳之后的消息生成 TLDR。
     *
     * @param timestamp 起始时间戳（毫秒），只保留此时间之后的消息
     */
    data class FromTimestamp(val timestamp: Long) : HistoryCompressionStrategy() {
        override suspend fun compress(
            session: LLMSession,
            memoryMessages: List<ChatMessage>,
            listener: CompressionEventListener?
        ) {
            val originalMessages = session.prompt.messages
            listener?.onCompressionStart("FromTimestamp", originalMessages.size)
            session.leaveMessagesFromTimestamp(timestamp)
            val tldrMessages = compressPromptIntoTLDR(session, listener)
            val compressed = composeMessageHistory(originalMessages, tldrMessages, memoryMessages)
            session.rewritePrompt { it.withMessages { compressed } }
            listener?.onCompressionDone(compressed.size)
        }
    }

    /**
     * 按固定大小分块，每块独立生成 TLDR
     *
     * @param chunkSize 每块的消息数
     */
    data class Chunked(val chunkSize: Int) : HistoryCompressionStrategy() {
        override suspend fun compress(
            session: LLMSession,
            memoryMessages: List<ChatMessage>,
            listener: CompressionEventListener?
        ) {
            val originalMessages = session.prompt.messages
            listener?.onCompressionStart("Chunked(chunkSize=$chunkSize)", originalMessages.size)
            val chunks = originalMessages.chunked(chunkSize)
            val tldrChunks = mutableListOf<ChatMessage>()
            chunks.forEachIndexed { index, chunk ->
                session.rewritePrompt { it.withMessages { chunk } }
                tldrChunks.addAll(compressPromptIntoTLDR(session, listener))
                listener?.onBlockCompressed(index + 1, chunks.size)
            }
            val compressed = composeMessageHistory(originalMessages, tldrChunks, memoryMessages)
            session.rewritePrompt { it.withMessages { compressed } }
            listener?.onCompressionDone(compressed.size)
        }
    }

    /**
     * 基于 token 预算的自动压缩策略
     * 当消息总 token 数超过阈值时自动触发 WholeHistory 压缩
     *
     * @param maxTokens 最大 token 预算
     * @param threshold 触发压缩的阈值比例（0.0~1.0），默认 0.75
     * @param tokenizer Token 计数器
     */
    data class TokenBudget(
        val maxTokens: Int,
        val threshold: Double = 0.75,
        val tokenizer: Tokenizer = TokenEstimator
    ) : HistoryCompressionStrategy() {

        /** 是否需要压缩 */
        fun shouldCompress(messages: List<ChatMessage>): Boolean {
            val totalTokens = messages.sumOf { tokenizer.countMessageTokens(it.role, it.content) }
            return totalTokens > (maxTokens * threshold).toInt()
        }

        override suspend fun compress(
            session: LLMSession,
            memoryMessages: List<ChatMessage>,
            listener: CompressionEventListener?
        ) {
            if (shouldCompress(session.prompt.messages)) {
                WholeHistory.compress(session, memoryMessages, listener)
            }
        }
    }

    companion object {
        /**
         * TLDR 摘要请求提示词
         * 参考 koog 的 Prompts.summarizeInTLDR
         */
        val SUMMARIZE_PROMPT = buildString {
            appendLine("Create a comprehensive summary of this conversation.")
            appendLine()
            appendLine("Include the following in your summary:")
            appendLine("1. Key objectives and problems being addressed")
            appendLine("2. All tools used along with their purpose and outcomes")
            appendLine("3. Critical information discovered or generated")
            appendLine("4. Current progress status and conclusions reached")
            appendLine("5. Any pending questions or unresolved issues")
            appendLine()
            appendLine("FORMAT YOUR SUMMARY WITH CLEAR SECTIONS for easy reference, including:")
            appendLine("- Key Objectives")
            appendLine("- Tools Used & Results")
            appendLine("- Key Findings")
            appendLine("- Current Status")
            appendLine("- Next Steps")
            appendLine()
            appendLine("This summary will be the ONLY context available for continuing this conversation, along with the system message.")
            append("Ensure it contains ALL essential information needed to proceed effectively.")
        }
    }
}
