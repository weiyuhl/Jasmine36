package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.model.Prompt

/**
 * Top-level Agent that wraps a graph strategy and provides callback-driven execution.
 */
class GraphAgent(
    private val client: ChatClient,
    private val model: String,
    private val strategy: AgentStrategy<String, String>,
    private val toolRegistry: ToolRegistry,
    private val tracing: Tracing? = null,
    private val agentId: String = "graph-agent"
) {
    /**
     * Execute the graph strategy with UI callbacks.
     *
     * @param prompt The initial prompt (contains system + conversation history)
     * @param input The user's current message
     * @param onChunk LLM streaming text chunk callback
     * @param onThinking LLM thinking/reasoning callback
     * @param onToolCallStart Called when a tool execution begins
     * @param onToolCallResult Called when a tool execution completes
     * @param onNodeEnter Called when a graph node starts executing
     * @param onNodeExit Called when a graph node finishes (success flag)
     * @param onEdge Called when traversing an edge (from, to, label)
     * @return The final LLM response text, or null if execution failed
     */
    suspend fun runWithCallbacks(
        prompt: Prompt,
        input: String,
        onChunk: (suspend (String) -> Unit)? = null,
        onThinking: (suspend (String) -> Unit)? = null,
        onToolCallStart: (suspend (String, String) -> Unit)? = null,
        onToolCallResult: (suspend (String, String) -> Unit)? = null,
        onNodeEnter: (suspend (String) -> Unit)? = null,
        onNodeExit: (suspend (String, Boolean) -> Unit)? = null,
        onEdge: (suspend (String, String, String) -> Unit)? = null
    ): String? {
        val toolDescriptors = toolRegistry.descriptors()
        val session = LLMWriteSession(client, model, prompt, toolDescriptors)
        val readSession = LLMReadSession(client, model, prompt, toolDescriptors)

        val runId = tracing?.newRunId() ?: "run-0"

        val context = AgentGraphContext(
            agentId = agentId,
            runId = runId,
            client = client,
            model = model,
            session = session,
            readSession = readSession,
            toolRegistry = toolRegistry,
            environment = GenericAgentEnvironment(agentId, toolRegistry),
            tracing = tracing
        )

        onChunk?.let { context.storage.set(PredefinedStrategies.KEY_ON_CHUNK, it) }
        onThinking?.let { context.storage.set(PredefinedStrategies.KEY_ON_THINKING, it) }
        onToolCallStart?.let { context.storage.set(PredefinedStrategies.KEY_ON_TOOL_CALL_START, it) }
        onToolCallResult?.let { context.storage.set(PredefinedStrategies.KEY_ON_TOOL_CALL_RESULT, it) }
        onNodeEnter?.let { context.storage.set(PredefinedStrategies.KEY_ON_NODE_ENTER, it) }
        onNodeExit?.let { context.storage.set(PredefinedStrategies.KEY_ON_NODE_EXIT, it) }
        onEdge?.let { context.storage.set(PredefinedStrategies.KEY_ON_EDGE, it) }

        return try {
            strategy.execute(context, input)
        } finally {
            session.close()
            readSession.close()
        }
    }
}
