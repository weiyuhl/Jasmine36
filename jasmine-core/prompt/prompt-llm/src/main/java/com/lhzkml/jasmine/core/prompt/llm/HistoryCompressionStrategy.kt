package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.Tokenizer
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
     * @param session 当前 LLM 可写会话
     * @param listener 压缩过程事件监听器（可选）
     * @param memoryMessages 需要保留的记忆消息列表（可选）
     */
    abstract suspend fun compress(
        session: LLMWriteSession,
        listener: CompressionEventListener? = null,
        memoryMessages: List<ChatMessage> = emptyList()
    )

    // ========== TLDR 摘要生成 ==========

    /**
     * 让 LLM 对当前 prompt 生成 TLDR 摘要
     * 流程：去掉末尾工具调用 → 追加"请总结"的用户消息 → 流式请求 LLM（不带工具）→ 返回摘要
     */
    protected suspend fun compressPromptIntoTLDR(
        session: LLMWriteSession,
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
     * 带时间戳的消息会按时间戳排序（system + first user 部分）。
     *
     * @param originalMessages 原始消息列表
     * @param tldrMessages TLDR 摘要消息列表
     * @param memoryMessages 需要保留的记忆消息列表
     */
    protected fun composeMessageHistory(
        originalMessages: List<ChatMessage>,
        tldrMessages: List<ChatMessage>,
        memoryMessages: List<ChatMessage> = emptyList()
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
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
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
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
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
                    // 只在第一个 block 中添加记忆消息
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
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
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
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
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
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
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
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
        ) {
            if (shouldCompress(session.prompt.messages)) {
                WholeHistory.compress(session, listener, memoryMessages)
            }
        }
    }

    /**
     * 渐进式压缩策略（推荐）
     *
     * 核心思路：
     * 1. 保留最近 N 轮对话原文不动（保持近期上下文完整）
     * 2. 只对更早的历史做 TLDR 摘要
     * 3. 已有的摘要会被复用，不重复压缩（增量式）
     * 4. 工具调用和结果中的关键信息被提取保留
     *
     * 压缩后结构：[system + 旧摘要(如有) + 新增摘要 + 最近 N 轮原文]
     *
     * @param keepRecentRounds 保留最近多少轮对话（1轮 = 1 user + 1 assistant）
     * @param maxTokens token 预算上限，超过时触发压缩
     * @param threshold 触发阈值（0.0~1.0）
     * @param tokenizer Token 计数器
     */
    data class Progressive(
        val keepRecentRounds: Int = 4,
        val maxTokens: Int = 128000,
        val threshold: Double = 0.75,
        val tokenizer: Tokenizer = TokenEstimator
    ) : HistoryCompressionStrategy() {

        fun shouldCompress(messages: List<ChatMessage>): Boolean {
            val totalTokens = messages.sumOf { tokenizer.countMessageTokens(it.role, it.content) }
            return totalTokens > (maxTokens * threshold).toInt()
        }

        override suspend fun compress(
            session: LLMWriteSession,
            listener: CompressionEventListener?,
            memoryMessages: List<ChatMessage>
        ) {
            val messages = session.prompt.messages
            if (!shouldCompress(messages)) return

            listener?.onCompressionStart("Progressive(keep=$keepRecentRounds)", messages.size)

            val systemMessages = messages.filter { it.role == "system" }
            val nonSystemMessages = messages.filter { it.role != "system" }

            // 按"轮"分割：一轮 = user 开头到下一个 user 之前
            val rounds = splitIntoRounds(nonSystemMessages)

            // 检查是否已经有旧摘要（CONTEXT_RESTORATION 标记）
            val existingSummaryIndex = nonSystemMessages.indexOfFirst {
                it.role == "assistant" && it.content.startsWith(CONTEXT_RESTORATION_PREFIX)
            }

            val keepCount = keepRecentRounds.coerceAtMost(rounds.size)
            if (keepCount >= rounds.size) {
                listener?.onCompressionDone(messages.size)
                return
            }

            val roundsToCompress = rounds.subList(0, rounds.size - keepCount)
            val roundsToKeep = rounds.subList(rounds.size - keepCount, rounds.size)

            // 构建需要摘要的消息（排除已有摘要标记本身，但包含旧摘要内容作为上下文）
            val messagesToSummarize = mutableListOf<ChatMessage>()
            for (round in roundsToCompress) {
                for (msg in round) {
                    if (msg.role == "assistant" && msg.content.startsWith(CONTEXT_RESTORATION_PREFIX)) {
                        continue
                    }
                    messagesToSummarize.add(msg)
                }
            }

            if (messagesToSummarize.isEmpty()) {
                listener?.onCompressionDone(messages.size)
                return
            }

            // 提取旧摘要内容以实现增量压缩
            val oldSummary = if (existingSummaryIndex >= 0) {
                nonSystemMessages[existingSummaryIndex].content
                    .removePrefix(CONTEXT_RESTORATION_PREFIX).trim()
            } else null

            // 构建摘要用的临时 prompt
            val summaryPrompt = buildSummaryPrompt(systemMessages, messagesToSummarize, oldSummary)
            session.rewritePrompt { summaryPrompt }

            val tldrMessages = compressPromptIntoTLDR(session, listener)
            val summaryContent = tldrMessages.firstOrNull()?.content ?: ""

            // 重组消息：system + 摘要标记 + memory + 保留的近期轮次
            val result = mutableListOf<ChatMessage>()
            result.addAll(systemMessages)
            if (summaryContent.isNotBlank()) {
                result.add(ChatMessage.assistant("$CONTEXT_RESTORATION_PREFIX\n$summaryContent"))
            }
            result.addAll(memoryMessages)
            for (round in roundsToKeep) {
                result.addAll(round)
            }

            session.rewritePrompt { it.withMessages { result } }
            listener?.onCompressionDone(result.size)
        }

        private fun buildSummaryPrompt(
            systemMessages: List<ChatMessage>,
            messagesToSummarize: List<ChatMessage>,
            oldSummary: String?
        ): Prompt {
            val messages = mutableListOf<ChatMessage>()
            messages.addAll(systemMessages)

            if (oldSummary != null) {
                messages.add(ChatMessage.assistant("Previous context summary:\n$oldSummary"))
            }

            messages.addAll(messagesToSummarize)
            messages.add(ChatMessage.user(PROGRESSIVE_SUMMARIZE_PROMPT))

            return Prompt(messages = messages, id = "compression")
        }

        /**
         * 将非 system 消息按 user 消息边界分割成"轮"
         * 每轮以 user 消息开头，包含后续的 assistant/tool 消息
         */
        private fun splitIntoRounds(messages: List<ChatMessage>): List<List<ChatMessage>> {
            val rounds = mutableListOf<MutableList<ChatMessage>>()
            for (msg in messages) {
                if (msg.role == "user" || rounds.isEmpty()) {
                    rounds.add(mutableListOf(msg))
                } else {
                    rounds.last().add(msg)
                }
            }
            return rounds
        }
    }

    companion object {
        /** 上下文恢复标记前缀，用于识别已压缩的摘要消息 */
        const val CONTEXT_RESTORATION_PREFIX = "CONTEXT RESTORATION:"

        /**
         * 全量 TLDR 摘要请求提示词（用于 WholeHistory 等传统策略）
         */
        val SUMMARIZE_PROMPT = buildString {
            appendLine("Summarize this conversation concisely. Preserve:")
            appendLine("- User's core intent and requirements")
            appendLine("- Key decisions made and their rationale")
            appendLine("- Important data: file paths, code snippets, config values, names")
            appendLine("- Tool execution results that affect next steps")
            appendLine("- Current progress and unresolved issues")
            appendLine()
            appendLine("Write a dense, factual summary. No headers or formatting. No filler.")
            append("This summary replaces the conversation history — include ALL essential context to continue effectively.")
        }

        /**
         * 渐进式压缩摘要提示词（用于 Progressive 策略）
         * 只需要总结被压缩的旧历史部分，近期对话仍完整保留
         */
        val PROGRESSIVE_SUMMARIZE_PROMPT = buildString {
            appendLine("Summarize the conversation above into a brief context note. Focus on:")
            appendLine("- What the user wanted and what was accomplished")
            appendLine("- Key facts: file paths, variable names, config values, decisions")
            appendLine("- Tool results that produced important data")
            appendLine("- Any unresolved issues or pending items")
            appendLine()
            appendLine("Be concise and factual. No headers, no bullet formatting, just dense prose.")
            append("The recent conversation is still available — only summarize the older context shown above.")
        }
    }
}
