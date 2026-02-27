package com.lhzkml.jasmine.core.config

/**
 * MCP 传输类型
 */
enum class McpTransportType {
    STREAMABLE_HTTP,
    SSE
}

/**
 * MCP 服务器配置
 * @param name 显示名称
 * @param url 服务器 URL
 * @param transportType 传输类型（Streamable HTTP 或 SSE）
 * @param headerName 请求头名称（如 Authorization）
 * @param headerValue 请求头值（如 Bearer xxx）
 * @param enabled 是否启用
 */
data class McpServerConfig(
    val name: String,
    val url: String,
    val transportType: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val headerName: String = "",
    val headerValue: String = "",
    val enabled: Boolean = true
)
