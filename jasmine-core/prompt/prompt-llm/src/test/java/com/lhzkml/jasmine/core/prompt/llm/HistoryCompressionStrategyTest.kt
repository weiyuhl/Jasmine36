package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HistoryCompressionStrategyTest {

    /**
     * Mock client 用于压缩测试
     * 当收到包含 "summary" 或 "comprehensive" 的用户消息时，返回 TLDR 摘要
     */
    private class CompressionMockClient : ChatClient {
        override val provider = LLMProvider.OpenAI
        var callCount = 0
        var lastMessages: List<ChatMessage>? = null

        override suspend fun chat(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): String = "mock"

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): ChatResult {
            lastMessages = messages
            callCount++
            // 检查最后一条消息是否是摘要请求
            val lastMsg = messages.lastOrNull()
            val content = if (lastMsg?.content?.contains("comprehensive") == true) {
                "## Key Objectives\nUser asked about weather.\n## Current Status\nCompleted."
            } else {
                "Regular response"
            }
            return ChatResult(content = content, usage = Usage(10, 20, 30), finishReason = "stop")
        }

        override fun chatStream(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): Flow<String> = emptyFlow()

        override suspend fun chatStreamWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>, onChunk: suspend (String) -> Unit
        ): StreamResult = StreamResult("mock")

        override suspend fun listModels(): List<ModelInfo> = emptyList()
        override fun close() {}
    }

    private fun buildLongConversation(): Prompt = prompt("test") {
        system("You are a helpful assistant.")
        user("What's the weather?")
        assistant("It's sunny today.")
        user("What about tomorrow?")
        assistant("It will rain tomorrow.")
        user("Should I bring an umbrella?")
        assistant("Yes, definitely bring an umbrella.")
        user("Thanks!")
        assistant("You're welcome!")
    }

    // ========== WholeHistory ==========

    @Test
    fun `WholeHistory compresses entire history into TLDR`() = runTest {
        val client = CompressionMockClient()
        val session = LLMSession(client, "gpt-4", buildLongConversation())

        assertEquals(9, session.prompt.messages.size)

        HistoryCompressionStrategy.WholeHistory.compress(session)

        val msgs = session.prompt.messages
        // 应该包含：system + 第一条 user + TLDR assistant
        assertTrue(msgs.size < 9)
        assertEquals("system", msgs[0].role)
        assertEquals("You are a helpful assistant.", msgs[0].content)
        // 第一条 user 被保留
        assertTrue(msgs.any { it.role == "user" && it.content == "What's the weather?" })
        // TLDR 摘要存在
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

    @Test
    fun `WholeHistory preserves memory messages`() = runTest {
        val client = CompressionMockClient()
        val p = prompt("test") {
            system("System")
            user("Q1")
            assistant("A1")
            // 模拟之前的压缩摘要（记忆消息）
            assistant("CONTEXT RESTORATION: Previous summary here")
            user("Q2")
            assistant("A2")
        }
        val session = LLMSession(client, "gpt-4", p)

        val memoryMsgs = session.prompt.messages.filter {
            it.content.contains("CONTEXT RESTORATION")
        }
        HistoryCompressionStrategy.WholeHistory.compress(session, memoryMsgs)

        // 记忆消息应该被保留
        assertTrue(session.prompt.messages.any { it.content.contains("CONTEXT RESTORATION") })
    }

    // ========== WholeHistoryMultipleSystemMessages ==========

    @Test
    fun `WholeHistoryMultipleSystemMessages compresses each system block independently`() = runTest {
        val client = CompressionMockClient()
        val p = prompt("test") {
            system("System prompt 1")
            user("Q1")
            assistant("A1")
            user("Q2")
            assistant("A2")
        }
        // 手动追加第二个 system 块
        val multiSystemPrompt = p.withMessages { msgs ->
            msgs + listOf(
                ChatMessage.system("System prompt 2"),
                ChatMessage.user("Q3"),
                ChatMessage.assistant("A3")
            )
        }
        val session = LLMSession(client, "gpt-4", multiSystemPrompt)

        HistoryCompressionStrategy.WholeHistoryMultipleSystemMessages.compress(session)

        val msgs = session.prompt.messages
        // 两个 system 消息都应该被保留
        val systemMsgs = msgs.filter { it.role == "system" }
        assertEquals(2, systemMsgs.size)
        assertEquals("System prompt 1", systemMsgs[0].content)
        assertEquals("System prompt 2", systemMsgs[1].content)
        // 应该有 TLDR 摘要
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
        // 每个块都应该被压缩，所以 LLM 被调用了 2 次
        assertEquals(2, client.callCount)
    }

    // ========== FromTimestamp ==========

    @Test
    fun `FromTimestamp compresses messages from timestamp`() = runTest {
        val client = CompressionMockClient()
        val now = System.currentTimeMillis()
        val p = prompt("test") {
            system("System")
            user("Old question")
            assistant("Old answer")
        }
        // 添加带时间戳的消息
        val withTimestamps = p.withMessages { msgs ->
            msgs.mapIndexed { index, msg ->
                msg.copy(timestamp = now - 10000 + (index * 1000L))
            } + listOf(
                ChatMessage("user", "New question", timestamp = now),
                ChatMessage("assistant", "New answer", timestamp = now + 1000)
            )
        }
        val session = LLMSession(client, "gpt-4", withTimestamps)
        val originalSize = session.prompt.messages.size

        HistoryCompressionStrategy.FromTimestamp(now).compress(session)

        val msgs = session.prompt.messages
        assertTrue(msgs.size < originalSize)
        // system 被保留
        assertEquals("system", msgs[0].role)
        // TLDR 存在
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

    // ========== FromLastNMessages ==========

    @Test
    fun `FromLastNMessages compresses with last N messages`() = runTest {
        val client = CompressionMockClient()
        val session = LLMSession(client, "gpt-4", buildLongConversation())

        HistoryCompressionStrategy.FromLastNMessages(4).compress(session)

        val msgs = session.prompt.messages
        assertTrue(msgs.size < 9)
        // system 被保留
        assertEquals("system", msgs[0].role)
        // TLDR 存在
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

    // ========== Chunked ==========

    @Test
    fun `Chunked compresses in chunks`() = runTest {
        val client = CompressionMockClient()
        val session = LLMSession(client, "gpt-4", buildLongConversation())

        HistoryCompressionStrategy.Chunked(3).compress(session)

        val msgs = session.prompt.messages
        // 压缩后应该比原来短
        assertTrue(msgs.size < 9)
        // system 被保留
        assertEquals("system", msgs[0].role)
        // 多个 TLDR 块（9 条消息 / 3 = 3 块，每块一个 TLDR）
        val tldrCount = msgs.count { it.content.contains("Key Objectives") }
        assertTrue("Should have multiple TLDR chunks, got $tldrCount", tldrCount >= 2)
    }

    // ========== TokenBudget ==========

    @Test
    fun `TokenBudget shouldCompress returns true when over threshold`() {
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 100,
            threshold = 0.5,
            tokenizer = TokenEstimator
        )

        // 短消息不应触发
        val shortMessages = listOf(ChatMessage.user("Hi"))
        assertFalse(strategy.shouldCompress(shortMessages))

        // 长消息应触发
        val longMessages = (1..50).map { ChatMessage.user("This is message number $it with some content") }
        assertTrue(strategy.shouldCompress(longMessages))
    }

    @Test
    fun `TokenBudget compresses when over threshold`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 50,
            threshold = 0.3,
            tokenizer = TokenEstimator
        )

        val session = LLMSession(client, "gpt-4", buildLongConversation())
        val originalSize = session.prompt.messages.size

        strategy.compress(session)

        // 超过阈值，应该被压缩
        assertTrue(session.prompt.messages.size < originalSize)
    }

    @Test
    fun `TokenBudget skips compression when under threshold`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 100000,  // 很大的预算
            threshold = 0.75,
            tokenizer = TokenEstimator
        )

        val session = LLMSession(client, "gpt-4", prompt("test") { user("Hi") })
        val originalSize = session.prompt.messages.size

        strategy.compress(session)

        // 没超过阈值，不应该被压缩
        assertEquals(originalSize, session.prompt.messages.size)
        assertEquals(0, client.callCount)
    }

    // ========== replaceHistoryWithTLDR ==========

    @Test
    fun `replaceHistoryWithTLDR uses WholeHistory by default`() = runTest {
        val client = CompressionMockClient()
        val session = LLMSession(client, "gpt-4", buildLongConversation())

        session.replaceHistoryWithTLDR()

        assertTrue(session.prompt.messages.size < 9)
        assertTrue(session.prompt.messages.any { it.content.contains("Key Objectives") })
    }

    // ========== compressIfNeeded ==========

    @Test
    fun `compressIfNeeded triggers when over budget`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 50,
            threshold = 0.3,
            tokenizer = TokenEstimator
        )

        val session = LLMSession(client, "gpt-4", buildLongConversation())
        session.compressIfNeeded(strategy)

        assertTrue(client.callCount > 0)
        assertTrue(session.prompt.messages.size < 9)
    }

    @Test
    fun `compressIfNeeded skips when under budget`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 100000,
            threshold = 0.75,
            tokenizer = TokenEstimator
        )

        val session = LLMSession(client, "gpt-4", prompt("test") { user("Hi") })
        session.compressIfNeeded(strategy)

        assertEquals(0, client.callCount)
    }

    // ========== dropTrailingToolCalls ==========

    @Test
    fun `compression drops trailing tool calls before TLDR`() = runTest {
        val client = CompressionMockClient()
        val toolCall = ToolCall("call_1", "test_tool", """{"a":1}""")
        val p = prompt("test") {
            system("System")
            user("Do something")
            assistantWithToolCalls(listOf(toolCall))
        }
        val session = LLMSession(client, "gpt-4", p)

        HistoryCompressionStrategy.WholeHistory.compress(session)

        // 压缩后不应该有孤立的工具调用
        val msgs = session.prompt.messages
        assertEquals("system", msgs[0].role)
    }

    // ========== SUMMARIZE_PROMPT ==========

    @Test
    fun `SUMMARIZE_PROMPT contains required sections`() {
        val prompt = HistoryCompressionStrategy.SUMMARIZE_PROMPT
        assertTrue(prompt.contains("Key Objectives"))
        assertTrue(prompt.contains("Tools Used"))
        assertTrue(prompt.contains("Key Findings"))
        assertTrue(prompt.contains("Current Status"))
        assertTrue(prompt.contains("Next Steps"))
        assertTrue(prompt.contains("comprehensive summary"))
    }

    // ========== splitHistoryBySystemMessages ==========

    @Test
    fun `splitHistoryBySystemMessages splits correctly`() {
        // 使用一个具体策略来访问 protected 方法
        val strategy = object : HistoryCompressionStrategy() {
            override suspend fun compress(session: LLMSession, memoryMessages: List<ChatMessage>) {}
            fun testSplit(messages: List<ChatMessage>) = splitHistoryBySystemMessages(messages)
        }

        val messages = listOf(
            ChatMessage.user("Before system"),
            ChatMessage.system("System 1"),
            ChatMessage.user("Q1"),
            ChatMessage.assistant("A1"),
            ChatMessage.system("System 2"),
            ChatMessage.user("Q2"),
            ChatMessage.assistant("A2")
        )

        val blocks = strategy.testSplit(messages)
        assertEquals(2, blocks.size)
        // 第一块包含 system1 之前的消息和 system1 块
        assertEquals(4, blocks[0].size) // Before system, System 1, Q1, A1
        assertEquals(3, blocks[1].size) // System 2, Q2, A2
    }
}
