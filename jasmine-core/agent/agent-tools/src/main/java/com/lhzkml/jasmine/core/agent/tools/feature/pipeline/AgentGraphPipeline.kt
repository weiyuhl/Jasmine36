package com.lhzkml.jasmine.core.agent.tools.feature.pipeline

import com.lhzkml.jasmine.core.agent.tools.feature.AgentGraphFeature
import com.lhzkml.jasmine.core.agent.tools.feature.FeatureKey
import com.lhzkml.jasmine.core.agent.tools.feature.config.FeatureConfig
import com.lhzkml.jasmine.core.agent.tools.feature.handler.*
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

/**
 * 图策略 Agent Pipeline
 * 移植自 koog 的 AIAgentGraphPipeline。
 *
 * 在基础 Pipeline 之上增加了 Node 和 Subgraph 事件的拦截和触发。
 */
class AgentGraphPipeline : AgentPipeline() {

    private val nodeExecutionHandlers: MutableMap<FeatureKey<*>, NodeExecutionEventHandler> = mutableMapOf()
    private val subgraphExecutionHandlers: MutableMap<FeatureKey<*>, SubgraphExecutionEventHandler> = mutableMapOf()

    /** 安装图 Feature */
    fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AgentGraphFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(featureConfig, this)
        super.install(feature.key, featureConfig, featureImpl)
    }

    // ========== 触发 Node 事件 ==========

    suspend fun onNodeExecutionStarting(
        eventId: String, nodeName: String, input: String?, context: AgentGraphContext
    ) {
        val eventContext = NodeExecutionStartingContext(eventId, nodeName, input, context)
        nodeExecutionHandlers.values.forEach { it.nodeExecutionStartingHandler.handle(eventContext) }
    }

    suspend fun onNodeExecutionCompleted(
        eventId: String, nodeName: String, input: String?, output: String?, context: AgentGraphContext
    ) {
        val eventContext = NodeExecutionCompletedContext(eventId, nodeName, input, output, context)
        nodeExecutionHandlers.values.forEach { it.nodeExecutionCompletedHandler.handle(eventContext) }
    }

    suspend fun onNodeExecutionFailed(
        eventId: String, nodeName: String, input: String?, throwable: Throwable, context: AgentGraphContext
    ) {
        val eventContext = NodeExecutionFailedContext(eventId, nodeName, input, throwable, context)
        nodeExecutionHandlers.values.forEach { it.nodeExecutionFailedHandler.handle(eventContext) }
    }

    // ========== 触发 Subgraph 事件 ==========

    suspend fun onSubgraphExecutionStarting(
        eventId: String, subgraphName: String, input: String?, context: AgentGraphContext
    ) {
        val eventContext = SubgraphExecutionStartingContext(eventId, subgraphName, input, context)
        subgraphExecutionHandlers.values.forEach { it.subgraphExecutionStartingHandler.handle(eventContext) }
    }

    suspend fun onSubgraphExecutionCompleted(
        eventId: String, subgraphName: String, input: String?, output: String?, context: AgentGraphContext
    ) {
        val eventContext = SubgraphExecutionCompletedContext(eventId, subgraphName, input, output, context)
        subgraphExecutionHandlers.values.forEach { it.subgraphExecutionCompletedHandler.handle(eventContext) }
    }

    suspend fun onSubgraphExecutionFailed(
        eventId: String, subgraphName: String, input: String?, throwable: Throwable, context: AgentGraphContext
    ) {
        val eventContext = SubgraphExecutionFailedContext(eventId, subgraphName, input, throwable, context)
        subgraphExecutionHandlers.values.forEach { it.subgraphExecutionFailedHandler.handle(eventContext) }
    }

    // ========== Node 拦截器 ==========

    fun interceptNodeExecutionStarting(
        feature: AgentGraphFeature<*, *>, handle: suspend (NodeExecutionStartingContext) -> Unit
    ) {
        val handler = nodeExecutionHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }
        handler.nodeExecutionStartingHandler = NodeExecutionStartingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptNodeExecutionCompleted(
        feature: AgentGraphFeature<*, *>, handle: suspend (NodeExecutionCompletedContext) -> Unit
    ) {
        val handler = nodeExecutionHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }
        handler.nodeExecutionCompletedHandler = NodeExecutionCompletedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptNodeExecutionFailed(
        feature: AgentGraphFeature<*, *>, handle: suspend (NodeExecutionFailedContext) -> Unit
    ) {
        val handler = nodeExecutionHandlers.getOrPut(feature.key) { NodeExecutionEventHandler() }
        handler.nodeExecutionFailedHandler = NodeExecutionFailedHandler(createConditionalHandler(feature, handle))
    }

    // ========== Subgraph 拦截器 ==========

    fun interceptSubgraphExecutionStarting(
        feature: AgentGraphFeature<*, *>, handle: suspend (SubgraphExecutionStartingContext) -> Unit
    ) {
        val handler = subgraphExecutionHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }
        handler.subgraphExecutionStartingHandler = SubgraphExecutionStartingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptSubgraphExecutionCompleted(
        feature: AgentGraphFeature<*, *>, handle: suspend (SubgraphExecutionCompletedContext) -> Unit
    ) {
        val handler = subgraphExecutionHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }
        handler.subgraphExecutionCompletedHandler = SubgraphExecutionCompletedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptSubgraphExecutionFailed(
        feature: AgentGraphFeature<*, *>, handle: suspend (SubgraphExecutionFailedContext) -> Unit
    ) {
        val handler = subgraphExecutionHandlers.getOrPut(feature.key) { SubgraphExecutionEventHandler() }
        handler.subgraphExecutionFailedHandler = SubgraphExecutionFailedHandler(createConditionalHandler(feature, handle))
    }
}
