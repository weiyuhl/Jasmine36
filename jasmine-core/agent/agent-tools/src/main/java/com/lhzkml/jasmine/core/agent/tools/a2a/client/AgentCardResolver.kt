package com.lhzkml.jasmine.core.agent.tools.a2a.client

import com.lhzkml.jasmine.core.agent.tools.a2a.model.AgentCard
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest

/**
 * Agent 名片解析器接口
 * 完整移植 koog 的 AgentCardResolver
 */
interface AgentCardResolver {
    suspend fun resolve(): AgentCard
}

/**
 * 显式 AgentCard 解析器 — 直接返回提供的 AgentCard
 * 参考 koog 的 ExplicitAgentCardResolver
 */
class ExplicitAgentCardResolver(val agentCard: AgentCard) : AgentCardResolver {
    override suspend fun resolve(): AgentCard = agentCard
}

/**
 * URL AgentCard 解析器 — 从 URL 获取 AgentCard
 * 参考 koog 的 UrlAgentCardResolver，使用 OkHttp 替代 Ktor
 *
 * @param baseUrl Agent 服务器基础 URL
 * @param path AgentCard 路径（默认 /.well-known/agent.json）
 * @param httpClient OkHttp 客户端
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
