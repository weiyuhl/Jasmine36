package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.tools.trace.TraceError
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.tools.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMSession
import com.lhzkml.jasmine.core.prompt.model.Prompt

/**
 * 图 Agent
 * 参考 koog 的 GraphAIAgent，基于图策略执行 Agent。
 *
 * 与 ToolExecutor（简单 while 循环）不同，GraphAgent 按照
 * 节点 → 边 → 节点的图结构执行，支持条件分支、子图嵌套等。
 *
 * 使用方式：
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
    private val agentId: String = "graph-agent"
) {
    /**
     * 执行 Agent
     * @param prompt 初始提示词
     * @param input Agent 输入
     * @return Agent 输出
     */
    suspend fun run(prompt: Prompt, input: TInput): TOutput? {
        val runId = tracing?.newRunId() ?: ""
        val session = LLMSession(client, model, prompt, toolRegistry.descriptors())

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
            toolRegistry = toolRegistry,
            tracing = tracing
        )

        return try {
            val result = strategy.execute(context, input)
            tracing?.emit(TraceEvent.AgentCompleted(
                eventId = tracing.newEventId(), runId = runId,
                agentId = agentId, result = result.toString().take(100),
                totalIterations = context.iterations
            ))
            result
        } catch (e: Exception) {
            tracing?.emit(TraceEvent.AgentFailed(
                eventId = tracing.newEventId(), runId = runId,
                agentId = agentId, error = TraceError.from(e)
            ))
            throw e
        } finally {
            session.close()
        }
    }
}
