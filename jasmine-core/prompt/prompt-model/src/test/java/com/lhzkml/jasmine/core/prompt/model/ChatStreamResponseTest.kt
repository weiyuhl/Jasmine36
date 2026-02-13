package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatStreamResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserialize stream chunk with content`() {
        val jsonStr = """
        {
            "id": "chatcmpl-1",
            "choices": [{
                "index": 0,
                "delta": {"content": "Hello"},
                "finish_reason": null
            }]
        }
        """.trimIndent()

        val chunk = json.decodeFromString(ChatStreamResponse.serializer(), jsonStr)
        assertEquals("chatcmpl-1", chunk.id)
        assertEquals("Hello", chunk.choices[0].delta.content)
        assertNull(chunk.choices[0].finishReason)
    }

    @Test
    fun `deserialize stream chunk with role only`() {
        val jsonStr = """
        {
            "id": "chatcmpl-1",
            "choices": [{
                "index": 0,
                "delta": {"role": "assistant"},
                "finish_reason": null
            }]
        }
        """.trimIndent()

        val chunk = json.decodeFromString(ChatStreamResponse.serializer(), jsonStr)
        assertEquals("assistant", chunk.choices[0].delta.role)
        assertNull(chunk.choices[0].delta.content)
    }

    @Test
    fun `deserialize stream chunk with empty delta`() {
        val jsonStr = """
        {
            "id": "chatcmpl-1",
            "choices": [{
                "index": 0,
                "delta": {},
                "finish_reason": "stop"
            }]
        }
        """.trimIndent()

        val chunk = json.decodeFromString(ChatStreamResponse.serializer(), jsonStr)
        assertNull(chunk.choices[0].delta.content)
        assertNull(chunk.choices[0].delta.role)
        assertEquals("stop", chunk.choices[0].finishReason)
    }

    @Test
    fun `deserialize with unknown fields`() {
        val jsonStr = """
        {
            "id": "x",
            "object": "chat.completion.chunk",
            "created": 123,
            "model": "deepseek-chat",
            "choices": [{
                "index": 0,
                "delta": {"content": "Hi"}
            }]
        }
        """.trimIndent()

        val chunk = json.decodeFromString(ChatStreamResponse.serializer(), jsonStr)
        assertEquals("Hi", chunk.choices[0].delta.content)
    }
}
