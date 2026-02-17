package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MultiChatClientTest {

    private class MockClient(
        override val provider: LLMProvider,
        private val response: String = "reply",
        private val delayMs: Long = 0,
        private val shouldFail: Boolean = false
    ) : ChatClient {
        override suspend fun chat(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): String = chatWithUsage(messages, model, maxTokens, samplingParams, tools).content

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): ChatResult {
            if (delayMs > 0) delay(delayMs)
            if (shouldFail) throw RuntimeException("模拟失败: ${provider.name}")
            return ChatResult(content = response, usage = Usage(10, 5, 15))
        }

        override fun chatStream(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): Flow<String> = flowOf(response)

        override suspend fun chatStreamWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>,
            onChunk: suspend (String) -> Unit
        ): StreamResult {
            onChunk(response)
            return StreamResult(content = response)
        }

        override suspend fun listModels(): List<ModelInfo> = emptyList()
        override suspend fun getBalance(): BalanceInfo? = null
        override fun close() {}
    }

    private val messages = listOf(ChatMessage.user("hello"))

    @Test
    fun `executeAll returns results from all targets`() = runBlocking {
        val multi = MultiChatClient()
        val targets = listOf(
            ChatTarget(MockClient(LLMProvider.OpenAI, "openai"), "gpt-4o", "GPT-4o"),
            ChatTarget(MockClient(LLMProvider.Claude, "claude"), "claude-sonnet", "Claude")
        )

        val results = multi.executeAll(messages, targets)
        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals("openai", results[0].content)
        assertEquals("claude", results[1].content)
        assertEquals("GPT-4o", results[0].target.label)
        assertEquals("Claude", results[1].target.label)
    }

    @Test
    fun `executeAll handles partial failure`() = runBlocking {
        val multi = MultiChatClient()
        val targets = listOf(
            ChatTarget(MockClient(LLMProvider.OpenAI, "ok"), "gpt-4o"),
            ChatTarget(MockClient(LLMProvider.Claude, shouldFail = true), "claude-sonnet")
        )

        val results = multi.executeAll(messages, targets)
        assertEquals(2, results.size)
        assertTrue(results[0].isSuccess)
        assertFalse(results[1].isSuccess)
        assertEquals("ok", results[0].content)
        assertNotNull(results[1].error)
    }

    @Test
    fun `executeFirst returns first success`() = runBlocking {
        val multi = MultiChatClient()
        val targets = listOf(
            ChatTarget(MockClient(LLMProvider.OpenAI, shouldFail = true), "gpt-4o"),
            ChatTarget(MockClient(LLMProvider.Claude, "claude ok"), "claude-sonnet")
        )

        val result = multi.executeFirst(messages, targets)
        assertTrue(result.isSuccess)
        assertEquals("claude ok", result.content)
    }

    @Test
    fun `executeFirst returns last error when all fail`() = runBlocking {
        val multi = MultiChatClient()
        val targets = listOf(
            ChatTarget(MockClient(LLMProvider.OpenAI, shouldFail = true), "gpt-4o"),
            ChatTarget(MockClient(LLMProvider.Claude, shouldFail = true), "claude-sonnet")
        )

        val result = multi.executeFirst(messages, targets)
        assertFalse(result.isSuccess)
        assertNotNull(result.error)
    }

    @Test
    fun `executeSuccessful filters out failures`() = runBlocking {
        val multi = MultiChatClient()
        val targets = listOf(
            ChatTarget(MockClient(LLMProvider.OpenAI, "ok1"), "gpt-4o"),
            ChatTarget(MockClient(LLMProvider.Claude, shouldFail = true), "claude"),
            ChatTarget(MockClient(LLMProvider.Gemini, "ok2"), "gemini")
        )

        val results = multi.executeSuccessful(messages, targets)
        assertEquals(2, results.size)
        assertEquals("ok1", results[0].content)
        assertEquals("ok2", results[1].content)
    }

    @Test
    fun `executeAll runs in parallel`() = runBlocking {
        val multi = MultiChatClient()
        val targets = listOf(
            ChatTarget(MockClient(LLMProvider.OpenAI, "a", delayMs = 200), "m1"),
            ChatTarget(MockClient(LLMProvider.Claude, "b", delayMs = 200), "m2")
        )

        val start = System.currentTimeMillis()
        val results = multi.executeAll(messages, targets)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertTrue("并行执行耗时 ${elapsed}ms，预期 < 350ms", elapsed < 350)
    }

    @Test
    fun `empty targets returns empty list`() = runBlocking {
        val multi = MultiChatClient()
        val results = multi.executeAll(messages, emptyList())
        assertTrue(results.isEmpty())
    }

    // ========== targetsFromRouter ==========

    @Test
    fun `targetsFromRouter builds targets from router`() {
        val multi = MultiChatClient()
        val openai = MockClient(LLMProvider.OpenAI, "openai")
        val claude = MockClient(LLMProvider.Claude, "claude")
        val router = ChatClientRouter("openai" to openai, "claude" to claude)

        val targets = multi.targetsFromRouter(router, mapOf(
            "openai" to "gpt-4o",
            "claude" to "claude-sonnet"
        ))

        assertEquals(2, targets.size)
        assertEquals("gpt-4o", targets[0].model)
        assertEquals("claude-sonnet", targets[1].model)
        assertEquals("openai/gpt-4o", targets[0].label)
    }

    @Test
    fun `targetsFromRouter skips unregistered providers`() {
        val multi = MultiChatClient()
        val openai = MockClient(LLMProvider.OpenAI, "openai")
        val router = ChatClientRouter("openai" to openai)

        val targets = multi.targetsFromRouter(router, mapOf(
            "openai" to "gpt-4o",
            "nonexistent" to "model"
        ))

        assertEquals(1, targets.size)
        assertEquals("gpt-4o", targets[0].model)
    }

    @Test
    fun `targetsFromRouter with executeAll integration`() = runBlocking {
        val multi = MultiChatClient()
        val openai = MockClient(LLMProvider.OpenAI, "openai result")
        val claude = MockClient(LLMProvider.Claude, "claude result")
        val router = ChatClientRouter("openai" to openai, "claude" to claude)

        val targets = multi.targetsFromRouter(router, mapOf(
            "openai" to "gpt-4o",
            "claude" to "claude-sonnet"
        ))
        val results = multi.executeAll(messages, targets)

        assertEquals(2, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals("openai result", results[0].content)
        assertEquals("claude result", results[1].content)
    }
}
