package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatMessage <-> Message 互转测试
 */
class MessageConversionTest {

    // ========== ChatMessage.toMessage() 测试 ==========

    @Test
    fun `system ChatMessage converts to Message System`() {
        val cm = ChatMessage.system("You are helpful")
        val msg = cm.toMessage()
        assertTrue(msg is Message.System)
        assertEquals("You are helpful", msg.content)
        assertEquals(MessageRole.System, msg.role)
    }

    @Test
    fun `user ChatMessage converts to Message User`() {
        val cm = ChatMessage.user("Hello")
        val msg = cm.toMessage()
        assertTrue(msg is Message.User)
        assertEquals("Hello", msg.content)
        assertEquals(MessageRole.User, msg.role)
    }

    @Test
    fun `assistant ChatMessage converts to Message Assistant`() {
        val cm = ChatMessage.assistant("Hi there", finishReason = "stop", timestamp = 1000L)
        val msg = cm.toMessage()
        assertTrue(msg is Message.Assistant)
        val assistant = msg as Message.Assistant
        assertEquals("Hi there", assistant.content)
        assertEquals("stop", assistant.finishReason)
        assertEquals(1000L, assistant.metaInfo.timestamp)
    }

    @Test
    fun `tool ChatMessage converts to Message Tool Result`() {
        val cm = ChatMessage.toolResult(ToolResult("call_1", "calculator", "42"))
        val msg = cm.toMessage()
        assertTrue(msg is Message.Tool.Result)
        val result = msg as Message.Tool.Result
        assertEquals("call_1", result.id)
        assertEquals("calculator", result.tool)
        assertEquals("42", result.content)
    }

    @Test
    fun `unknown role ChatMessage falls back to Message User`() {
        val cm = ChatMessage("unknown_role", "content")
        val msg = cm.toMessage()
        assertTrue(msg is Message.User)
        assertEquals("content", msg.content)
    }

    @Test
    fun `ChatMessage with metadata preserves metadata in conversion`() {
        val meta = JsonObject(mapOf("key" to JsonPrimitive("value")))
        val cm = ChatMessage("user", "test", metadata = meta)
        val msg = cm.toMessage() as Message.User
        assertEquals("value", (msg.metaInfo.metadata?.get("key") as JsonPrimitive).content)
    }

    // ========== Message.toChatMessage() 测试 ==========

    @Test
    fun `Message System converts to system ChatMessage`() {
        val msg = Message.System("system prompt")
        val cm = msg.toChatMessage()
        assertEquals("system", cm.role)
        assertEquals("system prompt", cm.content)
    }

    @Test
    fun `Message User converts to user ChatMessage`() {
        val msg = Message.User("hello")
        val cm = msg.toChatMessage()
        assertEquals("user", cm.role)
        assertEquals("hello", cm.content)
    }

    @Test
    fun `Message Assistant converts to assistant ChatMessage`() {
        val msg = Message.Assistant("response", finishReason = "stop")
        val cm = msg.toChatMessage()
        assertEquals("assistant", cm.role)
        assertEquals("response", cm.content)
        assertEquals("stop", cm.finishReason)
    }

    @Test
    fun `Message Reasoning converts to assistant ChatMessage`() {
        val msg = Message.Reasoning(content = "thinking...")
        val cm = msg.toChatMessage()
        assertEquals("assistant", cm.role)
        assertEquals("thinking...", cm.content)
    }

    @Test
    fun `Message Tool Call converts to assistant ChatMessage with toolCalls`() {
        val msg = Message.Tool.Call(id = "call_1", tool = "calc", content = """{"a":1}""")
        val cm = msg.toChatMessage()
        assertEquals("assistant", cm.role)
        assertEquals("tool_calls", cm.finishReason)
        assertEquals(1, cm.toolCalls?.size)
        assertEquals("call_1", cm.toolCalls?.first()?.id)
        assertEquals("calc", cm.toolCalls?.first()?.name)
    }

    @Test
    fun `Message Tool Result converts to tool ChatMessage`() {
        val msg = Message.Tool.Result(id = "call_1", tool = "calc", content = "42")
        val cm = msg.toChatMessage()
        assertEquals("tool", cm.role)
        assertEquals("42", cm.content)
        assertEquals("call_1", cm.toolCallId)
        assertEquals("calc", cm.toolName)
    }

    @Test
    fun `Message with timestamp preserves timestamp in conversion`() {
        val msg = Message.User("test", RequestMetaInfo(timestamp = 5000L))
        val cm = msg.toChatMessage()
        assertEquals(5000L, cm.timestamp)
    }

