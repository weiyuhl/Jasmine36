package com.lhzkml.jasmine.core.prompt.executor

import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ClientProviderTest {

    private val dummyHttp = HttpClient(MockEngine { request ->
        respond(
            content = """{"id":"1","choices":[{"index":0,"message":{"role":"assistant","content":"ok"}}]}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    @Test
    fun `DeepSeekClient has correct provider`() {
        val client = DeepSeekClient("key", httpClient = dummyHttp)
        assertEquals(LLMProvider.DeepSeek, client.provider)
        assertEquals("DeepSeek", client.provider.name)
    }

    @Test
    fun `SiliconFlowClient has correct provider`() {
        val client = SiliconFlowClient("key", httpClient = dummyHttp)
        assertEquals(LLMProvider.SiliconFlow, client.provider)
        assertEquals("SiliconFlow", client.provider.name)
    }

    @Test
    fun `DeepSeekClient default base url`() {
        assertEquals("https://api.deepseek.com", DeepSeekClient.DEFAULT_BASE_URL)
    }

    @Test
    fun `SiliconFlowClient default base url`() {
        assertEquals("https://api.siliconflow.cn", SiliconFlowClient.DEFAULT_BASE_URL)
    }
}
