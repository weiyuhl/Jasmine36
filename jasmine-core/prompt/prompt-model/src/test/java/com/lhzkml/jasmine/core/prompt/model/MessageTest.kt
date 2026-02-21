package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Message 密封类层次测试
 */
class MessageTest {

    // ========== MessageRole 测试 ==========

    @Test
    fun `MessageRole contains all expected values`() {
        val roles = MessageRole.entries
        assertEquals(5, roles.size)
        assertTrue(roles.contains(MessageRole.System))
        assertTrue(roles.contains(MessageRole.User))
        assertTrue(roles.contains(MessageRole.Assistant))
        assertTrue(roles.contains(MessageRole.Reasoning))
        assertTrue(roles.contains(MessageRole.Tool))
    }

    // ========== RequestMetaInfo 测试 ==========

    @Test
    fun `RequestMetaInfo Empty has null fields`() {
        val empty = RequestMetaInfo.Empty
        assertNull(empty.timestamp)
        assertNull(empty.metadata)
    }

    @Test
    fun `RequestMetaInfo with values`() {
        val meta = RequestMetaInfo(timestamp = 1000L, metadata = JsonObject(mapOf("key" to JsonPrimitive("value"))))
        assertEquals(1000L, meta.timestamp)
        assertEquals("value", (meta.metadata?.get("key") as JsonPrimitive).content)
    }

    // ========== ResponseMetaInfo 测试 ==========

    @Test
    fun `ResponseMetaInfo Empty has null fields`() {
        val empty = ResponseMetaInfo.Empty
        assertNull(empty.timestamp)
        assertNull(empty.metadata)
        assertNull(empty.totalTokensCount)
        assertNull(empty.inputTokensCount)
        assertNull(empty.outputTokensCount)
    }

    @Test
    fun `ResponseMetaInfo with token counts`() {
        val meta = ResponseMetaInfo(
            timestamp = 2000L,
            totalTokensCount = 100,
            inputTokensCount = 40,
            outputTokensCount = 60
        )
        assertEquals(2000L, meta.timestamp)
        assertEquals(100, meta.totalTokensCount)
        assertEquals(40, meta.inputTokensCount)
        assertEquals(60, meta.outputTokensCount)
    }

    // ========== Message.User 测试 ==========

    @Test
    fun `User message has correct role and content`() {
        val msg = Message.User("Hello")
        assertEquals(MessageRole.User, msg.role)
        assertEquals("Hello", msg.content)
        assertEquals(RequestMetaInfo.Empty, msg.metaInfo)
    }

    @Test
    fun `User message is Request`() {
        val msg: Message = Message.User("test")
        assertTrue(msg is Message.Request)
        assertFalse(msg is Message.Response)
    }

    // ========== Message.System 测试 ==========

    @Test
    fun `System message has correct role and content`() {
        val msg = Message.System("You are helpful")
        assertEquals(MessageRole.System, msg.role)
        assertEquals("You are helpful", msg.content)
    }

    @Test
    fun `System message is Request`() {
        val msg: Message = Message.System("system prompt")
        assertTrue(msg is Message.Request)
        assertFalse(msg is Message.Response)
    }

    // ========== Message.Assistant 测试 ==========

    @Test
    fun `Assistant message has correct role and content`() {
        val msg = Message.Assistant("I can help")
        assertEquals(MessageRole.Assistant, msg.role)
        assertEquals("I can help", msg.content)
        assertNull(msg.finishReason)
    }

    @Test
    fun `Assistant message with finishReason`() {
        val msg = Message.Assistant("done", finishReason = "stop")
        assertEquals("stop", msg.finishReason)
    }

    @Test
    fun `Assistant message is Response`() {
        val msg: Message = Message.Assistant("test")
        assertTrue(msg is Message.Response)
        assertFalse(msg is Message.Request)
    }

