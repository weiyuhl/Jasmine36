package com.lhzkml.jasmine.core.agent.tools.feature.pipeline

import com.lhzkml.jasmine.core.agent.tools.feature.AgentFeature
import com.lhzkml.jasmine.core.agent.tools.feature.AgentLifecycleEventContext
import com.lhzkml.jasmine.core.agent.tools.feature.FeatureKey
import com.lhzkml.jasmine.core.agent.tools.feature.config.FeatureConfig
import com.lhzkml.jasmine.core.agent.tools.feature.handler.*
import com.lhzkml.jasmine.core.agent.tools.feature.message.FeatureMessageProcessor
import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

/**
 * Agent Pipeline 抽象基类
 * 移植自 koog 的 AIAgentPipeline。
 *
 * Pipeline 是 Feature 系统的核心，提供：
 * - Feature 安装/卸载
 * - 事件拦截器注册
 * - 生命周期事件触发
 *
 * 所有 Agent/Strategy/LLM/Tool/Streaming 事件都通过 Pipeline 分发给已注册的 handler。
 */
abstract class AgentPipeline {

    // ========== 已注册 Feature ==========

    private class RegisteredFeature(
        val featureImpl: Any,
        val featureConfig: FeatureConfig
    )

    private val registeredFeatures: MutableMap<FeatureKey<*>, RegisteredFeature> = mutableMapOf()

    // ========== 事件处理器 Map ==========

    protected val agentEventHandlers: MutableMap<FeatureKey<*>, AgentEventHandler> = mutableMapOf()
    protected val strategyEventHandlers: MutableMap<FeatureKey<*>, StrategyEventHandler> = mutableMapOf()
    protected val toolCallEventHandlers: MutableMap<FeatureKey<*>, ToolCallEventHandler> = mutableMapOf()
    protected val llmCallEventHandlers: MutableMap<FeatureKey<*>, LLMCallEventHandler> = mutableMapOf()
    protected val llmStreamingEventHandlers: MutableMap<FeatureKey<*>, LLMStreamingEventHandler> = mutableMapOf()

    // ========== Feature 管理 ==========

