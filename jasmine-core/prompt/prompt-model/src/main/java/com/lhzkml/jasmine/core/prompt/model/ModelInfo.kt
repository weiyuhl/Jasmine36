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
 */
@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object")
    val objectType: String = "model",
    @SerialName("owned_by")
    val ownedBy: String = ""
)
