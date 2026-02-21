package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LLMSessionTest {

    /** 简单 mock client，返回固定回复 */
    private class MockClient(
        private val response: String = "Hello!",
        private val toolCallsResponse: List<ToolCall>? = null
    ) : ChatClient {
        override val provider = LLMProvider.OpenAI
        var lastMessages: List<ChatMessage>? = null
        var lastTools: List<ToolDescriptor>? = null
        var callCount = 0

        override suspend fun chat(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>, toolChoice: ToolChoice?
        ): String = response

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>, model: String,
            maxTokens: Int?, samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>, toolChoice: ToolChoice?
        ): ChatResult {
            lastMessages = messages
            lastTools = tools
            callCount++
            return if (toolCallsResponse != null && callCount == 1) {
                ChatResult(content = "", toolCalls = toolCallsResponse,
                    usage = Usage(10, 5, 15), finishReason = "tool_calls")
            } else {
                ChatResult(content = response, usage = Usage(10, 5, 15), finishReason = "stop")
            }
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
        ): StreamResult {
            lastMessages = messages
            lastTools = tools
            callCount++
            onChunk(response)
            return StreamResult(content = response, usage = Usage(10, 5, 15))
        }

        override suspend fun listModels(): List<ModelInfo> = emptyList()
        override fun close() {}
    }

    @Test
    fun `session accumulates messages`() = runTest {
        val client = MockClient("I'm fine, thanks!")
        val initialPrompt = prompt("chat") {
            system("You are helpful.")
        }

        val session = LLMSession(client, "gpt-4", initialPrompt)

        // 追加用户消息
        session.appendPrompt { user("How are you?") }
        assertEquals(2, session.prompt.messages.size)

        // 请求 LLM，response 自动追加
        val result = session.requestLLM()
        assertEquals("I'm fine, thanks!", result.content)
        assertEquals(3, session.prompt.messages.size)
        assertEquals("assistant", session.prompt.messages[2].role)
        assertEquals("I'm fine, thanks!", session.prompt.messages[2].content)

        session.close()
    }

    @Test
    fun `session passes tools to client`() = runTest {
        val client = MockClient("Result")
        val tools = listOf(
            ToolDescriptor("test_tool", "A test tool",
                requiredParameters = listOf(
                    ToolParameterDescriptor("input", "Input value", ToolParameterType.StringType)
                ))
        )
        val session = LLMSession(client, "gpt-4", prompt("test") { user("Hi") }, tools)

        session.requestLLM()
        assertEquals(1, client.lastTools?.size)
        assertEquals("test_tool", client.lastTools!![0].name)

        session.close()
    }

    @Test
    fun `requestLLMWithoutTools sends empty tools`() = runTest {
        val client = MockClient("No tools")
        val tools = listOf(ToolDescriptor("tool1", "desc"))
        val session = LLMSession(client, "gpt-4", prompt("test") { user("Hi") }, tools)

        session.requestLLMWithoutTools()
        assertTrue(client.lastTools!!.isEmpty())

        session.close()
    }

    @Test
    fun `clearHistory removes all messages`() = runTest {
        val session = LLMSession(
            MockClient(), "gpt-4",
            prompt("test") {
                system("System")
                user("User")
                assistant("Assistant")
            }
        )

        assertEquals(3, session.prompt.messages.size)
        session.clearHistory()
        assertEquals(0, session.prompt.messages.size)

        session.close()
    }

    @Test
    fun `leaveLastNMessages preserves system`() = runTest {
        val session = LLMSession(
            MockClient(), "gpt-4",
            prompt("test") {
                system("System prompt")
                user("Q1")
                assistant("A1")
                user("Q2")
                assistant("A2")
            }
        )

        session.leaveLastNMessages(2, preserveSystem = true)
        val msgs = session.prompt.messages
        // system + last 2
        assertEquals(3, msgs.size)
        assertEquals("system", msgs[0].role)
        assertEquals("Q2", msgs[1].content)
        assertEquals("A2", msgs[2].content)

        session.close()
    }

    @Test
    fun `dropLastNMessages removes from end`() = runTest {
        val session = LLMSession(
            MockClient(), "gpt-4",
            prompt("test") {
                system("System")
                user("Q1")
                assistant("A1")
            }
        )

        session.dropLastNMessages(1)
        assertEquals(2, session.prompt.messages.size)
        assertEquals("user", session.prompt.messages.last().role)

        session.close()
    }

    @Test
    fun `leaveMessagesFromTimestamp filters by timestamp`() = runTest {
        val now = System.currentTimeMillis()
        val initialPrompt = Prompt(
            messages = listOf(
                ChatMessage("system", "System prompt", timestamp = now - 10000),
                ChatMessage("user", "Old question", timestamp = now - 5000),
                ChatMessage("assistant", "Old answer", timestamp = now - 4000),
                ChatMessage("user", "New question", timestamp = now),
                ChatMessage("assistant", "New answer", timestamp = now + 1000)
            ),
            id = "test"
        )
        val session = LLMSession(MockClient(), "gpt-4", initialPrompt)

        session.leaveMessagesFromTimestamp(now)

        val msgs = session.prompt.messages
        // system (preserved) + 2 new messages
        assertEquals(3, msgs.size)
        assertEquals("system", msgs[0].role)
        assertEquals("New question", msgs[1].content)
        assertEquals("New answer", msgs[2].content)

        session.close()
    }

    @Test
    fun `leaveMessagesFromTimestamp without preserveSystem`() = runTest {
        val now = System.currentTimeMillis()
        val initialPrompt = Prompt(
            messages = listOf(
                ChatMessage("system", "System prompt", timestamp = now - 10000),
                ChatMessage("user", "Old question", timestamp = now - 5000),
                ChatMessage("user", "New question", timestamp = now)
            ),
            id = "test"
        )
        val session = LLMSession(MockClient(), "gpt-4", initialPrompt)

        session.leaveMessagesFromTimestamp(now, preserveSystem = false)

        val msgs = session.prompt.messages
        // only the new message
        assertEquals(1, msgs.size)
        assertEquals("New question", msgs[0].content)

        session.close()
    }

    @Test
    fun `rewritePrompt allows full control`() = runTest {
        val session = LLMSession(
            MockClient(), "gpt-4",
            prompt("test") {
                system("Old system")
                user("Old user")
            }
        )

        session.rewritePrompt { p ->
            prompt("test") {
                system("New system")
                user("Summary of previous conversation")
            }
        }

        assertEquals(2, session.prompt.messages.size)
        assertEquals("New system", session.prompt.messages[0].content)

        session.close()
    }

    @Test
    fun `tool choice methods work`() = runTest {
        val session = LLMSession(MockClient(), "gpt-4", prompt("test") { user("Hi") })

        session.setToolChoiceAuto()
        assertTrue(session.prompt.toolChoice is ToolChoice.Auto)

        session.setToolChoiceRequired()
        assertTrue(session.prompt.toolChoice is ToolChoice.Required)

        session.setToolChoiceNone()
        assertTrue(session.prompt.toolChoice is ToolChoice.None)

        session.setToolChoiceNamed("calculator")
        assertEquals("calculator", (session.prompt.toolChoice as ToolChoice.Named).toolName)

        session.unsetToolChoice()
        assertNull(session.prompt.toolChoice)

        session.close()
    }

    @Test
    fun `stream request accumulates response`() = runTest {
        val client = MockClient("Streamed response")
        val session = LLMSession(client, "gpt-4", prompt("test") { user("Hi") })

        val chunks = mutableListOf<String>()
        val result = session.requestLLMStream(onChunk = { chunks.add(it) })

        assertEquals("Streamed response", result.content)
        assertEquals(1, chunks.size)
        assertEquals(2, session.prompt.messages.size)
        assertEquals("assistant", session.prompt.messages[1].role)

        session.close()
    }

    @Test(expected = IllegalStateException::class)
    fun `closed session throws on appendPrompt`() = runTest {
        val session = LLMSession(MockClient(), "gpt-4", prompt("test") { user("Hi") })
        session.close()
        session.appendPrompt { user("Should fail") }
    }

    @Test
    fun `session extension function works`() = runTest {
        val client = MockClient("Extension result")
        val p = prompt("test") { system("System") }

        val result = client.session("gpt-4", p) {
            appendPrompt { user("Hello") }
            requestLLM()
        }

        assertEquals("Extension result", result.content)
    }
}
