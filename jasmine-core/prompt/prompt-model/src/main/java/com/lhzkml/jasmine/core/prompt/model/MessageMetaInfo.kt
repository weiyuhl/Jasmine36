package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 消息元数据接口
 * 移植自 koog MessageMetaInfo
 *
 * 简化: 使用 Long 时间戳代替 kotlinx.datetime.Instant
 */
@Serializable
sealed interface MessageMetaInfo {
    /**
     * 消息创建时间戳（毫秒），null 表示未记录
     */
    val timestamp: Long?

    /**
     * 自定义元数据，可存储任意 JSON 键值对
     */
    val metadata: JsonObject?
}

/**
 * 请求消息元数据
 * 移植自 koog RequestMetaInfo
 */
@Serializable
data class RequestMetaInfo(
    override val timestamp: Long? = null,
    override val metadata: JsonObject? = null
) : MessageMetaInfo {
    companion object {
        val Empty = RequestMetaInfo()
    }
}

/**
 * 响应消息元数据
 * 移植自 koog ResponseMetaInfo
 *
 * 简化: 使用 jasmine 现有的 Usage 类代替 koog 的分散 token 计数字段
 */
@Serializable
data class ResponseMetaInfo(
    override val timestamp: Long? = null,
    override val metadata: JsonObject? = null,
    val totalTokensCount: Int? = null,
    val inputTokensCount: Int? = null,
    val outputTokensCount: Int? = null
) : MessageMetaInfo {
    companion object {
        val Empty = ResponseMetaInfo()
    }
}
