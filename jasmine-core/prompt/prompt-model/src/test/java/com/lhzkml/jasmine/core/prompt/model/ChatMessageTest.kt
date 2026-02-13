package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
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
        assertEquals(original, decoded)
    }

    @Test
    fun `deserialization from json string`() {
        val jsonStr = """{"role":"assistant","content":"hi there"}"""
        val msg = json.decodeFromString(ChatMessage.serializer(), jsonStr)
        assertEquals("assistant", msg.role)
        assertEquals("hi there", msg.content)
    }
}
