package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HistoryCompressionStrategyTest {

    private class CompressionMockClient : ChatClient {
        override val provider = LLMProvider.OpenAI
        var callCount = 0
        var lastMessages: List<ChatMessage>? = null

        override suspend fun chat(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>, toolChoice: ToolChoice?
        ): String = "mock"

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>, toolChoice: ToolChoice?
        ): ChatResult {
            lastMessages = messages
            callCount++
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
            tools: List<ToolDescriptor>, toolChoice: ToolChoice?
        ): Flow<String> = emptyFlow()

        override suspend fun chatStreamWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>, toolChoice: ToolChoice?,
            onChunk: suspend (String) -> Unit
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

    @Test
    fun `WholeHistory compresses entire history into TLDR`() = runTest {
        val client = CompressionMockClient()
        val session = LLMWriteSession(client, "gpt-4", buildLongConversation())

        assertEquals(9, session.prompt.messages.size)

        HistoryCompressionStrategy.WholeHistory.compress(session)

        val msgs = session.prompt.messages
        assertTrue(msgs.size < 9)
        assertEquals("system", msgs[0].role)
        assertEquals("You are a helpful assistant.", msgs[0].content)
        assertTrue(msgs.any { it.role == "user" && it.content == "What's the weather?" })
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

    @Test
    fun `WholeHistory preserves memory messages`() = runTest {
        val client = CompressionMockClient()
        val p = prompt("test") {
            system("System")
            user("Q1")
            assistant("A1")
            assistant("CONTEXT RESTORATION: Previous summary here")
            user("Q2")
            assistant("A2")
        }
        val session = LLMWriteSession(client, "gpt-4", p)

        HistoryCompressionStrategy.WholeHistory.compress(session)

        val msgs = session.prompt.messages
        assertEquals("system", msgs[0].role)
        assertTrue(msgs.any { it.role == "user" && it.content == "Q1" })
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

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
        val multiSystemPrompt = p.withMessages { msgs ->
            msgs + listOf(
                ChatMessage.system("System prompt 2"),
                ChatMessage.user("Q3"),
                ChatMessage.assistant("A3")
            )
        }
        val session = LLMWriteSession(client, "gpt-4", multiSystemPrompt)

        HistoryCompressionStrategy.WholeHistoryMultipleSystemMessages.compress(session)

        val msgs = session.prompt.messages
        val systemMsgs = msgs.filter { it.role == "system" }
        assertEquals(2, systemMsgs.size)
        assertEquals("System prompt 1", systemMsgs[0].content)
        assertEquals("System prompt 2", systemMsgs[1].content)
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
        assertEquals(2, client.callCount)
    }

    @Test
    fun `FromTimestamp compresses messages from timestamp`() = runTest {
        val client = CompressionMockClient()
        val now = System.currentTimeMillis()
        val p = prompt("test") {
            system("System")
            user("Old question")
            assistant("Old answer")
        }
        val withTimestamps = p.withMessages { msgs ->
            msgs.mapIndexed { index, msg ->
                msg.copy(timestamp = now - 10000 + (index * 1000L))
            } + listOf(
                ChatMessage("user", "New question", timestamp = now),
                ChatMessage("assistant", "New answer", timestamp = now + 1000)
            )
        }
        val session = LLMWriteSession(client, "gpt-4", withTimestamps)
        val originalSize = session.prompt.messages.size

        HistoryCompressionStrategy.FromTimestamp(now).compress(session)

        val msgs = session.prompt.messages
        assertTrue(msgs.size < originalSize)
        assertEquals("system", msgs[0].role)
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

    @Test
    fun `FromLastNMessages compresses with last N messages`() = runTest {
        val client = CompressionMockClient()
        val session = LLMWriteSession(client, "gpt-4", buildLongConversation())

        HistoryCompressionStrategy.FromLastNMessages(4).compress(session)

        val msgs = session.prompt.messages
        assertTrue(msgs.size < 9)
        assertEquals("system", msgs[0].role)
        assertTrue(msgs.any { it.content.contains("Key Objectives") })
    }

    @Test
    fun `Chunked compresses in chunks`() = runTest {
        val client = CompressionMockClient()
        val session = LLMWriteSession(client, "gpt-4", buildLongConversation())

        HistoryCompressionStrategy.Chunked(3).compress(session)

        val msgs = session.prompt.messages
        assertTrue(msgs.size < 9)
        assertEquals("system", msgs[0].role)
        val tldrCount = msgs.count { it.content.contains("Key Objectives") }
        assertTrue("Should have multiple TLDR chunks, got $tldrCount", tldrCount >= 2)
    }

    @Test
    fun `TokenBudget shouldCompress returns true when over threshold`() {
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 100, threshold = 0.5, tokenizer = TokenEstimator
        )

        val shortMessages = listOf(ChatMessage.user("Hi"))
        assertFalse(strategy.shouldCompress(shortMessages))

        val longMessages = (1..50).map { ChatMessage.user("This is message number $it with some content") }
        assertTrue(strategy.shouldCompress(longMessages))
    }

    @Test
    fun `TokenBudget compresses when over threshold`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 50, threshold = 0.3, tokenizer = TokenEstimator
        )

        val session = LLMWriteSession(client, "gpt-4", buildLongConversation())
        val originalSize = session.prompt.messages.size

        strategy.compress(session)

        assertTrue(session.prompt.messages.size < originalSize)
    }

    @Test
    fun `TokenBudget skips compression when under threshold`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 100000, threshold = 0.75, tokenizer = TokenEstimator
        )

        val session = LLMWriteSession(client, "gpt-4", prompt("test") { user("Hi") })
        val originalSize = session.prompt.messages.size

        strategy.compress(session)

        assertEquals(originalSize, session.prompt.messages.size)
        assertEquals(0, client.callCount)
    }

    @Test
    fun `replaceHistoryWithTLDR uses WholeHistory by default`() = runTest {
        val client = CompressionMockClient()
        val session = LLMWriteSession(client, "gpt-4", buildLongConversation())

        session.replaceHistoryWithTLDR()

        assertTrue(session.prompt.messages.size < 9)
        assertTrue(session.prompt.messages.any { it.content.contains("Key Objectives") })
    }

    @Test
    fun `compressIfNeeded triggers when over budget`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 50, threshold = 0.3, tokenizer = TokenEstimator
        )

        val session = LLMWriteSession(client, "gpt-4", buildLongConversation())
        session.compressIfNeeded(strategy)

        assertTrue(client.callCount > 0)
        assertTrue(session.prompt.messages.size < 9)
    }

    @Test
    fun `compressIfNeeded skips when under budget`() = runTest {
        val client = CompressionMockClient()
        val strategy = HistoryCompressionStrategy.TokenBudget(
            maxTokens = 100000, threshold = 0.75, tokenizer = TokenEstimator
        )

        val session = LLMWriteSession(client, "gpt-4", prompt("test") { user("Hi") })
        session.compressIfNeeded(strategy)

        assertEquals(0, client.callCount)
    }

    @Test
    fun `compression drops trailing tool calls before TLDR`() = runTest {
        val client = CompressionMockClient()
        val toolCall = ToolCall("call_1", "test_tool", """{"a":1}""")
        val p = prompt("test") {
            system("System")
            user("Do something")
            assistantWithToolCalls(listOf(toolCall))
        }
        val session = LLMWriteSession(client, "gpt-4", p)

        HistoryCompressionStrategy.WholeHistory.compress(session)

        val msgs = session.prompt.messages
        assertEquals("system", msgs[0].role)
    }

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

    @Test
    fun `splitHistoryBySystemMessages splits correctly`() {
        val strategy = object : HistoryCompressionStrategy() {
            override suspend fun compress(session: LLMWriteSession, listener: CompressionEventListener?, memoryMessages: List<ChatMessage>) {}
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
        assertEquals(4, blocks[0].size)
        assertEquals(3, blocks[1].size)
    }
}
