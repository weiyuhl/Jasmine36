package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ToolExecutorTest {

    private class FakeClient(private val responses: MutableList<ChatResult>) : ChatClient {
        override val provider = LLMProvider.OpenAI
        var callCount = 0; private set

        override suspend fun chat(
            messages: List<ChatMessage>, model: String, maxTokens: Int?,
            samplingParams: SamplingParams?, tools: List<ToolDescriptor>
        ) = chatWithUsage(messages, model, maxTokens, samplingParams, tools).content

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>, model: String, maxTokens: Int?,
            samplingParams: SamplingParams?, tools: List<ToolDescriptor>
        ): ChatResult { callCount++; return responses.removeAt(0) }

        override fun chatStream(
            messages: List<ChatMessage>, model: String, maxTokens: Int?,
            samplingParams: SamplingParams?, tools: List<ToolDescriptor>
        ): Flow<String> = flowOf("")

        override suspend fun chatStreamWithUsage(
            messages: List<ChatMessage>, model: String, maxTokens: Int?,
            samplingParams: SamplingParams?, tools: List<ToolDescriptor>,
            onChunk: suspend (String) -> Unit
        ): StreamResult {
            val r = chatWithUsage(messages, model, maxTokens, samplingParams, tools)
            if (r.content.isNotEmpty()) onChunk(r.content)
            return StreamResult(r.content, r.usage, r.finishReason, r.toolCalls)
        }

        override suspend fun listModels(): List<ModelInfo> = emptyList()
        override fun close() {}
    }

    @Test
    fun `no tool calls returns immediately`() = runBlocking {
        val client = FakeClient(mutableListOf(ChatResult("Hello!", Usage(10, 5, 15), "stop")))
        val registry = ToolRegistry.build { register(CalculatorTool.plus) }
        val result = ToolExecutor(client, registry).execute(listOf(ChatMessage.user("Hi")), "m")
        assertEquals("Hello!", result.content)
        assertEquals(1, client.callCount)
    }

    @Test
    fun `tool call loop`() = runBlocking {
        val client = FakeClient(mutableListOf(
            ChatResult("", Usage(20, 10, 30), "tool_calls",
                listOf(ToolCall("tc1", "calculator_plus", """{"a":10,"b":20}"""))),
            ChatResult("Result is 30.0", Usage(30, 10, 40), "stop")
        ))
        val registry = ToolRegistry.build { registerAll(*CalculatorTool.allTools().toTypedArray()) }
        val result = ToolExecutor(client, registry).execute(listOf(ChatMessage.user("10+20?")), "m")
        assertEquals("Result is 30.0", result.content)
        assertEquals(2, client.callCount)
        assertEquals(50, result.usage?.promptTokens)
    }

    @Test
    fun `max iterations`() = runBlocking {
        val responses = mutableListOf<ChatResult>()
        repeat(5) {
            responses.add(ChatResult("", Usage(10, 5, 15), "tool_calls",
                listOf(ToolCall("tc$it", "calculator_plus", """{"a":1,"b":1}"""))))
        }
        val client = FakeClient(responses)
        val registry = ToolRegistry.build { register(CalculatorTool.plus) }
        val result = ToolExecutor(client, registry, maxIterations = 3).execute(listOf(ChatMessage.user("loop")), "m")
        assertTrue(result.content.contains("Exceeded"))
        assertEquals(3, client.callCount)
    }

    @Test
    fun `stream mode`() = runBlocking {
        val client = FakeClient(mutableListOf(
            ChatResult("", Usage(10, 5, 15), "tool_calls",
                listOf(ToolCall("tc1", "get_current_time", "{}"))),
            ChatResult("Time is now.", Usage(15, 8, 23), "stop")
        ))
        val registry = ToolRegistry.build { register(GetCurrentTimeTool) }
        val chunks = mutableListOf<String>()
        val result = ToolExecutor(client, registry).executeStream(
            listOf(ChatMessage.user("time?")), "m"
        ) { chunks.add(it) }
        assertEquals("Time is now.", result.content)
        assertTrue(chunks.contains("Time is now."))
    }
}
