package com.lhzkml.jasmine.core.agent.tools.feature.handler

import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventType
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

// ========== Strategy 事件上下文 ==========

interface StrategyEventContext : AgentLifecycleEventContext

/** 策略开始执行上下文 */
data class StrategyStartingContext(
    override val eventId: String,
    val strategyName: String,
    val context: AgentGraphContext
) : StrategyEventContext {
    override val eventType = AgentLifecycleEventType.StrategyStarting
}

/** 策略执行完成上下文 */
data class StrategyCompletedContext(
    override val eventId: String,
    val strategyName: String,
    val result: Any?,
    val context: AgentGraphContext
) : StrategyEventContext {
    override val eventType = AgentLifecycleEventType.StrategyCompleted
}

// ========== Strategy 事件处理器 ==========

fun interface StrategyStartingHandler {
    suspend fun handle(context: StrategyStartingContext)
}

fun interface StrategyCompletedHandler {
    suspend fun handle(context: StrategyCompletedContext)
}

/**
 * 策略事件处理器容器
 * 移植自 koog 的 StrategyEventHandler。
 */
class StrategyEventHandler {
    var strategyStartingHandler: StrategyStartingHandler = StrategyStartingHandler { }
    var strategyCompletedHandler: StrategyCompletedHandler = StrategyCompletedHandler { }

    suspend fun handleStrategyStarting(context: StrategyStartingContext) {
        strategyStartingHandler.handle(context)
    }

    suspend fun handleStrategyCompleted(context: StrategyCompletedContext) {
        strategyCompletedHandler.handle(context)
    }
}
