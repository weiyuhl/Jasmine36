package com.lhzkml.jasmine.core.agent.graph.feature

/**
 * 事件处理器上下文基础接口
 * 移植�?koog �?AgentLifecycleEventContext�?
 *
 * 所有事件上下文都实现此接口，提供事�?ID 和事件类型�?
 */
interface AgentLifecycleEventContext {

    /** 事件唯一 ID */
    val eventId: String

    /** 事件类型 */
    val eventType: AgentLifecycleEventType
}
