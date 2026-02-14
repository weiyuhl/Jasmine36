package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini generateContent API 请求体
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null
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
 * GET /v1beta/models?key=API_KEY
 */
@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModelInfo> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class GeminiModelInfo(
    /** 格式: "models/gemini-2.5-flash" */
    val name: String = "",
    val displayName: String = "",
    val description: String = "",
    val supportedGenerationMethods: List<String> = emptyList()
)
