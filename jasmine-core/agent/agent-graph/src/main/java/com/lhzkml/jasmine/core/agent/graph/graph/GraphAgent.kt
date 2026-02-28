package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.graph.feature.pipeline.AgentGraphPipeline
import com.lhzkml.jasmine.core.agent.observe.trace.TraceError
import com.lhzkml.jasmine.core.agent.observe.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.model.Prompt

/**
 * �?Agent
 * 移植�?koog �?GraphAIAgent，基于图策略执行 Agent�?
 *
 * �?ToolExecutor（简�?while 循环）不同，GraphAgent 按照
 * 节点 -> �?-> 节点的图结构执行，支持条件分支、子图嵌套等�?
 *
 * 使用方式�?
 * ```kotlin
 * val strategy = graphStrategy<String, String>("chat") {
 *     val process by node<String, String>("process") { input ->
 *         session.appendPrompt { user(input) }
 *         session.requestLLM().content
 *     }
 *     edge(nodeStart, process)
 *     edge(process, nodeFinish)
 * }
 *
 * val agent = GraphAgent(
 *     client = client,
 *     model = "gpt-4",
 *     strategy = strategy,
 *     tracing = tracing
 * )
 * val result = agent.run(prompt, "Hello!")
 * ```
 */
class GraphAgent<TInput, TOutput>(
    private val client: ChatClient,
    private val model: String,
    private val strategy: AgentStrategy<TInput, TOutput>,
    private val toolRegistry: ToolRegistry = ToolRegistry(),
    private val tracing: Tracing? = null,
    private val pipeline: AgentGraphPipeline? = null,
    private val agentId: String = "graph-agent"
) {
    /**
     * 执行 Agent
     * @param prompt 初始提示�?
     * @param input Agent 输入
     * @return Agent 输出
     */
    suspend fun run(prompt: Prompt, input: TInput): TOutput? {
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

    /**
     * 带回调的执行方式
     * 通过 context.storage 传递回调函数，供流式策略使用�?
     */
    suspend fun runWithCallbacks(
        prompt: Prompt,
        input: TInput,
        onChunk: (suspend (String) -> Unit)? = null,
        onThinking: (suspend (String) -> Unit)? = null,
        onToolCallStart: (suspend (String, String) -> Unit)? = null,
        onToolCallResult: (suspend (String, String) -> Unit)? = null,
        onNodeEnter: (suspend (String) -> Unit)? = null,
        onNodeExit: (suspend (String, Boolean) -> Unit)? = null,
        onEdge: (suspend (String, String, String) -> Unit)? = null
    ): TOutput? {
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

        // 通过 storage 传递回调（类型�?AgentStorageKey�?
        kotlinx.coroutines.runBlocking {
            onChunk?.let { context.storage.set(PredefinedStrategies.KEY_ON_CHUNK, it) }
            onThinking?.let { context.storage.set(PredefinedStrategies.KEY_ON_THINKING, it) }
            onToolCallStart?.let { context.storage.set(PredefinedStrategies.KEY_ON_TOOL_CALL_START, it) }
            onToolCallResult?.let { context.storage.set(PredefinedStrategies.KEY_ON_TOOL_CALL_RESULT, it) }
            onNodeEnter?.let { context.storage.set(PredefinedStrategies.KEY_ON_NODE_ENTER, it) }
            onNodeExit?.let { context.storage.set(PredefinedStrategies.KEY_ON_NODE_EXIT, it) }
            onEdge?.let { context.storage.set(PredefinedStrategies.KEY_ON_EDGE, it) }
        }

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
