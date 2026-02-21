package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ChatRequestTest {

    private val json = Json { encodeDefaults = true }

    private fun msg(role: String, content: String) = OpenAIRequestMessage(role = role, content = content)

    @Test
    fun `default stream is false`() {
        val request = ChatRequest(model = "gpt-4", messages = emptyList())
        assertFalse(request.stream)
    }

    @Test
    fun `stream true serializes correctly`() {
        val request = ChatRequest(
            model = "deepseek-chat",
            messages = listOf(msg("user", "hi")),
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
    fun `default temperature is null`() {
        val request = ChatRequest(model = "test", messages = emptyList())
        assertNull(request.temperature)
    }

    @Test
    fun `tools are serialized when present`() {
        val params = kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("object"))
            put("properties", kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("string"))
                    put("description", kotlinx.serialization.json.JsonPrimitive("File path"))
                })
            })
        }
        val toolDef = OpenAIToolDef(
            function = OpenAIFunctionDef(
                name = "read_file",
                description = "Read a file",
                parameters = params
            )
        )
        val request = ChatRequest(
            model = "test",
            messages = listOf(msg("user", "hi")),
            stream = true,
            tools = listOf(toolDef)
        )
        val jsonStr = json.encodeToString(ChatRequest.serializer(), request)
        assertTrue("tools field should be present", jsonStr.contains("\"tools\""))
        assertTrue("function name should be present", jsonStr.contains("\"read_file\""))
        assertTrue("function type should be present", jsonStr.contains("\"function\""))

        // Verify tools is an array, not null
        val obj = Json.parseToJsonElement(jsonStr).jsonObject
        val toolsElement = obj["tools"]
        assertNotNull("tools should not be null", toolsElement)
        assertTrue("tools should be an array", toolsElement is kotlinx.serialization.json.JsonArray)
        assertEquals(1, (toolsElement as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun `tools null when not provided`() {
        val request = ChatRequest(model = "test", messages = emptyList())
        val jsonStr = json.encodeToString(ChatRequest.serializer(), request)
        val obj = Json.parseToJsonElement(jsonStr).jsonObject
        val toolsElement = obj["tools"]
        assertTrue("tools should be null", toolsElement is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `messages are serialized in order`() {
        val messages = listOf(
            msg("system", "sys"),
            msg("user", "usr"),
            msg("assistant", "ast")
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
