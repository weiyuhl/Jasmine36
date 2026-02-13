package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserialize typical response`() {
        val jsonStr = """
        {
            "id": "chatcmpl-123",
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": "Hello!"},
                "finish_reason": "stop"
            }],
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 5,
                "total_tokens": 15
            }
        }
        """.trimIndent()

        val response = json.decodeFromString(ChatResponse.serializer(), jsonStr)
        assertEquals("chatcmpl-123", response.id)
        assertEquals(1, response.choices.size)
        assertEquals("Hello!", response.choices[0].message.content)
        assertEquals("stop", response.choices[0].finishReason)
        assertEquals(10, response.usage?.promptTokens)
        assertEquals(5, response.usage?.completionTokens)
        assertEquals(15, response.usage?.totalTokens)
    }

    @Test
    fun `deserialize response with unknown fields`() {
        val jsonStr = """
        {
            "id": "x",
            "object": "chat.completion",
            "created": 1234567890,
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": "ok"}
            }]
        }
        """.trimIndent()

        val response = json.decodeFromString(ChatResponse.serializer(), jsonStr)
        assertEquals("ok", response.choices[0].message.content)
        assertNull(response.usage)
    }

    @Test
    fun `empty choices list`() {
        val jsonStr = """{"id": "x", "choices": []}"""
        val response = json.decodeFromString(ChatResponse.serializer(), jsonStr)
        assertTrue(response.choices.isEmpty())
    }
}
