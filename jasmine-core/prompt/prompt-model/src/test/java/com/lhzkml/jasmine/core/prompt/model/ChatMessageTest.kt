package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ChatMessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `system factory creates correct role`() {
        val msg = ChatMessage.system("你是助手")
        assertEquals("system", msg.role)
        assertEquals("你是助手", msg.content)
    }

    @Test
    fun `user factory creates correct role`() {
        val msg = ChatMessage.user("你好")
        assertEquals("user", msg.role)
        assertEquals("你好", msg.content)
    }

    @Test
    fun `assistant factory creates correct role`() {
        val msg = ChatMessage.assistant("你好！")
        assertEquals("assistant", msg.role)
        assertEquals("你好！", msg.content)
    }

    @Test
    fun `serialization round trip preserves data`() {
        val original = ChatMessage("user", "hello")
        val jsonStr = json.encodeToString(ChatMessage.serializer(), original)
        val decoded = json.decodeFromString(ChatMessage.serializer(), jsonStr)
        assertEquals(original.role, decoded.role)
        assertEquals(original.content, decoded.content)
    }

    @Test
    fun `deserialization from json string`() {
        val jsonStr = """{"role":"assistant","content":"hi there"}"""
        val msg = json.decodeFromString(ChatMessage.serializer(), jsonStr)
        assertEquals("assistant", msg.role)
        assertEquals("hi there", msg.content)
    }

    @Test
    fun `metadata fields default to null`() {
        val msg = ChatMessage.user("hello")
        assertNull(msg.timestamp)
        assertNull(msg.finishReason)
        assertNull(msg.metadata)
    }

    @Test
    fun `assistant with finishReason`() {
        val msg = ChatMessage.assistant("done", finishReason = "stop")
        assertEquals("assistant", msg.role)
        assertEquals("done", msg.content)
        assertEquals("stop", msg.finishReason)
    }

    @Test
    fun `withTimestamp creates copy with timestamp`() {
        val msg = ChatMessage.user("hello")
        val ts = 1700000000000L
        val withTs = msg.withTimestamp(ts)
        assertEquals(ts, withTs.timestamp)
        assertEquals(msg.role, withTs.role)
        assertEquals(msg.content, withTs.content)
    }

    @Test
    fun `withMetadata creates copy with metadata`() {
        val msg = ChatMessage.assistant("hi")
        val meta = JsonObject(mapOf("source" to JsonPrimitive("test")))
        val withMeta = msg.withMetadata(meta)
        assertEquals(meta, withMeta.metadata)
        assertEquals(msg.content, withMeta.content)
    }

    @Test
    fun `transient fields are not serialized`() {
        val msg = ChatMessage.assistant("hi", finishReason = "stop", timestamp = 123456L)
        val jsonStr = json.encodeToString(ChatMessage.serializer(), msg)
        assertFalse("timestamp should not be in JSON", jsonStr.contains("timestamp"))
        assertFalse("finishReason should not be in JSON", jsonStr.contains("finishReason"))
        assertFalse("metadata should not be in JSON", jsonStr.contains("metadata"))
    }
}
