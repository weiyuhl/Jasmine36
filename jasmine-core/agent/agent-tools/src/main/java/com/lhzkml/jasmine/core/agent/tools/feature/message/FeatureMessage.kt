package com.lhzkml.jasmine.core.agent.tools.feature.message

/**
 * Feature 消息接口
 * 移植自 koog 的 FeatureMessage。
 *
 * 表示系统中的一个 Feature 消息或事件，用于在 FeatureMessageProcessor 之间传递。
 */
interface FeatureMessage {

    /** 消息创建时间戳（毫秒） */
    val timestamp: Long

    /** 消息类型 */
    val messageType: Type

    /**
     * 消息类型枚举
     * 移植自 koog 的 FeatureMessage.Type。
     */
    enum class Type(val value: String) {
        /** 文本消息 */
        Message("message"),
        /** 事件 */
        Event("event")
    }
}
