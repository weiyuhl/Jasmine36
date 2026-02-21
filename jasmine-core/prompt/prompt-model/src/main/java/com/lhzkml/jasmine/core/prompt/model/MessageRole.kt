package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.Serializable

/**
 * 消息角色枚举
 * 移植自 koog Message.Role
 */
@Serializable
enum class MessageRole {
    /** 系统消息 */
    System,
    /** 用户消息 */
    User,
    /** 助手消息 */
    Assistant,
    /** 推理/思考消息 */
    Reasoning,
    /** 工具相关消息 */
    Tool
}
