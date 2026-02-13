package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ChatRequestTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `default stream is false`() {
        val request = ChatRequest(model = "gpt-4", messages = emptyList())
        assertFalse(request.stream)
    }

    @Test
    fun `stream true serializes correctly`() {
        val request = ChatRequest(
            model = "deepseek-chat",
            messages = listOf(ChatMessage.user("hi")),
            stream = true
        )
        val jsonStr = json.encodeToString(ChatRequest.serializer(), request)
        val obj = Json.parseToJsonElement(jsonStr).jsonObject
        assertEquals("true", obj["stream"]?.jsonPrimitive?.content)
    }

    @Test
    fun `max_tokens serialized with snake_case`() {
        val request = ChatRequest(
            model = "test",
            messages = emptyList(),
            maxTokens = 1024
        )
        val jsonStr = json.encodeToString(ChatRequest.serializer(), request)
        assertTrue(jsonStr.contains("\"max_tokens\""))
        assertTrue(jsonStr.contains("1024"))
    }

    @Test
    fun `default temperature is 0_7`() {
        val request = ChatRequest(model = "test", messages = emptyList())
        assertEquals(0.7, request.temperature, 0.001)
    }

    @Test
    fun `messages are serialized in order`() {
        val messages = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("usr"),
            ChatMessage.assistant("ast")
        )
        val request = ChatRequest(model = "m", messages = messages)
        val decoded = json.decodeFromString(
            ChatRequest.serializer(),
            json.encodeToString(ChatRequest.serializer(), request)
        )
        assertEquals(3, decoded.messages.size)
        assertEquals("system", decoded.messages[0].role)
        assertEquals("user", decoded.messages[1].role)
        assertEquals("assistant", decoded.messages[2].role)
    }
}
