package com.lhzkml.jasmine.core.agent.tools.a2a.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 消息发送参数
 * 完整移植 koog 的 MessageSendParams
 */
@Serializable
data class MessageSendParams(
    val message: Message,
    val configuration: MessageSendConfiguration? = null,
    val metadata: JsonObject? = null
)

/**
 * 消息发送配置
 * 完整移植 koog 的 MessageSendConfiguration
 */
@Serializable
data class MessageSendConfiguration(
    val blocking: Boolean? = null,
    val acceptedOutputModes: List<String>? = null,
    val historyLength: Int? = null,
    val pushNotificationConfig: PushNotificationConfig? = null
)
