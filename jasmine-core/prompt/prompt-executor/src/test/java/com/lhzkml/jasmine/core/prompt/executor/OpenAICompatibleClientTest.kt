package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
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

    // ========== chat() 测试 ==========

    @Test
    fun `chat returns assistant content`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello!\"}}]}")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockHttp = createMockClient { request ->
            assertEquals("Bearer test-key", request.headers["Authorization"])
            assertTrue(request.url.toString().endsWith("/v1/chat/completions"))

            respond(
                content = sseBody,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val result = client.chat(listOf(ChatMessage.user("hi")), "deepseek-chat")
        assertEquals("Hello!", result)
        client.close()
    }

    @Test
    fun `chat throws on empty stream`() = runTest {
        val sseBody = buildString {
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
        val result = client.chat(listOf(ChatMessage.user("hi")), "model")
        assertEquals("", result)
        client.close()
    }

    @Test
    fun `chat sends correct request body`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"}}]}")
            appendLine()
            appendLine("data: [DONE]")
            appendLine()
        }

        val mockHttp = createMockClient { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("\"model\":\"test-model\""))
            assertTrue(body.contains("\"stream\":true"))
            assertTrue(body.contains("\"role\":\"user\""))

            respond(
                content = sseBody,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
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
            assertEquals(ErrorType.AUTHENTICATION, e.errorType)
            assertEquals(401, e.statusCode)
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

    @Test
    fun `listModels extracts metadata fields`() = runTest {
        val mockHttp = createMockClient {
            respond(
                content = """
                {
                    "object": "list",
                    "data": [
                        {
                            "id": "deepseek-ai/DeepSeek-V3",
                            "object": "model",
                            "owned_by": "deepseek",
                            "context_length": 65536,
                            "max_tokens": 8192,
                            "description": "DeepSeek V3 model"
                        },
                        {
                            "id": "Qwen/Qwen2.5-72B",
                            "object": "model",
                            "owned_by": "qwen",
                            "context_length": 32768
                        }
                    ]
                }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = createDeepSeekClient(mockHttp)
        val models = client.listModels()
        assertEquals(2, models.size)

        val v3 = models[0]
        assertEquals("deepseek-ai/DeepSeek-V3", v3.id)
        assertEquals(65536, v3.contextLength)
        assertEquals(8192, v3.maxOutputTokens)
        assertEquals("DeepSeek V3 model", v3.description)
        assertTrue(v3.hasMetadata)

        val qwen = models[1]
        assertEquals("Qwen/Qwen2.5-72B", qwen.id)
        assertEquals(32768, qwen.contextLength)
        assertNull(qwen.maxOutputTokens)
        assertTrue(qwen.hasMetadata)

        client.close()
    }

    // ========== chatWithUsage() 测试 ==========

    @Test
    fun `chatWithUsage returns content and usage`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi!\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}")
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
        val result = client.chatWithUsage(listOf(ChatMessage.user("hi")), "model")
        assertEquals("Hi!", result.content)
        assertNotNull(result.usage)
        assertEquals(10, result.usage!!.promptTokens)
        assertEquals(5, result.usage!!.completionTokens)
        assertEquals(15, result.usage!!.totalTokens)
        client.close()
    }

    @Test
    fun `chatWithUsage handles null usage`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"}}]}")
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
        val result = client.chatWithUsage(listOf(ChatMessage.user("hi")), "model")
        assertEquals("ok", result.content)
        assertNull(result.usage)
        client.close()
    }

    // ========== chatStreamWithUsage() 测试 ==========

    @Test
    fun `chatStreamWithUsage returns content and usage`() = runTest {
        val sseBody = buildString {
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" World\"}}]}")
            appendLine()
            appendLine("data: {\"id\":\"1\",\"choices\":[],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":4,\"total_tokens\":12}}")
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
        val chunks = mutableListOf<String>()
        val result = client.chatStreamWithUsage(listOf(ChatMessage.user("hi")), "model") { chunk ->
            chunks.add(chunk)
        }

        assertEquals(listOf("Hello", " World"), chunks)
        assertEquals("Hello World", result.content)
        assertNotNull(result.usage)
        assertEquals(8, result.usage!!.promptTokens)
        assertEquals(4, result.usage!!.completionTokens)
        assertEquals(12, result.usage!!.totalTokens)
        client.close()
    }

    @Test
    fun `chatStreamWithUsage handles no usage in stream`() = runTest {
        val sseBody = buildString {
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
        val result = client.chatStreamWithUsage(listOf(ChatMessage.user("hi")), "model") {}
        assertEquals("Hi", result.content)
        assertNull(result.usage)
        client.close()
    }
}
