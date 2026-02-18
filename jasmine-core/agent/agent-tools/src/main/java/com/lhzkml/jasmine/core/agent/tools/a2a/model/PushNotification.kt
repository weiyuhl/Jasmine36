package com.lhzkml.jasmine.core.agent.tools.a2a.model

import kotlinx.serialization.Serializable

/**
 * 任务推送通知配置容器
 * 完整移植 koog 的 TaskPushNotificationConfig
 */
@Serializable
data class TaskPushNotificationConfig(
    val taskId: String,
    val pushNotificationConfig: PushNotificationConfig
)

/**
 * 推送通知配置
 * 完整移植 koog 的 PushNotificationConfig
 */
@Serializable
data class PushNotificationConfig(
    val id: String? = null,
    val url: String,
    val token: String? = null,
    val authentication: PushNotificationAuthenticationInfo? = null
)

/**
 * 推送通知认证信息
 * 完整移植 koog 的 PushNotificationAuthenticationInfo
 */
@Serializable
data class PushNotificationAuthenticationInfo(
    val schemes: List<String>,
    val credentials: String? = null
)