    protected fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: FeatureKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl
    ) {
        registeredFeatures[featureKey] = RegisteredFeature(featureImpl, featureConfig)
    }

    protected suspend fun uninstall(featureKey: FeatureKey<*>) {
        registeredFeatures
            .filter { (key, _) -> key == featureKey }
            .forEach { (key, registered) ->
                registered.featureConfig.messageProcessors.forEach { it.close() }
                registeredFeatures.remove(key)
            }
    }

    /** 获取已安装的 Feature 实现 */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> feature(feature: AgentFeature<*, T>): T? {
        return registeredFeatures[feature.key]?.featureImpl as? T
    }

    // ========== Feature 生命周期 ==========

    internal suspend fun prepareFeature(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { processor ->
            processor.initialize()
        }
    }

    internal suspend fun prepareFeatures() {
        registeredFeatures.values.forEach { registered ->
            prepareFeature(registered.featureConfig)
        }
    }

    internal suspend fun closeFeatureMessageProcessors(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { processor ->
            processor.close()
        }
    }

    internal suspend fun closeAllFeaturesMessageProcessors() {
        registeredFeatures.values.forEach { registered ->
            closeFeatureMessageProcessors(registered.featureConfig)
        }
    }

    // ========== 触发 Agent 事件 ==========

    suspend fun onAgentStarting(
        eventId: String, agentId: String, runId: String, context: AgentGraphContext
    ) {
        val eventContext = AgentStartingContext(eventId, agentId, runId, context)
        agentEventHandlers.values.forEach { it.handleAgentStarting(eventContext) }
    }

    suspend fun onAgentCompleted(
        eventId: String, agentId: String, runId: String, result: Any?, context: AgentGraphContext
    ) {
        val eventContext = AgentCompletedContext(eventId, agentId, runId, result, context)
        agentEventHandlers.values.forEach { it.agentCompletedHandler.handle(eventContext) }
    }

    suspend fun onAgentExecutionFailed(
        eventId: String, agentId: String, runId: String, throwable: Throwable, context: AgentGraphContext
    ) {
        val eventContext = AgentExecutionFailedContext(eventId, agentId, runId, throwable, context)
        agentEventHandlers.values.forEach { it.agentExecutionFailedHandler.handle(eventContext) }
    }

    suspend fun onAgentClosing(eventId: String, agentId: String) {
        val eventContext = AgentClosingContext(eventId, agentId)
        agentEventHandlers.values.forEach { it.agentClosingHandler.handle(eventContext) }
    }

    // ========== 触发 Strategy 事件 ==========

    suspend fun onStrategyStarting(
        eventId: String, strategyName: String, context: AgentGraphContext
    ) {
        val eventContext = StrategyStartingContext(eventId, strategyName, context)
        strategyEventHandlers.values.forEach { it.handleStrategyStarting(eventContext) }
    }

    suspend fun onStrategyCompleted(
        eventId: String, strategyName: String, result: Any?, context: AgentGraphContext
    ) {
        val eventContext = StrategyCompletedContext(eventId, strategyName, result, context)
        strategyEventHandlers.values.forEach { it.handleStrategyCompleted(eventContext) }
    }

    // ========== 触发 LLM Call 事件 ==========

    suspend fun onLLMCallStarting(
        eventId: String, runId: String, model: String, messageCount: Int,
        tools: List<String>, context: AgentGraphContext
    ) {
        val eventContext = LLMCallStartingContext(eventId, runId, model, messageCount, tools, context)
        llmCallEventHandlers.values.forEach { it.llmCallStartingHandler.handle(eventContext) }
    }

    suspend fun onLLMCallCompleted(
        eventId: String, runId: String, model: String, responsePreview: String?,
        hasToolCalls: Boolean, toolCallCount: Int,
        promptTokens: Int, completionTokens: Int, totalTokens: Int,
        context: AgentGraphContext
    ) {
        val eventContext = LLMCallCompletedContext(
            eventId, runId, model, responsePreview, hasToolCalls, toolCallCount,
            promptTokens, completionTokens, totalTokens, context
        )
        llmCallEventHandlers.values.forEach { it.llmCallCompletedHandler.handle(eventContext) }
    }

    // ========== 触发 Tool Call 事件 ==========

    suspend fun onToolCallStarting(
        eventId: String, runId: String, toolCallId: String?, toolName: String,
        toolArgs: String, context: AgentGraphContext
    ) {
        val eventContext = ToolCallStartingContext(eventId, runId, toolCallId, toolName, toolArgs, context)
        toolCallEventHandlers.values.forEach { it.toolCallHandler.handle(eventContext) }
    }

    suspend fun onToolValidationFailed(
        eventId: String, runId: String, toolCallId: String?, toolName: String,
        toolArgs: String, message: String,
        error: com.lhzkml.jasmine.core.agent.tools.trace.TraceError,
        context: AgentGraphContext
    ) {
        val eventContext = ToolValidationFailedContext(
            eventId, runId, toolCallId, toolName, toolArgs, message, error, context
        )
        toolCallEventHandlers.values.forEach { it.toolValidationErrorHandler.handle(eventContext) }
    }

    suspend fun onToolCallFailed(
        eventId: String, runId: String, toolCallId: String?, toolName: String,
        toolArgs: String, message: String,
        error: com.lhzkml.jasmine.core.agent.tools.trace.TraceError?,
        context: AgentGraphContext
    ) {
        val eventContext = ToolCallFailedContext(
            eventId, runId, toolCallId, toolName, toolArgs, message, error, context
        )
        toolCallEventHandlers.values.forEach { it.toolCallFailureHandler.handle(eventContext) }
    }

    suspend fun onToolCallCompleted(
        eventId: String, runId: String, toolCallId: String?, toolName: String,
        toolArgs: String, toolResult: String?, context: AgentGraphContext
    ) {
        val eventContext = ToolCallCompletedContext(
            eventId, runId, toolCallId, toolName, toolArgs, toolResult, context
        )
        toolCallEventHandlers.values.forEach { it.toolCallResultHandler.handle(eventContext) }
    }

    // ========== 触发 LLM Streaming 事件 ==========

    suspend fun onLLMStreamingStarting(
        eventId: String, runId: String, model: String, messageCount: Int,
        tools: List<String>, context: AgentGraphContext
    ) {
        val eventContext = LLMStreamingStartingContext(eventId, runId, model, messageCount, tools, context)
        llmStreamingEventHandlers.values.forEach { it.llmStreamingStartingHandler.handle(eventContext) }
    }

    suspend fun onLLMStreamingFrameReceived(
        eventId: String, runId: String, chunk: String, context: AgentGraphContext
    ) {
        val eventContext = LLMStreamingFrameReceivedContext(eventId, runId, chunk, context)
        llmStreamingEventHandlers.values.forEach { it.llmStreamingFrameReceivedHandler.handle(eventContext) }
    }

    suspend fun onLLMStreamingFailed(
        eventId: String, runId: String, model: String, error: Throwable, context: AgentGraphContext
    ) {
        val eventContext = LLMStreamingFailedContext(eventId, runId, model, error, context)
        llmStreamingEventHandlers.values.forEach { it.llmStreamingFailedHandler.handle(eventContext) }
    }

    suspend fun onLLMStreamingCompleted(
        eventId: String, runId: String, model: String, responsePreview: String?,
        hasToolCalls: Boolean, toolCallCount: Int,
        promptTokens: Int, completionTokens: Int, totalTokens: Int,
        context: AgentGraphContext
    ) {
        val eventContext = LLMStreamingCompletedContext(
            eventId, runId, model, responsePreview, hasToolCalls, toolCallCount,
            promptTokens, completionTokens, totalTokens, context
        )
        llmStreamingEventHandlers.values.forEach { it.llmStreamingCompletedHandler.handle(eventContext) }
    }

    // ========== 拦截器注册 ==========

    fun interceptAgentStarting(
        feature: AgentFeature<*, *>, handle: suspend (AgentStartingContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }
        handler.agentStartingHandler = AgentStartingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptAgentCompleted(
        feature: AgentFeature<*, *>, handle: suspend (AgentCompletedContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }
        handler.agentCompletedHandler = AgentCompletedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptAgentExecutionFailed(
        feature: AgentFeature<*, *>, handle: suspend (AgentExecutionFailedContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }
        handler.agentExecutionFailedHandler = AgentExecutionFailedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptAgentClosing(
        feature: AgentFeature<*, *>, handle: suspend (AgentClosingContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }
        handler.agentClosingHandler = AgentClosingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptStrategyStarting(
        feature: AgentFeature<*, *>, handle: suspend (StrategyStartingContext) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(feature.key) { StrategyEventHandler() }
        handler.strategyStartingHandler = StrategyStartingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptStrategyCompleted(
        feature: AgentFeature<*, *>, handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(feature.key) { StrategyEventHandler() }
        handler.strategyCompletedHandler = StrategyCompletedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptLLMCallStarting(
        feature: AgentFeature<*, *>, handle: suspend (LLMCallStartingContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(feature.key) { LLMCallEventHandler() }
        handler.llmCallStartingHandler = LLMCallStartingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptLLMCallCompleted(
        feature: AgentFeature<*, *>, handle: suspend (LLMCallCompletedContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(feature.key) { LLMCallEventHandler() }
        handler.llmCallCompletedHandler = LLMCallCompletedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptToolCallStarting(
        feature: AgentFeature<*, *>, handle: suspend (ToolCallStartingContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }
        handler.toolCallHandler = ToolCallHandler(createConditionalHandler(feature, handle))
    }

    fun interceptToolValidationFailed(
        feature: AgentFeature<*, *>, handle: suspend (ToolValidationFailedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }
        handler.toolValidationErrorHandler = ToolValidationErrorHandler(createConditionalHandler(feature, handle))
    }

    fun interceptToolCallFailed(
        feature: AgentFeature<*, *>, handle: suspend (ToolCallFailedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }
        handler.toolCallFailureHandler = ToolCallFailureHandler(createConditionalHandler(feature, handle))
    }

    fun interceptToolCallCompleted(
        feature: AgentFeature<*, *>, handle: suspend (ToolCallCompletedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }
        handler.toolCallResultHandler = ToolCallResultHandler(createConditionalHandler(feature, handle))
    }

    fun interceptLLMStreamingStarting(
        feature: AgentFeature<*, *>, handle: suspend (LLMStreamingStartingContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingStartingHandler = LLMStreamingStartingHandler(createConditionalHandler(feature, handle))
    }

    fun interceptLLMStreamingFrameReceived(
        feature: AgentFeature<*, *>, handle: suspend (LLMStreamingFrameReceivedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingFrameReceivedHandler = LLMStreamingFrameReceivedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptLLMStreamingFailed(
        feature: AgentFeature<*, *>, handle: suspend (LLMStreamingFailedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingFailedHandler = LLMStreamingFailedHandler(createConditionalHandler(feature, handle))
    }

    fun interceptLLMStreamingCompleted(
        feature: AgentFeature<*, *>, handle: suspend (LLMStreamingCompletedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingCompletedHandler = LLMStreamingCompletedHandler(createConditionalHandler(feature, handle))
    }

    // ========== 条件处理器 ==========

    protected fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        feature: AgentFeature<*, *>,
        handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = handler@{ eventContext ->
        val featureConfig = registeredFeatures[feature.key]?.featureConfig
        if (featureConfig != null && !featureConfig.eventFilter.invoke(eventContext)) {
            return@handler
        }
        handle(eventContext)
    }
}
