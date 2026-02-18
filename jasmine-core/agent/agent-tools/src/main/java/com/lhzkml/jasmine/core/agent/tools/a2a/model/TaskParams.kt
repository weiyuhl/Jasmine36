package com.lhzkml.jasmine.core.agent.tools.a2a.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 任务 ID 参数
 * 完整移植 koog 的 TaskIdParams
 */
@Serializable
data class TaskIdParams(
    val id: String,
    val metadata: JsonObject? = null
)

/**
 * 任务查询参数
 * 完整移植 koog 的 TaskQueryParams
 */
@Serializable
data class TaskQueryParams(
    val id: String,
    val historyLength: Int? = null,
    val metadata: JsonObject? = null
)

/**
 * 推送通知配置查询参数
 * 完整移植 koog 的 TaskPushNotificationConfigParams
 */
@Serializable
data class TaskPushNotificationConfigParams(
    val id: String,
    val pushNotificationConfigId: String? = null,
    val metadata: JsonObject? = null
)
