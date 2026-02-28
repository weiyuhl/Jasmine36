package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.graph.feature.pipeline.AgentFunctionalPipeline
import com.lhzkml.jasmine.core.agent.observe.trace.TraceError
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.model.Prompt

/**
 * 函数�?Agent
 * 移植�?koog �?FunctionalAIAgent，基于函数式策略执行 Agent�?
 *
 * �?GraphAgent（图结构执行）不同，FunctionalAgent 通过一个挂起函�?
 * 定义执行逻辑，适用于简单的循环场景�?
 *
 * 使用方式�?
 * ```kotlin
 * val strategy = functionalStrategy<String, String>("chat") { input ->
 *     session.appendPrompt { user(input) }
 *     var result = session.requestLLM()
 *     while (result.hasToolCalls) {
 *         for (call in result.toolCalls) {
 *             val toolResult = environment.executeTool(call)
 *             session.appendPrompt { message(ChatMessage.toolResult(...)) }
 *         }
 *         result = session.requestLLM()
 *     }
 *     result.content
 * }
 *
 * val agent = FunctionalAgent(
 *     client = client,
 *     model = "gpt-4",
 *     strategy = strategy
 * )
 * val result = agent.run(prompt, "Hello!")
 * ```
 */
class FunctionalAgent<TInput, TOutput>(
    private val client: ChatClient,
    private val model: String,
    private val strategy: FunctionalStrategy<TInput, TOutput>,
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val tracing: Tracing? = null,
    private val pipeline: AgentFunctionalPipeline? = null,
    private val agentId: String = "functional-agent"
) {
    /**
     * 执行 Agent
     * @param prompt 初始提示�?
     * @param input Agent 输入
     * @return Agent 输出
     */
    suspend fun run(prompt: Prompt, input: TInput): TOutput {
        val runId = tracing?.newRunId() ?: ""
        val session = LLMWriteSession(client, model, prompt, toolRegistry.descriptors())
        val readSession = LLMReadSession(client, model, prompt, toolRegistry.descriptors())
        val environment = GenericAgentEnvironment(agentId, toolRegistry)

        tracing?.emit(TraceEvent.AgentStarting(
            eventId = tracing.newEventId(), runId = runId,
            agentId = agentId, model = model, toolCount = toolRegistry.descriptors().size
        ))

        val context = AgentGraphContext(
            agentId = agentId,
            runId = runId,
            client = client,
            model = model,
            session = session,
            readSession = readSession,
            toolRegistry = toolRegistry,
            environment = environment,
            tracing = tracing,
            pipeline = pipeline,
            executionInfo = AgentExecutionInfo(null, agentId)
        )

        // 触发 Pipeline 事件
        pipeline?.onAgentStarting(
            eventId = tracing?.newEventId() ?: "", agentId = agentId, runId = runId, context = context
        )

        return try {
            val result = strategy.execute(context, input)
            tracing?.emit(TraceEvent.AgentCompleted(
                eventId = tracing.newEventId(), runId = runId,
                agentId = agentId, result = result.toString().take(100),
                totalIterations = context.iterations
            ))
            pipeline?.onAgentCompleted(
                eventId = tracing?.newEventId() ?: "", agentId = agentId, runId = runId,
                result = result, context = context
            )
            result
        } catch (e: Exception) {
            tracing?.emit(TraceEvent.AgentFailed(
                eventId = tracing.newEventId(), runId = runId,
                agentId = agentId, error = TraceError.from(e)
            ))
            pipeline?.onAgentExecutionFailed(
                eventId = tracing?.newEventId() ?: "", agentId = agentId, runId = runId,
                throwable = e, context = context
            )
            throw e
        } finally {
            pipeline?.onAgentClosing(
                eventId = tracing?.newEventId() ?: "", agentId = agentId
            )
            pipeline?.closeAllFeaturesMessageProcessors()
            session.close()
            readSession.close()
        }
    }
}
