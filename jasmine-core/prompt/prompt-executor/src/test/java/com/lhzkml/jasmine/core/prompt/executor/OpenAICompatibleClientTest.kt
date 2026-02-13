package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OpenAICompatibleClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createMockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    private fun createDeepSeekClient(httpClient: HttpClient): DeepSeekClient {
        return DeepSeekClient(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            httpClient = httpClient
        )
    }

    // ========== chat() 非流式测试 ==========

    @Test
    fun `chat returns assistant content`() = runTest {
        val mockHttp = createMockClient { request ->
            assertEquals("Bearer test-key", request.headers["Authorization"])
            assertTrue(request.url.toString().endsWith("/v1/chat/completions"))

            respond(
                content = """
                {
                    "id": "1",
                    "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": "Hello!"},
                        "finish_reason": "stop"
                    }]
                }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val result = client.chat(listOf(ChatMessage.user("hi")), "deepseek-chat")
        assertEquals("Hello!", result)
        client.close()
    }

    @Test
    fun `chat throws on empty choices`() = runTest {
        val mockHttp = createMockClient {
            respond(
                content = """{"id": "1", "choices": []}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        try {
            client.chat(listOf(ChatMessage.user("hi")), "model")
            fail("Should have thrown")
        } catch (e: ChatClientException) {
            assertTrue(e.message!!.contains("响应中没有有效内容"))
        }
        client.close()
    }

    @Test
    fun `chat sends correct request body`() = runTest {
        val mockHttp = createMockClient { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("\"model\":\"test-model\""))
            assertTrue(body.contains("\"stream\":false"))
            assertTrue(body.contains("\"role\":\"user\""))

            respond(
                content = """
                {
                    "id": "1",
                    "choices": [{"index": 0, "message": {"role": "assistant", "content": "ok"}}]
                }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        client.chat(listOf(ChatMessage.user("hello")), "test-model")
        client.close()
    }

    // ========== chatStream() 流式测试 ==========

    @Test
    fun `chatStream collects chunks`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" World\"}}]}")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockHttp = createMockClient { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("\"stream\":true"))

            respond(
                content = sseBody,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val chunks = client.chatStream(listOf(ChatMessage.user("hi")), "model").toList()
        assertEquals(listOf("Hello", " World"), chunks)
        client.close()
    }

    @Test
    fun `chatStream handles empty delta content`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockHttp = createMockClient {
            respond(
                content = sseBody,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val chunks = client.chatStream(listOf(ChatMessage.user("hi")), "model").toList()
        // Empty content and role-only delta should be skipped
        assertEquals(listOf("Hi"), chunks)
        client.close()
    }

    @Test
    fun `chatStream throws on non-success status`() = runTest {
        val mockHttp = createMockClient {
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        try {
            client.chatStream(listOf(ChatMessage.user("hi")), "model").toList()
            fail("Should have thrown")
        } catch (e: ChatClientException) {
            assertTrue(e.message!!.contains("401"))
        }
        client.close()
    }

    // ========== listModels() 测试 ==========

    @Test
    fun `listModels returns model list`() = runTest {
        val mockHttp = createMockClient { request ->
            assertTrue(request.url.toString().endsWith("/v1/models"))

            respond(
                content = """
                {
                    "object": "list",
                    "data": [
                        {"id": "deepseek-chat", "object": "model", "owned_by": "deepseek"},
                        {"id": "deepseek-coder", "object": "model", "owned_by": "deepseek"}
                    ]
                }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val models = client.listModels()
        assertEquals(2, models.size)
        assertEquals("deepseek-chat", models[0].id)
        assertEquals("deepseek-coder", models[1].id)
        client.close()
    }

    @Test
    fun `listModels returns empty list`() = runTest {
        val mockHttp = createMockClient {
            respond(
                content = """{"object": "list", "data": []}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val models = client.listModels()
        assertTrue(models.isEmpty())
        client.close()
    }
}
