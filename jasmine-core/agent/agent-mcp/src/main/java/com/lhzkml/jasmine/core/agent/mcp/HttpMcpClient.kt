package com.lhzkml.jasmine.core.agent.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger

/**
 * 基于 HTTP JSON-RPC �?MCP 客户�?
 *
 * 通过 HTTP POST 发�?JSON-RPC 2.0 请求�?MCP 服务器通信�?
 * 支持 Streamable HTTP transport（MCP 2025-03-26 规范）�?
 *
 * @param serverUrl MCP 服务�?URL（如 http://localhost:8080/mcp�?
 * @param customHeaders 自定义请求头（如认证 token�?
 */
class HttpMcpClient(
    private val serverUrl: String,
    private val customHeaders: Map<String, String> = emptyMap()
) : McpClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@HttpMcpClient.json)
        }
    }

    private val requestId = AtomicInteger(0)
    private var sessionId: String? = null

    override suspend fun connect() {
        val result = rpcCall("initialize", buildJsonObject {
            put("protocolVersion", "2025-03-26")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME)
                put("version", McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION)
            })
        })

        rpcNotify("notifications/initialized")
    }

    override suspend fun listTools(): List<McpToolDefinition> {
        val result = rpcCall("tools/list", buildJsonObject {})
        val toolsArray = result?.get("tools")?.jsonArray ?: return emptyList()

        return toolsArray.map { element ->
            val obj = element.jsonObject
            val inputSchemaJson = obj["inputSchema"]?.jsonObject
            McpToolDefinition(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                inputSchema = inputSchemaJson?.let { parseInputSchema(it) }
            )
        }
    }

    override suspend fun callTool(name: String, arguments: String): McpToolResult {
        val argsJson = try {
            json.parseToJsonElement(arguments).jsonObject
        } catch (_: Exception) {
            buildJsonObject { put("input", arguments) }
        }

        val result = rpcCall("tools/call", buildJsonObject {
            put("name", name)
            put("arguments", argsJson)
        })

        if (result == null) {
            return McpToolResult(content = "Error: No response from MCP server", isError = true)
        }

        val isError = result["isError"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val contentArray = result["content"]?.jsonArray
        val content = contentArray?.joinToString("\n") { element ->
            val obj = element.jsonObject
            obj["text"]?.jsonPrimitive?.contentOrNull ?: obj.toString()
        } ?: result.toString()

        return McpToolResult(content = content, isError = isError)
    }

    override fun close() {
        httpClient.close()
    }

    // ========== JSON-RPC 通信 ==========

    @Serializable
    private data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val id: Int? = null,
        val method: String,
        val params: JsonElement? = null
    )

    /**
     * JSON-RPC 响应 �?id 使用 JsonElement 以兼�?int �?string 类型
     */
    @Serializable
    private data class JsonRpcResponse(
        val jsonrpc: String = "2.0",
        val id: JsonElement? = null,
        val result: JsonElement? = null,
        val error: JsonRpcError? = null
    )

    @Serializable
    private data class JsonRpcError(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    )

    private suspend fun rpcCall(method: String, params: JsonObject): JsonObject? {
        val id = requestId.incrementAndGet()
        val request = JsonRpcRequest(id = id, method = method, params = params)

        val response = httpClient.post(serverUrl) {
            contentType(ContentType.Application.Json)
            // MCP 规范要求客户端同时接�?JSON �?SSE
            headers.append("Accept", "application/json, text/event-stream")
            for ((k, v) in customHeaders) { headers.append(k, v) }
            sessionId?.let { headers.append("Mcp-Session-Id", it) }
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }

        // 保存 session ID
        response.headers["Mcp-Session-Id"]?.let { sessionId = it }

        val body = response.body<String>()
        val jsonBody = extractJsonFromResponse(body)
        val rpcResponse = json.decodeFromString(JsonRpcResponse.serializer(), jsonBody)

        if (rpcResponse.error != null) {
            throw McpException(
                "MCP error ${rpcResponse.error.code}: ${rpcResponse.error.message}"
            )
        }

        return rpcResponse.result?.jsonObject
    }

    private suspend fun rpcNotify(method: String, params: JsonObject? = null) {
        val request = JsonRpcRequest(method = method, params = params)
        httpClient.post(serverUrl) {
            contentType(ContentType.Application.Json)
            headers.append("Accept", "application/json, text/event-stream")
            for ((k, v) in customHeaders) { headers.append(k, v) }
            sessionId?.let { headers.append("Mcp-Session-Id", it) }
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }
    }

    /**
     * 从响应体中提�?JSON �?兼容�?JSON �?SSE 格式
     *
     * 有些 MCP 服务器即使客户端 Accept �?application/json�?
     * 仍然返回 SSE 格式（event: message\ndata: {...}）�?
     * 此方法从 SSE 事件流中提取最后一�?JSON-RPC 响应�?
     */
    private fun extractJsonFromResponse(body: String): String {
        val trimmed = body.trim()

        // 如果�?{ 开头，就是�?JSON
        if (trimmed.startsWith("{")) return trimmed

        // SSE 格式：提取所�?"data:" 行，找到包含 JSON-RPC 响应的那�?
        // SSE 格式示例�?
        //   event: message
        //   data: {"jsonrpc":"2.0","id":1,"result":{...}}
        //
        //   event: message
        //   data: {"jsonrpc":"2.0","method":"notifications/..."}
        val dataLines = trimmed.lines()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.startsWith("{") }

        // 优先找包�?"result" �?"error" 的响应（�?JSON-RPC response，而非 notification�?
        val rpcResponse = dataLines.lastOrNull { line ->
            line.contains("\"result\"") || line.contains("\"error\"")
        }
        if (rpcResponse != null) return rpcResponse

        // 退而求其次，返回最后一�?data �?
        return dataLines.lastOrNull()
            ?: throw McpException("No JSON-RPC response found in SSE stream: ${trimmed.take(200)}")
    }

    // ========== Schema 解析 ==========

    private fun parseInputSchema(obj: JsonObject): McpInputSchema {
        val properties = obj["properties"]?.jsonObject?.mapValues { (_, v) ->
            parsePropertySchema(v.jsonObject)
        }
        val required = obj["required"]?.jsonArray?.map {
            it.jsonPrimitive.contentOrNull ?: ""
        }
        return McpInputSchema(
            type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "object",
            properties = properties,
            required = required
        )
    }

    private fun parsePropertySchema(obj: JsonObject): McpPropertySchema {
        return McpPropertySchema(
            type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "string",
            description = obj["description"]?.jsonPrimitive?.contentOrNull,
            enum = obj["enum"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" },
            items = obj["items"]?.jsonObject?.let { parsePropertySchema(it) },
            properties = obj["properties"]?.jsonObject?.mapValues { (_, v) ->
                parsePropertySchema(v.jsonObject)
            }
        )
    }
}

/**
 * MCP 异常
 */
class McpException(message: String, cause: Throwable? = null) : Exception(message, cause)
