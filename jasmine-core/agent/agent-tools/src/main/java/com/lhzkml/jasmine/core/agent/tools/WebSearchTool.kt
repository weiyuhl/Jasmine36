package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 网络搜索工具集
 * 直接参考 koog 的 WebSearchTools 实现
 * 使用 BrightData SERP API 进行 Google 搜索和网页抓取
 */
class WebSearchTool(
    private val brightDataKey: String,
    private val serpZone: String = "serp_api1",
    private val unlockerZone: String = "web_unlocker1"
) : AutoCloseable {

    @OptIn(ExperimentalSerializationApi::class)
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Serializable
    data class WebSearchResult(val organic: List<OrganicResult> = emptyList()) {
        @Serializable
        data class OrganicResult(
            val link: String = "", val title: String = "", val description: String = "",
            val rank: Int = 0, val globalRank: Int = 0
        )
    }

    @Serializable
    data class WebPageScrapingResult(val body: String = "")

    @Serializable
    data class BrightDataRequest(val zone: String, val url: String, val format: String, val dataFormat: String? = null)

    private val httpClient = HttpClient {
        defaultRequest {
            url("https://api.brightdata.com/request")
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $brightDataKey")
        }
        install(ContentNegotiation) { json(jsonParser) }
    }

    val search = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "web_search",
            description = "Search for a query on Google. Returns a list of search results with title, link, and description.",
            requiredParameters = listOf(
                ToolParameterDescriptor("query", "The search query", ToolParameterType.StringType)
            )
        )
        override suspend fun execute(arguments: String): String {
            val obj = Json.parseToJsonElement(arguments).jsonObject
            val query = obj["query"]?.jsonPrimitive?.content ?: return "Error: Missing parameter 'query'"
            val searchUrl = URLBuilder("https://www.google.com/search").apply {
                parameters.append("brd_json", "1"); parameters.append("q", query)
            }.buildString()
            val request = BrightDataRequest(zone = serpZone, url = searchUrl, format = "raw")
            return try {
                val response = httpClient.post { setBody(request) }
                val result = response.body<WebSearchResult>()
                result.organic.joinToString("\n\n") { "[${it.rank}] ${it.title}\n${it.link}\n${it.description}" }
                    .ifEmpty { "No results found." }
            } catch (e: Exception) { "Error: ${e.message}" }
        }
    }

    val scrape = object : Tool() {
        override val descriptor = ToolDescriptor(
            name = "web_scrape",
            description = "Scrape a web page and return its content as markdown text.",
            requiredParameters = listOf(
                ToolParameterDescriptor("url", "The URL to scrape", ToolParameterType.StringType)
            )
        )
        override suspend fun execute(arguments: String): String {
            val obj = Json.parseToJsonElement(arguments).jsonObject
            val url = obj["url"]?.jsonPrimitive?.content ?: return "Error: Missing parameter 'url'"
            val request = BrightDataRequest(zone = unlockerZone, url = url, format = "json", dataFormat = "markdown")
            return try {
                val response = httpClient.post { setBody(request) }
                response.body<WebPageScrapingResult>().body.ifEmpty { "No content found." }
            } catch (e: Exception) { "Error: ${e.message}" }
        }
    }

    fun allTools(): List<Tool> = listOf(search, scrape)

    override fun close() { httpClient.close() }
}
