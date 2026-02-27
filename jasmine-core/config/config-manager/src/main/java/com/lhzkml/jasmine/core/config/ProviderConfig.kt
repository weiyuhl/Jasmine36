package com.lhzkml.jasmine.core.config

import com.lhzkml.jasmine.core.prompt.executor.ApiType

/**
 * 供应商配置
 * @param id 唯一标识
 * @param name 显示名称
 * @param defaultBaseUrl 默认 API 地址
 * @param defaultModel 默认模型名
 * @param apiType API 渠道类型
 * @param isCustom 是否为自定义供应商（可删除）
 */
data class ProviderConfig(
    val id: String,
    val name: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val apiType: ApiType = ApiType.OPENAI,
    val isCustom: Boolean = false
)

/**
 * 当前激活的完整配置
 */
data class ActiveProviderConfig(
    val providerId: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val apiType: ApiType,
    val chatPath: String? = null,
    val vertexEnabled: Boolean = false,
    val vertexProjectId: String = "",
    val vertexLocation: String = "global",
    val vertexServiceAccountJson: String = ""
)
