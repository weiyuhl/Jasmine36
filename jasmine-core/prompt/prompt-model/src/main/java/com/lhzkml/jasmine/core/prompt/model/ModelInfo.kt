package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 模型列表响应
 */
@Serializable
data class ModelListResponse(
    @SerialName("object")
    val objectType: String = "list",
    val data: List<ModelInfo> = emptyList()
)

/**
 * 单个模型信息
 * @param id 模型标识，可用于 API 调用
 * @param ownedBy 模型所属组织
 * @param displayName 显示名称（部分供应商 API 返回）
 * @param contextLength 最大输入 token 数（部分供应商 API 返回）
 * @param maxOutputTokens 最大输出 token 数（部分供应商 API 返回）
 * @param supportsThinking 是否支持思考/推理（Gemini API 返回）
 * @param temperature 默认 temperature 值
 * @param maxTemperature 最大 temperature 值
 * @param topP 默认 topP 值
 * @param topK 默认 topK 值
 * @param description 模型描述
 */
@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object")
    val objectType: String = "model",
    @SerialName("owned_by")
    val ownedBy: String = "",
    // 以下为可选的元数据字段（不参与 API 反序列化，由客户端手动填充）
    @kotlinx.serialization.Transient
    val displayName: String? = null,
    @kotlinx.serialization.Transient
    val contextLength: Int? = null,
    @kotlinx.serialization.Transient
    val maxOutputTokens: Int? = null,
    @kotlinx.serialization.Transient
    val supportsThinking: Boolean? = null,
    @kotlinx.serialization.Transient
    val temperature: Double? = null,
    @kotlinx.serialization.Transient
    val maxTemperature: Double? = null,
    @kotlinx.serialization.Transient
    val topP: Double? = null,
    @kotlinx.serialization.Transient
    val topK: Int? = null,
    @kotlinx.serialization.Transient
    val description: String? = null
) {
    /** 是否包含丰富的元数据（不仅仅是 id） */
    val hasMetadata: Boolean
        get() = contextLength != null || maxOutputTokens != null
}
