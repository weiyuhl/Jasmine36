package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.StringType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 网页抓取工具集
 * 移植自 AetherLink @aether/fetch
 * 直接 HTTP GET 请求，返回 HTML / 纯文本 / JSON 三种格式
 * 不依赖 BrightData，适用于无反爬保护的 URL
 */
class FetchUrlTool : AutoCloseable {

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    private val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * 通用 fetch，解析 url 和 headers 参数
     */
    private suspend fun fetch(arguments: String): Pair<String, String> {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val url = obj["url"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing parameter 'url'")
        val headers = obj["headers"]?.jsonObject

        val response = httpClient.get(url) {
            headers?.forEach { (key, value) ->
                header(key, value.jsonPrimitive.content)
            }
        }

        val status = response.status.value
        if (status !in 200..299) {
            throw RuntimeException("HTTP $status ${response.status.description}")
        }

        return url to response.bodyAsText()
    }

    val fetchHtml = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "fetch_url_as_html",
            description = "Fetches a URL and returns the raw HTML content.",
            requiredParameters = listOf(
                ToolParameterDescriptor("url", "URL to fetch", StringType)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("headers", "Optional HTTP request headers (JSON object)", StringType)
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val (_, body) = fetch(arguments)
                body
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    val fetchText = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "fetch_url_as_text",
            description = "Fetches a URL and returns the content as plain text.",
            requiredParameters = listOf(
                ToolParameterDescriptor("url", "URL to fetch", StringType)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("headers", "Optional HTTP request headers (JSON object)", StringType)
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val (_, body) = fetch(arguments)
                // 简单去除 HTML 标签
                body.replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    val fetchJson = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "fetch_url_as_json",
            description = "Fetches a URL and returns the content parsed as formatted JSON.",
            requiredParameters = listOf(
                ToolParameterDescriptor("url", "URL to fetch", StringType)
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("headers", "Optional HTTP request headers (JSON object)", StringType)
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val (url, body) = fetch(arguments)
                val parsed = jsonParser.parseToJsonElement(body)
                jsonParser.encodeToString(JsonElement.serializer(), parsed)
            } catch (e: kotlinx.serialization.SerializationException) {
                "Error: Response is not valid JSON"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    fun allTools(): List<Tool> = listOf(fetchHtml, fetchText, fetchJson)

    override fun close() {
        httpClient.close()
    }
}
