package com.lhzkml.jasmine.core.agent.graph.feature.message

/**
 * Feature 消息接口
 * 移植�?koog �?FeatureMessage�?
 *
 * 表示系统中的一�?Feature 消息或事件，用于�?FeatureMessageProcessor 之间传递�?
 */
interface FeatureMessage {

    /** 消息创建时间戳（毫秒�?*/
    val timestamp: Long

    /** 消息类型 */
    val messageType: Type

    /**
     * 消息类型枚举
     * 移植�?koog �?FeatureMessage.Type�?
     */
    enum class Type(val value: String) {
        /** 文本消息 */
        Message("message"),
        /** 事件 */
        Event("event")
    }
}
