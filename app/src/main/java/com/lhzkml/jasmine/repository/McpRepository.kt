package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.agent.mcp.McpToolDefinition
import com.lhzkml.jasmine.core.agent.runtime.McpConnectionManager
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.McpServerConfig

/**
 * MCP Repository
 *
 * 负责：
 * - MCP 启用开关
 * - MCP Server 列表增删改
 * - 连接状态查询
 * - reconnect
 * - connectSingleServerByName
 * - clearServerCache
 *
 * 对应页面：
 * - McpServerActivity
 * - McpServerEditActivity
 * - SettingsActivity 中 MCP 摘要
 * - ChatViewModel 中预连接与工具装载
 *
 * 说明：
 * - McpConnectionManager 继续保留为底层 service
 * - Repository 负责把"配置 + 状态 + service 调用"整成 ViewModel 可用的数据口
 */
interface McpRepository {
    
    // MCP 开关
    fun isMcpEnabled(): Boolean
    fun setMcpEnabled(enabled: Boolean)
    
    // Server 列表管理
    fun getMcpServers(): List<McpServerConfig>
    fun setMcpServers(servers: List<McpServerConfig>)
    
    // 连接管理
    suspend fun reconnect(onComplete: () -> Unit = {})
    suspend fun connectSingleServerByName(serverName: String)
    fun clearServerCache(serverName: String)
    
    // 连接状态查询
    data class CachedConnection(
        val success: Boolean,
        val tools: List<McpToolDefinition>,
        val error: String?
    )
    
    fun getConnectionCache(): Map<String, CachedConnection>
    
    // 获取底层 ConnectionManager（用于 ChatViewModel 等需要直接访问的场景）
    fun getConnectionManager(): McpConnectionManager
}

class DefaultMcpRepository(
    private val configRepo: ConfigRepository,
    private val mcpConnectionManager: McpConnectionManager
) : McpRepository {
    
    override fun isMcpEnabled(): Boolean = configRepo.isMcpEnabled()
    
    override fun setMcpEnabled(enabled: Boolean) {
        configRepo.setMcpEnabled(enabled)
    }
    
    override fun getMcpServers(): List<McpServerConfig> = configRepo.getMcpServers()
    
    override fun setMcpServers(servers: List<McpServerConfig>) {
        configRepo.setMcpServers(servers)
    }
    
    override suspend fun reconnect(onComplete: () -> Unit) {
        mcpConnectionManager.reconnect(onComplete)
    }
    
    override suspend fun connectSingleServerByName(serverName: String) {
        mcpConnectionManager.connectSingleServerByName(serverName)
    }
    
    override fun clearServerCache(serverName: String) {
        mcpConnectionManager.clearServerCache(serverName)
    }
    
    override fun getConnectionCache(): Map<String, McpRepository.CachedConnection> {
        val cache = mcpConnectionManager.getConnectionCache()
        return cache.mapValues { (_, value) ->
            McpRepository.CachedConnection(
                success = value.success ?: false,
                tools = value.tools,
                error = value.error
            )
        }
    }
    
    override fun getConnectionManager(): McpConnectionManager = mcpConnectionManager
}
