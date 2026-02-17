package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.BalanceInfo
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import com.lhzkml.jasmine.core.prompt.model.SamplingParams
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatClientRouterTest {

    private class MockChatClient(
        override val provider: LLMProvider,
        private val response: String = "mock response"
    ) : ChatClient {
        var lastModel: String? = null
        var closed = false

        override suspend fun chat(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): String {
            lastModel = model
            return response
        }

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>
        ): ChatResult {
            lastModel = model
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
        override fun close() { closed = true }
    }

    private val messages = listOf(ChatMessage.user("hello"))

    // ========== 基本路由 ==========

    @Test
    fun `register and route to correct client`() = runBlocking {
        val router = ChatClientRouter()
        val openai = MockChatClient(LLMProvider.OpenAI, "openai reply")
        val claude = MockChatClient(LLMProvider.Claude, "claude reply")

        router.register("openai", openai)
        router.register("claude", claude)

        assertEquals("openai reply", router.chat("openai", messages, "gpt-4o"))
        assertEquals("claude reply", router.chat("claude", messages, "claude-sonnet"))
        assertEquals("gpt-4o", openai.lastModel)
        assertEquals("claude-sonnet", claude.lastModel)
    }

    // ========== vararg Pair 构造 ==========

    @Test
    fun `construct with vararg pairs`() = runBlocking {
        val openai = MockChatClient(LLMProvider.OpenAI, "openai")
        val claude = MockChatClient(LLMProvider.Claude, "claude")

        val router = ChatClientRouter("openai" to openai, "claude" to claude)

        assertEquals("openai", router.chat("openai", messages, "gpt-4o"))
        assertEquals("claude", router.chat("claude", messages, "claude-sonnet"))
    }

    // ========== fromClients 自动分组 ==========

    @Test
    fun `fromClients auto groups by provider name`() = runBlocking {
        val openai = MockChatClient(LLMProvider.OpenAI, "openai")
        val claude = MockChatClient(LLMProvider.Claude, "claude")

        val router = ChatClientRouter.fromClients(openai, claude)

        assertEquals("openai", router.chat("openai", messages, "gpt-4o"))
        assertEquals("claude", router.chat("claude", messages, "claude-sonnet"))
    }

    // ========== Fallback ==========

    @Test
    fun `fallback when provider not found`() = runBlocking {
        val openai = MockChatClient(LLMProvider.OpenAI, "fallback reply")
        val router = ChatClientRouter(
            "openai" to openai,
            fallback = FallbackConfig("openai", "gpt-4o-mini")
        )

        val result = router.chat("unknown", messages, "some-model")
        assertEquals("fallback reply", result)
        // fallback 应该使用 fallback 配置的模型名，而不是原始请求的模型名
        assertEquals("gpt-4o-mini", openai.lastModel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fallback provider must be registered`() {
        val openai = MockChatClient(LLMProvider.OpenAI)
        ChatClientRouter(
            "openai" to openai,
            fallback = FallbackConfig("nonexistent", "model")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when no client and no fallback`(): Unit = runBlocking {
        val router = ChatClientRouter()
        router.chat("nonexistent", messages, "model")
    }

    // ========== 生命周期 ==========

    @Test
    fun `unregister closes client`() {
        val router = ChatClientRouter()
        val client = MockChatClient(LLMProvider.OpenAI)
        router.register("openai", client)

        router.unregister("openai")
        assertTrue(client.closed)
        assertNull(router.getClient("openai"))
    }

    @Test
    fun `close closes all clients`() {
        val c1 = MockChatClient(LLMProvider.OpenAI)
        val c2 = MockChatClient(LLMProvider.Claude)
        val router = ChatClientRouter("openai" to c1, "claude" to c2)

        router.close()
        assertTrue(c1.closed)
        assertTrue(c2.closed)
        assertTrue(router.registeredIds().isEmpty())
    }

    // ========== 辅助方法 ==========

    @Test
    fun `registeredIds returns all ids`() {
        val router = ChatClientRouter(
            "a" to MockChatClient(LLMProvider.OpenAI),
            "b" to MockChatClient(LLMProvider.Claude)
        )
        assertEquals(setOf("a", "b"), router.registeredIds())
    }

    @Test
    fun `chatWithUsage routes correctly`() = runBlocking {
        val router = ChatClientRouter("openai" to MockChatClient(LLMProvider.OpenAI, "with usage"))

        val result = router.chatWithUsage("openai", messages, "gpt-4o")
        assertEquals("with usage", result.content)
        assertNotNull(result.usage)
    }

    @Test
    fun `listAllModels aggregates from all clients`() = runBlocking {
        val router = ChatClientRouter(
            "openai" to MockChatClient(LLMProvider.OpenAI),
            "claude" to MockChatClient(LLMProvider.Claude)
        )

        val allModels = router.listAllModels()
        assertEquals(2, allModels.size)
        assertTrue("openai" in allModels)
        assertTrue("claude" in allModels)
    }
}
