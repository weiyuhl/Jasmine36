package com.lhzkml.jasmine.core.agent.tools.mcp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 基于 SSE (Server-Sent Events) 的 MCP 客户端
 *
 * 实现 MCP 的 SSE 传输协议（旧版传输方式）：
 * 1. GET /sse — 建立 SSE 连接，接收服务器推送的事件
 * 2. POST <messageEndpoint> — 发送 JSON-RPC 请求
 *
 * SSE 流中的事件格式：
 * - event: endpoint → data 为 POST 消息的目标 URL
 * - event: message → data 为 JSON-RPC 响应
 *
 * @param serverUrl MCP 服务器基础 URL（如 http://localhost:8080）
 * @param ssePath SSE 端点路径，默认 /sse
 * @param customHeaders 自定义请求头
 */
class SseMcpClient(
    private val serverUrl: String,
    private val ssePath: String = "/sse",
    private val customHeaders: Map<String, String> = emptyMap()
) : McpClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(this@SseMcpClient.json)
        }
    }

    private val requestId = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject?>>()
    private var messageEndpoint: String? = null
    private var sseJob: Job? = null
    private val sseScope = CoroutineScope(Dispatchers.IO + Job())
    private val endpointReady = CompletableDeferred<Unit>()

    override suspend fun connect() {
        // 启动 SSE 监听
        startSseListener()

        // 等待服务器发送 endpoint 事件
        endpointReady.await()

        // 发送 initialize 请求
        val result = rpcCall("initialize", buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", McpToolRegistryProvider.DEFAULT_MCP_CLIENT_NAME)
                put("version", McpToolRegistryProvider.DEFAULT_MCP_CLIENT_VERSION)
            })
        })

        // 发送 initialized 通知
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
        sseJob?.cancel()
        sseScope.cancel()
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        httpClient.close()
    }

    // ========== SSE 监听 ==========

    private fun startSseListener() {
        sseJob = sseScope.launch {
            try {
                val sseUrl = buildSseUrl()
                val response = httpClient.get(sseUrl) {
                    headers.append("Accept", "text/event-stream")
                    for ((k, v) in customHeaders) { headers.append(k, v) }
                }

                val channel = response.bodyAsChannel()
                var currentEvent = ""
                var currentData = StringBuilder()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    when {
                        line.startsWith("event:") -> {
                            currentEvent = line.removePrefix("event:").trim()
                        }
                        line.startsWith("data:") -> {
                            currentData.append(line.removePrefix("data:").trim())
                        }
                        line.isBlank() -> {
                            // 空行表示事件结束
                            if (currentEvent.isNotEmpty() || currentData.isNotEmpty()) {
                                handleSseEvent(currentEvent, currentData.toString())
                                currentEvent = ""
                                currentData = StringBuilder()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // SSE 连接断开
            }
        }
    }

    private fun handleSseEvent(event: String, data: String) {
        when (event) {
            "endpoint" -> {
                // 服务器告知 POST 消息的目标 URL
                messageEndpoint = resolveEndpointUrl(data)
                if (!endpointReady.isCompleted) {
                    endpointReady.complete(Unit)
                }
            }
            "message" -> {
                // JSON-RPC 响应
                try {
                    val rpcResponse = json.decodeFromString(JsonRpcResponse.serializer(), data)
                    val id = rpcResponse.id?.let { extractIntId(it) }
                    if (id != null) {
                        val deferred = pendingRequests.remove(id)
                        if (rpcResponse.error != null) {
                            deferred?.completeExceptionally(
                                McpException("MCP error ${rpcResponse.error.code}: ${rpcResponse.error.message}")
                            )
                        } else {
                            deferred?.complete(rpcResponse.result?.jsonObject)
                        }
                    }
                } catch (_: Exception) {
                    // 忽略无法解析的消息
                }
            }
        }
    }

    private fun extractIntId(element: JsonElement): Int? {
        return try {
            element.jsonPrimitive.contentOrNull?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSseUrl(): String {
        val base = serverUrl.trimEnd('/')
        return "$base$ssePath"
    }

    private fun resolveEndpointUrl(endpoint: String): String {
        return if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            endpoint
        } else {
            val base = serverUrl.trimEnd('/')
            "$base${if (endpoint.startsWith("/")) endpoint else "/$endpoint"}"
        }
    }

    // ========== JSON-RPC 通信 ==========

    @Serializable
    private data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val id: Int? = null,
        val method: String,
        val params: JsonElement? = null
    )

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
        val endpoint = messageEndpoint
            ?: throw McpException("SSE endpoint not ready")

        val id = requestId.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject?>()
        pendingRequests[id] = deferred

        val request = JsonRpcRequest(id = id, method = method, params = params)

        try {
            httpClient.post(endpoint) {
                contentType(ContentType.Application.Json)
                for ((k, v) in customHeaders) { headers.append(k, v) }
                setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            throw McpException("Failed to send RPC request: ${e.message}", e)
        }

        return deferred.await()
    }

    private suspend fun rpcNotify(method: String, params: JsonObject? = null) {
        val endpoint = messageEndpoint
            ?: throw McpException("SSE endpoint not ready")

        val request = JsonRpcRequest(method = method, params = params)
        httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            for ((k, v) in customHeaders) { headers.append(k, v) }
            setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
        }
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