    @Test
    fun `Assistant copy with updated metaInfo`() {
        val original = Message.Assistant("test", ResponseMetaInfo(timestamp = 1000L))
        val updated = original.copy(updatedMetaInfo = ResponseMetaInfo(timestamp = 2000L, totalTokensCount = 50))
        assertEquals(2000L, updated.metaInfo.timestamp)
        assertEquals(50, updated.metaInfo.totalTokensCount)
        assertEquals("test", updated.content)
    }

    // ========== Message.Reasoning 测试 ==========

    @Test
    fun `Reasoning message has correct role and content`() {
        val msg = Message.Reasoning(content = "thinking...")
        assertEquals(MessageRole.Reasoning, msg.role)
        assertEquals("thinking...", msg.content)
        assertNull(msg.id)
        assertNull(msg.encrypted)
    }

    @Test
    fun `Reasoning message with id and encrypted`() {
        val msg = Message.Reasoning(id = "r1", encrypted = "enc_data", content = "thought")
        assertEquals("r1", msg.id)
        assertEquals("enc_data", msg.encrypted)
    }

    @Test
    fun `Reasoning message is Response`() {
        val msg: Message = Message.Reasoning(content = "test")
        assertTrue(msg is Message.Response)
        assertFalse(msg is Message.Request)
    }

    // ========== Message.Tool.Call 测试 ==========

    @Test
    fun `Tool Call has correct role and properties`() {
        val msg = Message.Tool.Call(id = "call_1", tool = "calculator", content = """{"a":1}""")
        assertEquals(MessageRole.Tool, msg.role)
        assertEquals("call_1", msg.id)
        assertEquals("calculator", msg.tool)
        assertEquals("""{"a":1}""", msg.content)
    }

    @Test
    fun `Tool Call is both Tool and Response`() {
        val msg: Message = Message.Tool.Call(id = "c1", tool = "t", content = "{}")
        assertTrue(msg is Message.Tool)
        assertTrue(msg is Message.Response)
        assertFalse(msg is Message.Request)
    }

    @Test
    fun `Tool Call contentJson parses valid JSON`() {
        val msg = Message.Tool.Call(id = "c1", tool = "t", content = """{"key":"value"}""")
        val json = msg.contentJson
        assertEquals("value", (json["key"] as JsonPrimitive).content)
    }

    @Test
    fun `Tool Call contentJsonResult fails for invalid JSON`() {
        val msg = Message.Tool.Call(id = "c1", tool = "t", content = "not json")
        assertTrue(msg.contentJsonResult.isFailure)
    }

    // ========== Message.Tool.Result 测试 ==========

    @Test
    fun `Tool Result has correct role and properties`() {
        val msg = Message.Tool.Result(id = "call_1", tool = "calculator", content = "42")
        assertEquals(MessageRole.Tool, msg.role)
        assertEquals("call_1", msg.id)
        assertEquals("calculator", msg.tool)
        assertEquals("42", msg.content)
    }

    @Test
    fun `Tool Result is both Tool and Request`() {
        val msg: Message = Message.Tool.Result(id = "c1", tool = "t", content = "result")
        assertTrue(msg is Message.Tool)
        assertTrue(msg is Message.Request)
        assertFalse(msg is Message.Response)
    }

    // ========== 类型层次测试 ==========

    @Test
    fun `all Request subtypes are correct`() {
        val requests: List<Message.Request> = listOf(
            Message.User("u"),
            Message.System("s"),
            Message.Tool.Result(id = "id", tool = "t", content = "r")
        )
        assertEquals(3, requests.size)
        requests.forEach { assertTrue(it is Message) }
    }

    @Test
    fun `all Response subtypes are correct`() {
        val responses: List<Message.Response> = listOf(
            Message.Assistant("a"),
            Message.Reasoning(content = "r"),
            Message.Tool.Call(id = "id", tool = "t", content = "c")
        )
        assertEquals(3, responses.size)
        responses.forEach { assertTrue(it is Message) }
    }

    @Test
    fun `LLMChoice is list of Response`() {
        val choice: LLMChoice = listOf(
            Message.Assistant("hello"),
            Message.Tool.Call(id = "c1", tool = "search", content = """{"q":"test"}""")
        )
        assertEquals(2, choice.size)
    }
}
