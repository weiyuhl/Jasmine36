package com.lhzkml.jasmine.core.agent.runtime

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.mcp.HttpMcpClient
import com.lhzkml.jasmine.core.agent.mcp.McpClient
import com.lhzkml.jasmine.core.agent.mcp.McpToolAdapter
import com.lhzkml.jasmine.core.agent.mcp.McpToolDefinition
import com.lhzkml.jasmine.core.agent.mcp.McpToolRegistryProvider
import com.lhzkml.jasmine.core.agent.mcp.SseMcpClient
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.McpServerConfig
import com.lhzkml.jasmine.core.config.McpTransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MCP 连接管理器
 *
 * 管理 MCP 客户端的生命周期、连接状态和工具加载。
 * 将 MainActivity 中的 MCP 相关逻辑迁移到 core 层。
 *
 * 线程安全：使用 Mutex 保护共享状态。
 */
class McpConnectionManager(private val configRepo: ConfigRepository) {

    /**
     * MCP 服务器连接状态
     */
    data class McpServerStatus(
        val success: Boolean,
        val tools: List<McpToolDefinition> = emptyList(),
        val error: String? = null
    )

    /**
     * MCP 连接事件监听器
     * app 层可实现此接口来显示 Toast 等 UI 反馈
     */
    interface ConnectionListener {
        /** 服务器连接成功 */
        fun onConnected(serverName: String, transportType: McpTransportType, toolCount: Int)
        /** 服务器连接失败 */
        fun onConnectionFailed(serverName: String, error: String)
    }

    private val mutex = Mutex()
    private val clients = mutableListOf<McpClient>()
    private val preloadedTools = mutableListOf<McpToolAdapter>()
    private val connectionCache = mutableMapOf<String, McpServerStatus>()
    @Volatile private var preloaded = false
    @Volatile private var connecting = false

    var listener: ConnectionListener? = null

    /** 获取连接状态缓存（只读副本） */
    fun getConnectionCache(): Map<String, McpServerStatus> = connectionCache.toMap()

    /** 获取指定服务器的连接状态 */
    fun getServerStatus(serverName: String): McpServerStatus? = connectionCache[serverName]

    /** 是否已完成预加载 */
    val isPreloaded: Boolean get() = preloaded

    /**
     * 后台预连接所有启用的 MCP 服务器
     * 连接成功后缓存客户端和工具，发消息时直接复用。
     * 如果已经连接过或正在连接中，直接跳过。
     */
    suspend fun preconnect() = withContext(Dispatchers.IO) {
        // 已连接或正在连接中，跳过
        if (preloaded || connecting) return@withContext
        if (!configRepo.isMcpEnabled()) return@withContext

        connecting = true
        try {
            val servers = configRepo.getMcpServers().filter { it.enabled && it.url.isNotBlank() }
            if (servers.isEmpty()) {
                preloaded = true
                return@withContext
            }

            for (server in servers) {
                // 跳过已有缓存的服务器（避免重复连接）
                if (connectionCache.containsKey(server.name)) continue
                connectSingleServer(server)
            }
            preloaded = true
        } finally {
            connecting = false
        }
    }

    /**
     * 连接单个 MCP 服务器，结果写入 connectionCache 和 preloadedTools
     */
    private suspend fun connectSingleServer(server: McpServerConfig) {
        try {
            val headers = buildHeaders(server)
            val client = createClient(server.transportType, server.url, headers)
            client.connect()

            mutex.withLock {
                clients.add(client)
            }

            val mcpRegistry = McpToolRegistryProvider.fromClient(client)
            val tools = mutableListOf<McpToolAdapter>()
            for (descriptor in mcpRegistry.descriptors()) {
                val mcpTool = mcpRegistry.findTool(descriptor.name) ?: continue
                tools.add(McpToolAdapter(mcpTool))
            }

            mutex.withLock {
                preloadedTools.addAll(tools)
            }

            val toolDefs = client.listTools()
            connectionCache[server.name] = McpServerStatus(
                success = true,
                tools = toolDefs
            )

            listener?.onConnected(server.name, server.transportType, mcpRegistry.size)
        } catch (e: Exception) {
            connectionCache[server.name] = McpServerStatus(
                success = false,
                error = e.message ?: "未知错误"
            )
            listener?.onConnectionFailed(server.name, e.message ?: "未知错误")
        }
    }

    /**
     * 加载 MCP 工具到注册表
     * 优先复用预连接的工具，避免重复连接。
     */
    suspend fun loadToolsInto(registry: ToolRegistry) {
        if (!configRepo.isMcpEnabled()) return

        // 如果已经预加载了，直接复用
        if (preloaded && preloadedTools.isNotEmpty()) {
            mutex.withLock {
                for (tool in preloadedTools) {
                    registry.register(tool)
                }
            }
            return
        }

        // 正在连接中，等待完成（最多 30 秒）
        if (connecting) {
            var waited = 0
            while (connecting && waited < 30000) {
                kotlinx.coroutines.delay(200)
                waited += 200
            }
            if (preloadedTools.isNotEmpty()) {
                mutex.withLock {
                    for (tool in preloadedTools) {
                        registry.register(tool)
                    }
                }
                return
            }
        }

        // 预连接未启动过，执行一次连接
        if (!preloaded && !connecting) {
            preconnect()
            if (preloadedTools.isNotEmpty()) {
                mutex.withLock {
                    for (tool in preloadedTools) {
                        registry.register(tool)
                    }
                }
            }
        }
    }

    /**
     * 强制重新连接所有 MCP 服务器
     * 仅在用户修改了 MCP 配置后调用。
     */
    suspend fun reconnect() = withContext(Dispatchers.IO) {
        close()
        preconnect()
    }

    /**
     * 按名称连接单个 MCP 服务器（供 McpServerActivity 测试按钮使用）
     * 先清除该服务器的旧缓存，再重新连接。
     */
    suspend fun connectSingleServerByName(serverName: String) = withContext(Dispatchers.IO) {
        val servers = configRepo.getMcpServers().filter { it.name == serverName && it.enabled && it.url.isNotBlank() }
        val server = servers.firstOrNull() ?: return@withContext
        // 清除旧缓存
        connectionCache.remove(serverName)
        connectSingleServer(server)
    }

    /**
     * 关闭所有 MCP 客户端连接
     */
    fun close() {
        clients.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        preloadedTools.clear()
        connectionCache.clear()
        preloaded = false
        connecting = false
    }

    private fun createClient(
        transportType: McpTransportType,
        url: String,
        headers: Map<String, String>
    ): McpClient = when (transportType) {
        McpTransportType.SSE -> SseMcpClient(url, customHeaders = headers)
        McpTransportType.STREAMABLE_HTTP -> HttpMcpClient(url, headers)
    }

    private fun buildHeaders(server: McpServerConfig): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (server.headerName.isNotBlank() && server.headerValue.isNotBlank()) {
            headers[server.headerName] = server.headerValue
        }
        return headers
    }
}
