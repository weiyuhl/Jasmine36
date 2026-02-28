package com.lhzkml.jasmine.core.agent.graph.feature.config

import com.lhzkml.jasmine.core.agent.graph.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.graph.feature.message.FeatureMessageProcessor

/**
 * Feature 配置抽象基类
 * 移植�?koog �?FeatureConfig�?
 *
 * 管理 FeatureMessageProcessor 列表和事件过滤器�?
 * 子类可扩展此配置类以定义 Feature 特有的设置�?
 */
abstract class FeatureConfig {

    private val _messageProcessors = mutableListOf<FeatureMessageProcessor>()

    private var _eventFilter: (AgentLifecycleEventContext) -> Boolean = { true }

    /** 已注册的消息处理器列表（只读�?*/
    val messageProcessors: List<FeatureMessageProcessor>
        get() = _messageProcessors.toList()

    /** 事件过滤�?*/
    val eventFilter: (AgentLifecycleEventContext) -> Boolean
        get() = _eventFilter

    /** 添加消息处理�?*/
    fun addMessageProcessor(processor: FeatureMessageProcessor) {
        _messageProcessors.add(processor)
    }

    /**
     * 设置事件过滤�?
     * 移植�?koog �?setEventFilter�?
     *
     * 在事件发送到消息处理器之前调用�?
     * 返回 true 表示处理该事件，false 表示忽略�?
     * 默认处理所有事件�?
     */
    open fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        _eventFilter = filter
    }
}
