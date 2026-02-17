package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Gemini generateContent API 请求体
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiToolDef>? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

/**
 * Gemini Part — 支持 text、functionCall、functionResponse
 */
@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null
)

/**
 * Gemini 工具定义
 */
@Serializable
data class GeminiToolDef(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject? = null
)

/**
 * Gemini functionCall（模型返回的工具调用）
 */
@Serializable
data class GeminiFunctionCall(
    val name: String = "",
    val args: JsonObject? = null
)

/**
 * Gemini functionResponse（工具结果回传）
 */
@Serializable
data class GeminiFunctionResponse(
    val name: String = "",
    val response: JsonObject? = null
)

/**
 * Gemini generateContent API 响应体
 */
@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)

/**
 * Gemini List Models API 响应
 */
@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModelInfo> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class GeminiModelInfo(
    val name: String = "",
    val displayName: String = "",
    val description: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val thinking: Boolean? = null,
    val temperature: Double? = null,
    val maxTemperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null
)
