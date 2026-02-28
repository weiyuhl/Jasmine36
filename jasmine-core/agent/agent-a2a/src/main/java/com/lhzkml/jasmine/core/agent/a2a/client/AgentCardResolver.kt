package com.lhzkml.jasmine.core.agent.a2a.client

import com.lhzkml.jasmine.core.agent.a2a.model.AgentCard
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest

/**
 * Agent 名片解析器接�?
 * 完整移植 koog �?AgentCardResolver
 */
interface AgentCardResolver {
    suspend fun resolve(): AgentCard
}

/**
 * 显式 AgentCard 解析�?�?直接返回提供�?AgentCard
 * 参�?koog �?ExplicitAgentCardResolver
 */
class ExplicitAgentCardResolver(val agentCard: AgentCard) : AgentCardResolver {
    override suspend fun resolve(): AgentCard = agentCard
}

/**
 * URL AgentCard 解析�?�?�?URL 获取 AgentCard
 * 参�?koog �?UrlAgentCardResolver，使�?OkHttp 替代 Ktor
 *
 * @param baseUrl Agent 服务器基础 URL
 * @param path AgentCard 路径（默�?/.well-known/agent.json�?
 * @param httpClient OkHttp 客户�?
 */
class UrlAgentCardResolver(
    val baseUrl: String,
    val path: String = AGENT_CARD_WELL_KNOWN_PATH,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AgentCardResolver {

    override suspend fun resolve(): AgentCard {
        val url = "${baseUrl.trimEnd('/')}$path"
        val request = OkRequest.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to fetch AgentCard from $url: ${response.code}")
        }

        val body = response.body?.string()
            ?: throw IllegalStateException("Empty response body from $url")

        return json.decodeFromString<AgentCard>(body)
    }

    companion object {
        const val AGENT_CARD_WELL_KNOWN_PATH = "/.well-known/agent.json"
    }
}