    @Test
    fun `Message with metadata preserves metadata in conversion`() {
        val meta = JsonObject(mapOf("k" to JsonPrimitive("v")))
        val msg = Message.System("sys", RequestMetaInfo(metadata = meta))
        val cm = msg.toChatMessage()
        assertEquals("v", (cm.metadata?.get("k") as JsonPrimitive).content)
    }

    // ========== 往返一致性测试 ==========

    @Test
    fun `system message roundtrip preserves content`() {
        val original = ChatMessage.system("system prompt")
        val roundtrip = original.toMessage().toChatMessage()
        assertEquals(original.role, roundtrip.role)
        assertEquals(original.content, roundtrip.content)
    }

    @Test
    fun `user message roundtrip preserves content`() {
        val original = ChatMessage.user("user input")
        val roundtrip = original.toMessage().toChatMessage()
        assertEquals(original.role, roundtrip.role)
        assertEquals(original.content, roundtrip.content)
    }

    @Test
    fun `assistant message roundtrip preserves content and finishReason`() {
        val original = ChatMessage.assistant("response", finishReason = "stop", timestamp = 1000L)
        val roundtrip = original.toMessage().toChatMessage()
        assertEquals(original.role, roundtrip.role)
        assertEquals(original.content, roundtrip.content)
        assertEquals(original.finishReason, roundtrip.finishReason)
        assertEquals(original.timestamp, roundtrip.timestamp)
    }

    @Test
    fun `tool result roundtrip preserves all fields`() {
        val original = ChatMessage.toolResult(ToolResult("call_1", "calc", "42"))
        val roundtrip = original.toMessage().toChatMessage()
        assertEquals(original.role, roundtrip.role)
        assertEquals(original.content, roundtrip.content)
        assertEquals(original.toolCallId, roundtrip.toolCallId)
        assertEquals(original.toolName, roundtrip.toolName)
    }

    // ========== 批量转换测试 ==========

    @Test
    fun `toMessages converts list of ChatMessages`() {
        val chatMessages = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("usr"),
            ChatMessage.assistant("ast")
        )
        val messages = chatMessages.toMessages()
        assertEquals(3, messages.size)
        assertTrue(messages[0] is Message.System)
        assertTrue(messages[1] is Message.User)
        assertTrue(messages[2] is Message.Assistant)
    }

    @Test
    fun `toChatMessages converts list of Messages`() {
        val messages: List<Message> = listOf(
            Message.System("sys"),
            Message.User("usr"),
            Message.Assistant("ast")
        )
        val chatMessages = messages.toChatMessages()
        assertEquals(3, chatMessages.size)
        assertEquals("system", chatMessages[0].role)
        assertEquals("user", chatMessages[1].role)
        assertEquals("assistant", chatMessages[2].role)
    }

    // ========== ChatResult.toAssistantMessage() 测试 ==========

    @Test
    fun `ChatResult converts to Assistant message`() {
        val result = ChatResult(
            content = "answer",
            usage = Usage(promptTokens = 10, completionTokens = 20, totalTokens = 30),
            finishReason = "stop"
        )
        val msg = result.toAssistantMessage()
        assertTrue(msg is Message.Assistant)
        val assistant = msg as Message.Assistant
        assertEquals("answer", assistant.content)
        assertEquals("stop", assistant.finishReason)
        assertEquals(30, assistant.metaInfo.totalTokensCount)
        assertEquals(10, assistant.metaInfo.inputTokensCount)
        assertEquals(20, assistant.metaInfo.outputTokensCount)
    }

    @Test
    fun `ChatResult with thinking converts to Reasoning message`() {
        val result = ChatResult(content = "answer", thinking = "let me think...")
        val msg = result.toAssistantMessage()
        assertTrue(msg is Message.Reasoning)
        assertEquals("let me think...", msg.content)
    }

    @Test
    fun `ChatResult toMessages includes all parts`() {
        val result = ChatResult(
            content = "I'll search for that",
            thinking = "need to search",
            toolCalls = listOf(ToolCall("c1", "search", """{"q":"test"}""")),
            finishReason = "tool_calls"
        )
        val messages = result.toMessages()
        assertEquals(3, messages.size)
        assertTrue(messages[0] is Message.Reasoning)
        assertTrue(messages[1] is Message.Assistant)
        assertTrue(messages[2] is Message.Tool.Call)
    }

    @Test
    fun `ChatResult toMessages without thinking or tools`() {
        val result = ChatResult(content = "simple answer", finishReason = "stop")
        val messages = result.toMessages()
        assertEquals(1, messages.size)
        assertTrue(messages[0] is Message.Assistant)
        assertEquals("simple answer", messages[0].content)
    }
}
